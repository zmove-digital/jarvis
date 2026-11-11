package com.zmove.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Escuta em tempo integral em foreground.
 *
 * Usa o Porcupine (wake word local) quando há uma Access Key configurada e cai
 * para o reconhecedor do sistema caso contrário. Ao detectar "Jarvis", pausa a
 * escuta, abre a [MainActivity] para capturar o comando e retoma automaticamente
 * depois — a tela avisa o serviço quando terminar de ouvir.
 */
class JarvisService : Service() {

    companion object {
        private const val TAG = "JarvisService"
        private const val CHANNEL_ID = "jarvis.listening"
        private const val NOTIFICATION_ID = 1001
        private const val AUTO_RESUME_DELAY_MS = 30_000L

        const val ACTION_START = "com.zmove.jarvis.action.START"
        const val ACTION_STOP = "com.zmove.jarvis.action.STOP"
        const val ACTION_PAUSE = "com.zmove.jarvis.action.PAUSE"
        const val ACTION_RESUME = "com.zmove.jarvis.action.RESUME"
        const val EXTRA_WAKE_WORD = "extra_wake_word"
        const val EXTRA_TRANSCRIPT = "extra_transcript"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var preferences: WakeWordPreferences

    private var engine: WakeWordEngine? = null
    private var paused = false
    private var wakeInProgress = false

    private val autoResume = Runnable { resumeListening() }

    override fun onCreate() {
        super.onCreate()
        preferences = WakeWordPreferences(this)
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                preferences.alwaysListening = false
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> pauseListening()

            ACTION_RESUME -> resumeListening()

            else -> {
                preferences.alwaysListening = true
                startListening()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        engine?.release()
        engine = null
        super.onDestroy()
    }

    private fun startListening() {
        if (engine == null) {
            engine = createEngine()
        }
        paused = false
        handler.removeCallbacks(autoResume)
        engine?.start()
    }

    private fun createEngine(): WakeWordEngine {
        val accessKey = preferences.accessKey
        if (accessKey.isNotBlank()) {
            try {
                return PorcupineWakeWordEngine(
                    context = this,
                    accessKey = accessKey,
                    sensitivity = preferences.sensitivity
                ) { onWakeWordDetected("") }
            } catch (exception: Exception) {
                // Access Key inválida ou recurso indisponível: mantém o
                // assistente funcional com o reconhecedor do sistema.
                Log.e(TAG, "Falha ao iniciar o Porcupine; usando SpeechRecognizer", exception)
            }
        }

        return SpeechWakeWordEngine(this) { transcript ->
            onWakeWordDetected(transcript)
        }
    }

    private fun pauseListening() {
        paused = true
        handler.removeCallbacks(autoResume)
        engine?.pause()
    }

    private fun resumeListening() {
        if (!paused) return
        paused = false
        wakeInProgress = false
        handler.removeCallbacks(autoResume)
        engine?.start()
    }

    private fun onWakeWordDetected(transcript: String) {
        if (wakeInProgress) return
        wakeInProgress = true
        pauseListening()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_WAKE_WORD, true)
            putExtra(EXTRA_TRANSCRIPT, transcript)
        }

        try {
            startActivity(launchIntent)
        } catch (exception: Exception) {
            // Android pode bloquear o início de atividades em segundo plano;
            // a notificação fixa continua disponível para abrir o app.
            Log.w(TAG, "Não foi possível abrir a tela principal", exception)
        }

        // Se a tela não avisar quando terminar, retoma sozinho.
        handler.postDelayed(autoResume, AUTO_RESUME_DELAY_MS)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_listening_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_listening_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, JarvisService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .build()
    }
}
