package com.example.jarvis

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Log

class JarvisService : android.app.Service() {

    companion object {
        const val ACTION_START = "com.example.jarvis.START_SERVICE"
        const val ACTION_STOP  = "com.example.jarvis.STOP_SERVICE"
    }

    private var wakeWordDetector: Any? = null // placeholder for Picovoice WakeWordDetector
    private var audioRecorder: android.media.AudioRecord? = null
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        // TODO: inicializar wakeWordDetector com a Access‑Key da Picovoice
        // wakeWordDetector = WakeWordDetector("SUA_ACCESS_KEY_AQUI") { onKeywordDetected() }

        // Para testes, vamos usar o Google Speech Recognition a partir de um botão.
        // O serviço ficará em foreground com a notificação.
        startForeground(1, showListeningNotification())
        // Simular captura de áudio em loop (apenas para manter o serviço vivo)
        startAudioLoop()
    }

    private fun startAudioLoop() {
        val sampleRate = 16000
        val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
        val format = android.media.AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, format)
        audioRecorder = android.media.AudioRecord(MediaAudioSource.MIC, sampleRate, channelConfig, format, bufferSize)

        val thread = Thread {
            audioRecorder?.startRecording()
            val buffer = ShortArray(bufferSize / 2)
            while (isListening) {
                val count = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
                // Aqui poderíamos enviar o buffer para o Picovoice ou Google Speech
                // Para o exemplo, apenas consumimos o áudio para manter o serviço vivo.
            }
        }
        thread.start()
    }

    private fun onKeywordDetected() {
        // Quando a palavra‑chave “Jarvis” for detectada, lanzamos a UI principal
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("wakeWordDetected", true)
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        // Após processar, podemos parar a gravação temporária
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
    }

    private fun showListeningNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jarvis.listening",
                "Jarvis Listening",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, JarvisService::class.java).apply { setAction(JarvisService.ACTION_STOP) }
        val pendingStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "jarvis.listening")
            .setContentTitle("Jarvis – ouvindo…")
            .setContentText("Diga ‘Jarvis’ + comando")
            .setSmallIcon(android.R.drawable.ic_media_play) // ícone placeholder; substitua pelo seu recurso
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close, "Interromper", pendingStop)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let {
            when {
                it == ACTION_START -> {
                    isListening = true
                    // O serviço já foi iniciado em onCreate com foreground service
                    return START_STICKY
                }
                it == ACTION_STOP -> {
                    isListening = false
                    stopForeground(true)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        audioRecorder?.stop()
        audioRecorder?.release()
        // if (wakeWordDetector != null) wakeWordDetector?.close()
    }
}