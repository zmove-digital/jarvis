package com.zmove.jarvis

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager

/**
 * Wake word local via Picovoice Porcupine. Processa o áudio no próprio
 * dispositivo, sem enviar nada para a nuvem, e usa pouca bateria.
 */
class PorcupineWakeWordEngine(
    context: Context,
    accessKey: String,
    sensitivity: Float,
    private val onDetected: () -> Unit
) : WakeWordEngine {

    private val manager: PorcupineManager = PorcupineManager.Builder()
        .setAccessKey(accessKey)
        .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
        .setSensitivity(sensitivity)
        .setErrorCallback { error -> Log.e(TAG, "Erro no Porcupine", error) }
        .build(context.applicationContext) { onDetected() }

    private var started = false
    private var released = false

    override fun start() {
        if (started || released) return
        try {
            manager.start()
            started = true
        } catch (exception: Exception) {
            Log.e(TAG, "Falha ao iniciar o Porcupine", exception)
        }
    }

    override fun pause() {
        if (!started) return
        started = false
        try {
            manager.stop()
        } catch (exception: Exception) {
            Log.w(TAG, "Falha ao pausar o Porcupine", exception)
        }
    }

    override fun release() {
        if (released) return
        pause()
        released = true
        manager.delete()
    }

    private companion object {
        const val TAG = "PorcupineEngine"
    }
}
