package com.zmove.jarvis

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Fallback sem Access Key: mantém o [SpeechRecognizer] do sistema em loop,
 * reiniciando após silêncio ou erros, e dispara quando a transcrição contém
 * a palavra "Jarvis".
 */
class SpeechWakeWordEngine(
    context: Context,
    private val onDetected: (String) -> Unit
) : WakeWordEngine {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var recognizing = false

    private val restartRecognition = Runnable { startRecognition() }

    override fun start() {
        if (active) return
        active = true
        startRecognition()
    }

    override fun pause() {
        active = false
        recognizing = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
    }

    override fun release() {
        pause()
        recognizer?.destroy()
        recognizer = null
    }

    private fun startRecognition() {
        if (!active || recognizing) return

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Log.w(TAG, "Reconhecimento de fala indisponível")
            active = false
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(recognitionListener)
            }
        }

        recognizing = true
        try {
            recognizer?.startListening(Speech.createRecognizerIntent(partialResults = true))
        } catch (exception: Exception) {
            Log.w(TAG, "Falha ao iniciar o reconhecimento", exception)
            recognizing = false
            scheduleRestart(1_500L)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!active) return
        handler.removeCallbacks(restartRecognition)
        handler.postDelayed(restartRecognition, delayMs)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            recognizing = false
            if (!active) return

            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Log.w(TAG, "Permissão de microfone ausente")
                    active = false
                }

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(400L)

                else -> scheduleRestart(1_500L)
            }
        }

        override fun onResults(results: Bundle?) {
            recognizing = false
            if (!active) return

            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()

            if (!transcript.isNullOrBlank() && Speech.containsWakeWord(transcript)) {
                active = false
                handler.removeCallbacksAndMessages(null)
                onDetected(transcript)
            } else {
                scheduleRestart(400L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!active) return

            val transcript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?: return

            if (Speech.containsWakeWord(transcript)) {
                active = false
                recognizing = false
                handler.removeCallbacksAndMessages(null)
                recognizer?.cancel()
                onDetected(transcript)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val TAG = "SpeechWakeWordEngine"
    }
}
