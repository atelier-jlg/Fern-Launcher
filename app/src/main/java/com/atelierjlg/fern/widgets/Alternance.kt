package com.atelierjlg.fern.widgets

import com.atelierjlg.fern.data.AltPeriod
import com.atelierjlg.fern.data.AltType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Les calculs du widget Alternance (sans Android : testés automatiquement). */
object Alternance {

    private fun AltPeriod.startDate(): LocalDate = LocalDate.parse(start)
    private fun AltPeriod.endDate(): LocalDate = LocalDate.parse(end)

    /** La période en cours à cette date (null = vacances, ou rien de prévu). */
    fun periodAt(periods: List<AltPeriod>, date: LocalDate): AltPeriod? =
        periods.firstOrNull { !date.isBefore(it.startDate()) && !date.isAfter(it.endDate()) }

    /** Le prochain changement : la prochaine période d'un autre type (ou la prochaine tout court). */
    data class Change(val period: AltPeriod, val daysUntil: Long)

    fun nextChange(periods: List<AltPeriod>, date: LocalDate): Change? {
        val current = periodAt(periods, date)
        val upcoming = periods
            .filter { it.startDate().isAfter(date) }
            .sortedBy { it.start }
            .firstOrNull { current == null || it.type != current.type }
            ?: return null
        return Change(upcoming, ChronoUnit.DAYS.between(date, upcoming.startDate()))
    }

    /** Le type de chaque semaine (regardé le mercredi), à partir de la semaine de `date`. */
    fun weekStrip(periods: List<AltPeriod>, date: LocalDate, weeks: Int = 8): List<AltType?> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until weeks).map { w -> periodAt(periods, monday.plusWeeks(w.toLong()).plusDays(2))?.type }
    }

    /**
     * Génère un rythme régulier : `schoolWeeks` semaines d'école puis `companyWeeks` en entreprise
     * (ou l'inverse selon `first`), à partir du lundi de `start`, jusqu'à `until`.
     */
    fun generate(start: LocalDate, until: LocalDate, schoolWeeks: Int, companyWeeks: Int, first: AltType): List<AltPeriod> {
        require(schoolWeeks > 0 && companyWeeks > 0)
        val result = mutableListOf<AltPeriod>()
        var cursor = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var type = first
        while (!cursor.isAfter(until)) {
            val weeks = if (type == AltType.Ecole) schoolWeeks else companyWeeks
            val end = cursor.plusWeeks(weeks.toLong()).minusDays(1).let { if (it.isAfter(until)) until else it }
            result += AltPeriod(type, cursor.toString(), end.toString())
            cursor = cursor.plusWeeks(weeks.toLong())
            type = if (type == AltType.Ecole) AltType.Entreprise else AltType.Ecole
        }
        return result
    }

    private val shortFormat = DateTimeFormatter.ofPattern("dd/MM")

    fun shortDate(iso: String): String = runCatching { LocalDate.parse(iso).format(shortFormat) }.getOrDefault(iso)

    /** « 06/10/2026 », « 6/10 » (année en cours), « 2026-10-06 » → date, ou null. */
    fun parseDate(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val t = text.trim()
        runCatching { return LocalDate.parse(t) }
        val parts = t.split('/', '-', '.', ' ').filter { it.isNotBlank() }
        return runCatching {
            when (parts.size) {
                2 -> LocalDate.of(today.year, parts[1].toInt(), parts[0].toInt())
                3 -> LocalDate.of(parts[2].toInt().let { if (it < 100) 2000 + it else it }, parts[1].toInt(), parts[0].toInt())
                else -> null
            }
        }.getOrNull()
    }
}
