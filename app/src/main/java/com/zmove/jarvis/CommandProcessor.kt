package com.zmove.jarvis

import java.net.URLEncoder

/** Resultado do interpretador de comandos. */
sealed interface Command {

    data class OpenApp(
        val label: String,
        val packageName: String,
        val storeUrl: String
    ) : Command

    data class OpenUrl(val url: String) : Command

    data class SetTimer(val seconds: Int) : Command

    data object CurrentTime : Command

    data class Unknown(val transcript: String) : Command
}

/**
 * Interpreta frases em português brasileiro e devolve a ação correspondente.
 * Não depende do Android, então é coberto por testes unitários na JVM.
 */
class CommandProcessor {

    private class AppTarget(
        val label: String,
        val packageName: String,
        val storeUrl: String,
        vararg val aliases: String
    )

    private val apps = listOf(
        AppTarget(
            label = "Spotify",
            packageName = "com.spotify.music",
            storeUrl = "https://play.google.com/store/apps/details?id=com.spotify.music",
            "spotify", "musica", "musicas"
        ),
        AppTarget(
            label = "WhatsApp",
            packageName = "com.whatsapp",
            storeUrl = "https://play.google.com/store/apps/details?id=com.whatsapp",
            "whatsapp", "whats", "zap"
        ),
        AppTarget(
            label = "Chrome",
            packageName = "com.android.chrome",
            storeUrl = "https://play.google.com/store/apps/details?id=com.android.chrome",
            "chrome", "navegador", "browser"
        ),
        AppTarget(
            label = "YouTube",
            packageName = "com.google.android.youtube",
            storeUrl = "https://play.google.com/store/apps/details?id=com.google.android.youtube",
            "youtube"
        ),
        AppTarget(
            label = "Instagram",
            packageName = "com.instagram.android",
            storeUrl = "https://play.google.com/store/apps/details?id=com.instagram.android",
            "instagram"
        )
    )

    private val openVerbs = listOf(
        "abrir", "abra", "abre", "iniciar", "inicie", "inicia",
        "tocar", "toque", "executar", "executa", "lancar", "lanca"
    )

    private val searchVerbs = listOf(
        "pesquisar", "pesquise", "buscar", "busque", "procurar", "procure"
    )

    private val timerWithKeyword = Regex(
        """(?:timer|alarme|despertador|cronometro)\D{0,24}(\d{1,3})(?:\s*(segundos?|minutos?|horas?))?"""
    )

    private val timerRelative = Regex(
        """(?:em|daqui a|daqui)\s+(\d{1,3})\s*(segundos?|minutos?|horas?)"""
    )

    fun parse(raw: String): Command {
        val text = Speech.afterWakeWord(raw)
        if (text.isBlank()) return Command.Unknown(raw)

        parseTimer(text)?.let { return it }

        if (text.contains("que horas") || text.contains("hora agora")) {
            return Command.CurrentTime
        }

        if (
            text.contains("previsao do tempo") ||
            text.contains("clima") ||
            text.contains("vai chover")
        ) {
            return Command.OpenUrl(searchUrl("previsao do tempo"))
        }

        parseSearch(text)?.let { return it }

        matchApp(text)?.let { app ->
            return Command.OpenApp(app.label, app.packageName, app.storeUrl)
        }

        return Command.Unknown(raw)
    }

    private fun parseTimer(text: String): Command.SetTimer? {
        val match = timerWithKeyword.find(text) ?: timerRelative.find(text) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        val seconds = secondsFor(value, unit).coerceIn(1, 24 * 60 * 60)
        return Command.SetTimer(seconds)
    }

    private fun secondsFor(value: Int, unit: String?): Int = when {
        unit == null -> value * 60
        unit.startsWith("segundo") -> value
        unit.startsWith("hora") -> value * 60 * 60
        else -> value * 60
    }

    private fun parseSearch(text: String): Command.OpenUrl? {
        val verb = searchVerbs.firstOrNull { text == it || text.startsWith("$it ") } ?: return null
        var query = text.removePrefix(verb).trim()
        val engine = when {
            query.endsWith(" no youtube") -> "youtube"
            else -> "google"
        }
        query = query.removeSuffix(" no youtube").removeSuffix(" no google").trim()
        if (query.isBlank()) return null

        val url = if (engine == "youtube") {
            "https://www.youtube.com/results?search_query=${encode(query)}"
        } else {
            searchUrl(query)
        }
        return Command.OpenUrl(url)
    }

    private fun matchApp(text: String): AppTarget? {
        val hasOpenVerb = openVerbs.any { verb ->
            text == verb || text.startsWith("$verb ")
        }
        return apps.firstOrNull { app ->
            app.aliases.any { alias ->
                text == alias ||
                    (hasOpenVerb && Regex("""\b${Regex.escape(alias)}\b""").containsMatchIn(text))
            }
        }
    }

    private fun searchUrl(query: String): String =
        "https://www.google.com/search?q=${encode(query)}"

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
