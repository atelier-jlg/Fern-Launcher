package com.atelierjlg.fern.messages.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val DAYS = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
private val MONTHS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
private val DAYS_LONG = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
private val MONTHS_LONG = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")

/** Heure dans la liste : « 14:02 », « Hier », « lun. », « 6 oct. », « 6 oct. 2025 ». */
fun formatListTime(date: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(date).atZone(zone)
    val d = t.toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        d == today -> "%02d:%02d".format(t.hour, t.minute)
        d == today.minusDays(1) -> "Hier"
        d.isAfter(today.minusDays(7)) -> DAYS[d.dayOfWeek.value - 1]
        d.year == today.year -> "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
        else -> "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
    }
}

/** Séparateur de jour dans une conversation : « AUJOURD'HUI », « HIER », « LUNDI 6 OCTOBRE ». */
fun formatDayHeader(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Aujourd'hui"
    today.minusDays(1) -> "Hier"
    else -> "${DAYS_LONG[day.dayOfWeek.value - 1]} ${day.dayOfMonth} ${MONTHS_LONG[day.monthValue - 1]}" +
        if (day.year != today.year) " ${day.year}" else ""
}

fun dayOf(date: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()

fun formatHour(date: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(date).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

/** « demain à 08:30 », « lundi 6 octobre à 18:00 » (messages programmés). */
fun formatScheduled(date: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val d = dayOf(date, zone)
    val today = dayOf(now, zone)
    val day = when (d) {
        today -> "aujourd'hui"
        today.plusDays(1) -> "demain"
        else -> formatDayHeader(d, today)
    }
    return "$day à ${formatHour(date, zone)}"
}
