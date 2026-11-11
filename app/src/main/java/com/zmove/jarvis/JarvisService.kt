package com.zmove.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Serviço de escuta contínua: mantém o microfone ativo em foreground e espera
 * pela palavra de ativação "Jarvis". Ao detectá-la, abre a [MainActivity] para
 * capturar o comando e encerra a escuta para liberar o microfone.
 *
 * Observação: o Android pode bloquear a abertura de atividades a partir do
 * segundo plano. Quando isso acontece, o usuário ainda pode tocar na notificação
 * fixa para abrir o assistente.
 */
class JarvisService : Service() {

    companion object {
        private const val TAG = "JarvisService"
        private const val CHANNEL_ID = "jarvis.listening"
        private const val NOTIFICATION_ID = 1001
        private const val RESTART_DELAY_MS = 500L

        const val ACTION_START = "com.zmove.jarvis.action.START"
        const val ACTION_STOP = "com.zmove.jarvis.action.STOP"
        const val EXTRA_WAKE_WORD = "extra_wake_word"
        const val EXTRA_TRANSCRIPT = "extra_transcript"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var active = false
    private var isRecognizing = false

    private val restartListening = Runnable { startListening() }

    override fun onCreate() {
        super.onCreate()
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
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        active = false
        isRecognizing = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun startListening() {
        active = true
        handler.removeCallbacks(restartListening)

        if (isRecognizing) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Reconhecimento de fala indisponível; encerrando o serviço.")
            stopSelf()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(recognitionListener)
            }
        }

        isRecognizing = true
        try {
            speechRecognizer?.startListening(Speech.createRecognizerIntent(partialResults = true))
        } catch (exception: Exception) {
            Log.w(TAG, "Falha ao iniciar o reconhecimento", exception)
            isRecognizing = false
            scheduleRestart(1_500L)
        }
    }

    private fun scheduleRestart(delayMs: Long = RESTART_DELAY_MS) {
        if (!active) return
        handler.removeCallbacks(restartListening)
        handler.postDelayed(restartListening, delayMs)
    }

    private fun onWakeWordDetected(transcript: String) {
        if (!active) return
        active = false
        isRecognizing = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.cancel()

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_WAKE_WORD, true)
            putExtra(EXTRA_TRANSCRIPT, transcript)
        }

        try {
            startActivity(launchIntent)
        } catch (exception: Exception) {
            // Restrição de início de atividade em segundo plano: a notificação
            // continua disponível para o usuário abrir o app manualmente.
            Log.w(TAG, "Não foi possível abrir a tela principal", exception)
        }

        stopSelf()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            isRecognizing = false
            if (!active) return

            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Log.w(TAG, "Permissão de microfone ausente; encerrando o serviço.")
                    stopSelf()
                }

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(400L)

                else -> scheduleRestart(1_500L)
            }
        }

        override fun onResults(results: Bundle?) {
            isRecognizing = false
            if (!active) return

            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()

            if (!transcript.isNullOrBlank() && Speech.containsWakeWord(transcript)) {
                onWakeWordDetected(transcript)
            } else {
                scheduleRestart()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active) return

            val transcript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?: return

            if (Speech.containsWakeWord(transcript)) {
                onWakeWordDetected(transcript)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
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
