package com.atelierjlg.fern.widgets

import com.atelierjlg.fern.data.CarnetDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Tests des calculs des widgets de la v0.14 (météo, plante, temps d'écran, examens). */
class CozyWidgetsTest {

    @Test
    fun `codes meteo WMO`() {
        assertEquals(WeatherKind.Soleil, WeatherCodes.kindOf(0))
        assertEquals(WeatherKind.Eclaircies, WeatherCodes.kindOf(2))
        assertEquals(WeatherKind.Brouillard, WeatherCodes.kindOf(45))
        assertEquals(WeatherKind.Pluie, WeatherCodes.kindOf(63))
        assertEquals(WeatherKind.Pluie, WeatherCodes.kindOf(81))
        assertEquals(WeatherKind.Neige, WeatherCodes.kindOf(73))
        assertEquals(WeatherKind.Orage, WeatherCodes.kindOf(95))
    }

    @Test
    fun `lecture de la reponse Open-Meteo`() {
        val body = """
            {"latitude":47.22,"current":{"time":"2026-10-06T14:00","temperature_2m":14.6,"weather_code":61,"is_day":1},
             "daily":{"time":["2026-10-06","2026-10-07"],"weather_code":[63,1],
                      "temperature_2m_max":[16.2,18.7],"temperature_2m_min":[9.4,8.1]}}
        """.trimIndent()
        val w = WeatherCodes.parse(body, now = 42L)
        assertNotNull(w)
        w!!
        assertEquals(15, w.temperature)
        assertEquals(WeatherKind.Pluie, w.kind)
        assertTrue(w.isDay)
        assertEquals(9, w.min)
        assertEquals(16, w.max)
        assertEquals(1, w.tomorrowCode)
        assertEquals(19, w.tomorrowMax)
        assertEquals(42L, w.fetchedAt)
    }

    @Test
    fun `reponse incomplete = null`() {
        assertEquals(null, WeatherCodes.parse("""{"current":{}}"""))
    }

    @Test
    fun `la plante pousse avec les habitudes`() {
        val today = LocalDate.of(2026, 10, 6)
        val empty = Plant.state(emptyMap(), today, 3)
        assertEquals(1, empty.fronds)
        assertTrue(empty.thirsty)

        // 7 jours avec les 3 habitudes : plante au maximum.
        val full = (0L..6L).associate { today.minusDays(it).toString() to CarnetDay(habits = setOf(0, 1, 2)) }
        val s = Plant.state(full, today, 3)
        assertEquals(7, s.fronds)
        assertEquals(7, s.activeDays)
        assertFalse(s.thirsty)

        // Seulement il y a 3 jours : elle a soif.
        val old = mapOf(today.minusDays(3).toString() to CarnetDay(habits = setOf(0)))
        assertTrue(Plant.state(old, today, 3).thirsty)
        assertEquals(1, Plant.state(old, today, 3).activeDays)
    }

    @Test
    fun `temps passe au premier plan`() {
        val e = listOf(
            ScreenTime.Event("insta", true, 1_000),
            ScreenTime.Event("insta", false, 61_000),
            ScreenTime.Event("signal", true, 70_000),
            ScreenTime.Event("signal", false, 80_000),
            ScreenTime.Event("insta", true, 100_000),
        )
        val t = ScreenTime.foregroundTimes(e, start = 0, end = 160_000)
        assertEquals(60_000L + 60_000L, t["insta"])
        assertEquals(10_000L, t["signal"])
    }

    @Test
    fun `appli deja ouverte a minuit`() {
        val t = ScreenTime.foregroundTimes(listOf(ScreenTime.Event("x", false, 5_000)), start = 0, end = 10_000)
        assertEquals(5_000L, t["x"])
    }

    @Test
    fun `format du temps`() {
        assertEquals("42 min", ScreenTime.format(42 * 60_000L))
        assertEquals("1 h 05", ScreenTime.format(65 * 60_000L))
    }

    @Test
    fun `reperer un examen dans un titre`() {
        val words = listOf("examen", "partiel", "ds", "controle")
        assertTrue(Agenda.isExam("Partiel de Mécanique", words))
        assertTrue(Agenda.isExam("DS maths", words))
        assertTrue(Agenda.isExam("Contrôle continu RDM", words))
        assertFalse(Agenda.isExam("Cours de DSP", words))
        assertFalse(Agenda.isExam("TD thermodynamique", words))
    }
}
