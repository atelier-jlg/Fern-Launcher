package com.atelierjlg.fern.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class AstroTest {

    private fun minutesBetween(a: LocalTime, b: LocalTime) = abs(ChronoUnit.MINUTES.between(a, b))

    @Test
    fun nantesSunTimes() {
        // Nantes, 6 octobre : lever ≈ 8 h 11, coucher ≈ 19 h 38 (heure d'été).
        val t = Astro.sunTimes(LocalDate.of(2026, 10, 6), 47.2184, -1.5536, ZoneId.of("Europe/Paris"))
        assertTrue(minutesBetween(t.sunrise!!, LocalTime.of(8, 11)) <= 6)
        assertTrue(minutesBetween(t.sunset!!, LocalTime.of(19, 38)) <= 6)
    }

    @Test
    fun knownFullMoon() {
        // Pleine lune du 17 octobre 2024 à 11 h 26 UTC.
        val millis = ZonedDateTime.of(2024, 10, 17, 11, 26, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val phase = Astro.moonPhase(millis)
        assertEquals(0.5, phase, 0.03)
        assertEquals("Pleine lune", Astro.moonPhaseName(phase))
        assertEquals(1.0, Astro.moonIllumination(phase), 0.02)
    }
}
