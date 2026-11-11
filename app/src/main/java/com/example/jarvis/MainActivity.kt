package com.example.jarvis

import android.content.Intent
import.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Se o serviço já detectou a palavra‑chave, inicia o reconhecedor imediatamente
        if (intent?.getBooleanExtra("wakeWordDetected", false) == true) {
            // Pequena pausa para o serviço soltar o foco do microfone
           Handler().postDelayed({ startListening() }, 500)
        }

        btnOuvir.setOnClickListener { startListening() }
    }

    private fun startListening() {
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let { frases ->
                    frases.forEach { frase ->
                        processCommand(frase)
                    }
                }
            }

            override fun onError(error: Int) {
                Log.e("SR", "Erro $error")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onAudioLevelsChanged(rms: Float, frequency: Float) {}
        })

        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun processCommand(frase: String) {
        val lower = frase.toLowerCase().trim()

        when {
            lower.contains("abrir spotify") -> {
                val intent = Intent().setPackage("com.spotify.music")
                intent.setAction("android.intent.action.RUN")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")))
                }
            }
            lower.contains("abrir whatsapp") -> {
                val intent = Intent().setPackage("com.whatsapp")
                startActivity(intent)
            }
            lower.contains("abra o chrome") || lower.contains("abra o browser") -> {
                startActivity(Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("https://www.google.com")))
            }
            lower.contains("definir timer") -> {
                // Aqui poderia usar AlarmManager; para o exemplo apenas um toast
                android.widget.Toast.makeText(this, "Timer definido!", android.widget.Toast.LENGTH_SHORT).show()
            }
            lower.contains("qual é a previsão do tempo") -> {
                // TODO: chamar a API Google AI (Gemini) com a chave do usuário
                android.widget.Toast.makeText(this, "Buscando previsão do tempo…", android.widget.Toast.LENGTH_SHORT).show()
            }
            else -> {
                android.widget.Toast.makeText(this, "Você disse: $frase", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}