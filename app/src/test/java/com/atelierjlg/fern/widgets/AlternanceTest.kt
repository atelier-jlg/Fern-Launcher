package com.atelierjlg.fern.widgets

import com.atelierjlg.fern.data.AltType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AlternanceTest {

    // Lundi 5 octobre 2026 : 2 semaines d'école puis 3 en entreprise.
    private val periods = Alternance.generate(
        start = LocalDate.of(2026, 10, 7),
        until = LocalDate.of(2026, 12, 31),
        schoolWeeks = 2,
        companyWeeks = 3,
        first = AltType.Ecole,
    )

    @Test
    fun generatesAlternatingPeriods() {
        assertEquals("2026-10-05", periods[0].start)
        assertEquals("2026-10-18", periods[0].end)
        assertEquals(AltType.Entreprise, periods[1].type)
        assertEquals("2026-10-19", periods[1].start)
        assertEquals("2026-11-08", periods[1].end)
        assertEquals("2026-12-31", periods.last().end)
    }

    @Test
    fun currentAndNext() {
        val day = LocalDate.of(2026, 10, 14)
        assertEquals(AltType.Ecole, Alternance.periodAt(periods, day)?.type)
        val next = Alternance.nextChange(periods, day)!!
        assertEquals(AltType.Entreprise, next.period.type)
        assertEquals(5, next.daysUntil)
        assertNull(Alternance.periodAt(periods, LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun weekStrip() {
        val strip = Alternance.weekStrip(periods, LocalDate.of(2026, 10, 7), weeks = 6)
        assertEquals(
            listOf(AltType.Ecole, AltType.Ecole, AltType.Entreprise, AltType.Entreprise, AltType.Entreprise, AltType.Ecole),
            strip,
        )
    }

    @Test
    fun parsesFrenchDates() {
        val today = LocalDate.of(2026, 10, 6)
        assertEquals(LocalDate.of(2026, 11, 3), Alternance.parseDate("3/11", today))
        assertEquals(LocalDate.of(2027, 1, 15), Alternance.parseDate("15/01/2027", today))
        assertEquals(LocalDate.of(2027, 1, 15), Alternance.parseDate("15/01/27", today))
        assertNull(Alternance.parseDate("bonjour", today))
    }
}
