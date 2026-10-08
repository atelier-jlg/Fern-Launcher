package com.atelierjlg.fern.widgets

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * L'emploi du temps de l'école, lu directement depuis son lien « iCal » (.ics), sans ICSx⁵.
 *
 * Un fichier .ics, c'est du texte : des blocs BEGIN:VEVENT … END:VEVENT, un par cours,
 * avec des lignes « CLÉ:valeur » (SUMMARY = titre, DTSTART = début, LOCATION = salle…).
 * Un peu comme un fichier INI en Python, qu'on lit ligne par ligne.
 */
object IcsCalendar {

    /** Le dernier problème (null = tout va bien), affiché dans Paramètres → Cours du jour. */
    @Volatile
    var lastError: String? = null
        private set

    /** `webcal://` n'est qu'un surnom de `https://`. */
    fun normalizeUrl(url: String): String {
        val u = url.trim()
        return when {
            u.startsWith("webcal://", ignoreCase = true) -> "https://" + u.substring(9)
            u.startsWith("webcals://", ignoreCase = true) -> "https://" + u.substring(10)
            else -> u
        }
    }

    /** Télécharge le fichier et l'enregistre dans `cache`. true si réussi. (Sur Dispatchers.IO.) */
    fun download(url: String, cache: File): Boolean = runCatching {
        val connection = URL(normalizeUrl(url)).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "FernLauncher")
        try {
            val code = connection.responseCode
            if (code != 200) {
                lastError = "Le serveur de l'école a répondu HTTP $code"
                return false
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            if (!text.contains("BEGIN:VCALENDAR")) {
                lastError = "Ce lien n'est pas un agenda (.ics) : cherche le bouton « Exporter » ou « iCal »"
                return false
            }
            cache.writeText(text)
            lastError = null
            true
        } finally {
            connection.disconnect()
        }
    }.getOrElse { e ->
        lastError = "${e.javaClass.simpleName} : ${e.message ?: "?"}"
        false
    }

    /** Les lignes « dépliées » : en .ics, une ligne trop longue continue sur la suivante, qui commence par un espace. */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + raw.substring(1)
            } else {
                out += raw
            }
        }
        return out
    }

    private fun unescape(value: String) =
        value.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\;", ";").replace("\\\\", "\\").trim()

    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * Une date .ics → millisecondes. Formes acceptées :
     * `20261012T081500Z` (heure UTC), `TZID=Europe/Paris:20261012T081500` (fuseau donné),
     * `20261012T081500` (heure locale). null pour une journée entière (`VALUE=DATE:20261012`).
     */
    fun parseInstant(params: String, value: String, defaultZone: ZoneId): Long? = runCatching {
        if (value.length == 8) return null // journée entière : on l'ignore (pas un cours)
        val utc = value.endsWith("Z")
        val local = LocalDateTime.parse(value.removeSuffix("Z").take(15), dateTime)
        val zone = when {
            utc -> ZoneOffset.UTC
            else -> Regex("TZID=([^;:]+)").find(params)?.groupValues?.get(1)
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone
        }
        local.atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** Lit le texte .ics et renvoie les cours (événements avec une heure), triés par date. */
    fun parse(text: String, defaultZone: ZoneId = ZoneId.systemDefault()): List<AgendaEvent> {
        val events = mutableListOf<AgendaEvent>()
        var inEvent = false
        var title = ""
        var location = ""
        var begin: Long? = null
        var end: Long? = null
        for (line in unfold(text)) {
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true; title = ""; location = ""; begin = null; end = null
                }
                line == "END:VEVENT" -> {
                    val b = begin
                    if (inEvent && b != null) {
                        val e = end ?: (b + 60 * 60 * 1000L)
                        events += AgendaEvent(eventId = -1, title = title.ifBlank { "Cours" }, begin = b, end = e, location = location)
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val head = line.substring(0, colon)
                    val value = line.substring(colon + 1)
                    val key = head.substringBefore(';').uppercase()
                    val params = if (head.contains(';')) head.substringAfter(';') else ""
                    when (key) {
                        "SUMMARY" -> title = unescape(value)
                        "LOCATION" -> location = unescape(value)
                        "DTSTART" -> begin = parseInstant(params, value, defaultZone)
                        "DTEND" -> end = parseInstant(params, value, defaultZone)
                    }
                }
            }
        }
        return events.sortedBy { it.begin }
    }

    /** Le cours en cours ou à venir (jusqu'à demain soir) et le prochain examen, comme pour l'agenda Android. */
    fun coursState(events: List<AgendaEvent>, examKeywords: List<String>, now: Long = System.currentTimeMillis()): CoursState {
        val day = 24L * 60 * 60 * 1000
        val next = events.firstOrNull { it.end > now && it.begin < now + 2 * day }
        val exam = events.firstOrNull { it.begin >= now && it.begin < now + 60 * day && Agenda.isExam(it.title, examKeywords) }
        return CoursState(hasPermission = true, next = next, exam = exam)
    }
}
