package com.zmove.jarvis

import android.content.Intent
import android.speech.RecognizerIntent
import java.text.Normalizer
import java.util.Locale

/**
 * Helpers compartilhados entre a tela principal e o serviço de escuta contínua.
 * Mantido sem dependências do Android além do [Intent] para poder ser testado
 * em testes unitários da JVM.
 */
object Speech {

    const val LANGUAGE_TAG = "pt-BR"
    const val WAKE_WORD = "jarvis"

    fun createRecognizerIntent(partialResults: Boolean = true): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_TAG)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE_TAG)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    /** Remove acentos e normaliza a caixa para comparar comandos falados. */
    fun normalize(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").trim()
    }

    fun containsWakeWord(text: String): Boolean = normalize(text).contains(WAKE_WORD)

    /**
     * Devolve o texto normalizado que vem depois da palavra de ativação.
     * Se a palavra não estiver presente, devolve o texto normalizado inteiro.
     */
    fun afterWakeWord(text: String): String {
        val normalized = normalize(text)
        val index = normalized.indexOf(WAKE_WORD)
        return if (index >= 0) {
            normalized.substring(index + WAKE_WORD.length)
                .trim()
                .trimStart(',', '.', '!', '?', ';', ':', '-', ' ')
                .trim()
        } else {
            normalized
        }
    }
}
