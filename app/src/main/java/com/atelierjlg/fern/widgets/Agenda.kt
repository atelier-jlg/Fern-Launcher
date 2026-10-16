package com.atelierjlg.fern.widgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.atelierjlg.fern.apps.normalizeForSearch

/** Un agenda Android (par exemple celui créé par ICSx⁵ pour Proton Calendar). */
data class CalendarInfo(val id: Long, val name: String, val account: String)

/** Un événement de l'agenda (cours, examen…). */
data class AgendaEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
)

/** Ce qu'affiche le widget « Cours du jour ». */
data class CoursState(
    val hasPermission: Boolean = false,
    /** Le cours en cours ou le prochain (aujourd'hui ou demain). */
    val next: AgendaEvent? = null,
    /** Le prochain examen dans les 60 jours. */
    val exam: AgendaEvent? = null,
)

/** Lecture de l'agenda Android pour le widget « Cours du jour ». */
object Agenda {

    /** Le titre ressemble-t-il à un examen ? On compare mot à mot, sans accents ni majuscules. */
    fun isExam(title: String, keywords: List<String>): Boolean {
        val words = title.normalizeForSearch().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        return keywords.any { it.normalizeForSearch().trim() in words }
    }

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                ),
                null,
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) add(CalendarInfo(c.getLong(0), c.getString(1) ?: "?", c.getString(2) ?: ""))
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Les événements (hors journées entières) entre `from` et `to`, des agendas choisis (vide = tous). */
    fun events(context: Context, from: Long, to: Long, calendarIds: Set<Long>): List<AgendaEvent> {
        if (!hasPermission(context)) return emptyList()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from)
        ContentUris.appendId(builder, to)
        var selection = "${CalendarContract.Instances.ALL_DAY} = 0"
        if (calendarIds.isNotEmpty()) {
            selection += " AND ${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",")})"
        }
        return runCatching {
            context.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION,
                ),
                selection,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(AgendaEvent(c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getLong(3), c.getString(4) ?: ""))
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Le cours en cours ou à venir (jusqu'à demain soir), et le prochain examen (60 jours). */
    fun coursState(context: Context, calendarIds: Set<Long>, examKeywords: List<String>, now: Long = System.currentTimeMillis()): CoursState {
        if (!hasPermission(context)) return CoursState(hasPermission = false)
        val day = 24L * 60 * 60 * 1000
        val soon = events(context, now - 12 * 60 * 60 * 1000L, now + 2 * day, calendarIds)
        val next = soon.firstOrNull { it.end > now && it.begin < now + 2 * day }
        val exam = events(context, now, now + 60 * day, calendarIds).firstOrNull { isExam(it.title, examKeywords) }
        return CoursState(hasPermission = true, next = next, exam = exam)
    }
}
