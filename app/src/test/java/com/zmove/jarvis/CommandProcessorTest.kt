package com.zmove.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandProcessorTest {

    private val processor = CommandProcessor()

    @Test
    fun `abre whatsapp apos a palavra de ativacao`() {
        val command = processor.parse("Jarvis, abrir WhatsApp")

        assertTrue(command is Command.OpenApp)
        assertEquals("com.whatsapp", (command as Command.OpenApp).packageName)
    }

    @Test
    fun `reconhece apelidos de aplicativos`() {
        val chrome = processor.parse("abra o navegador")
        val spotify = processor.parse("tocar música")

        assertEquals("com.android.chrome", (chrome as Command.OpenApp).packageName)
        assertEquals("com.spotify.music", (spotify as Command.OpenApp).packageName)
    }

    @Test
    fun `interpreta timer em minutos e segundos`() {
        assertEquals(Command.SetTimer(300), processor.parse("definir timer de 5 minutos"))
        assertEquals(Command.SetTimer(30), processor.parse("timer de 30 segundos"))
    }

    @Test
    fun `interpreta timer relativo em horas`() {
        assertEquals(Command.SetTimer(7200), processor.parse("me avise daqui a 2 horas"))
    }

    @Test
    fun `pesquisa no youtube`() {
        val command = processor.parse("pesquisar receita de bolo no YouTube")

        assertTrue(command is Command.OpenUrl)
        assertTrue((command as Command.OpenUrl).url.startsWith("https://www.youtube.com/results?search_query="))
    }

    @Test
    fun `consulta a hora atual`() {
        assertEquals(Command.CurrentTime, processor.parse("que horas são?"))
    }

    @Test
    fun `consulta a previsao do tempo`() {
        val command = processor.parse("qual é a previsão do tempo")

        assertTrue(command is Command.OpenUrl)
        assertTrue((command as Command.OpenUrl).url.startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun `frase desconhecida vira comando desconhecido`() {
        val command = processor.parse("faça um café")

        assertTrue(command is Command.Unknown)
        assertEquals("faça um café", (command as Command.Unknown).transcript)
    }
}
