package com.zmove.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTest {

    @Test
    fun `normaliza acentos e caixa`() {
        assertEquals("jarvis, abrir arvore", Speech.normalize("  JÁRVIS, abrir ÁRVORE "))
    }

    @Test
    fun `detecta palavra de ativacao em qualquer posicao`() {
        assertTrue(Speech.containsWakeWord("oi, Jarvis!"))
        assertTrue(Speech.containsWakeWord("JÁRVIS"))
        assertTrue(!Speech.containsWakeWord("abrir whatsapp"))
    }

    @Test
    fun `remove palavra de ativacao e pontuacao inicial`() {
        assertEquals("abrir whatsapp", Speech.afterWakeWord("Jarvis, abrir WhatsApp"))
        assertEquals("que horas sao", Speech.afterWakeWord("jarvis que horas são"))
        assertEquals("abrir chrome", Speech.afterWakeWord("abrir chrome"))
    }
}
