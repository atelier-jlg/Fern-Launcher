package com.atelierjlg.fern.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class IcsCalendarTest {

    private val paris = ZoneId.of("Europe/Paris")
    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(paris).toInstant().toEpochMilli()

    private val ics = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:1
        DTSTART;TZID=Europe/Paris:20261026T081500
        DTEND;TZID=Europe/Paris:20261026T101500
        SUMMARY:Mécanique des fluides
        LOCATION:B204\, bâtiment B
        END:VEVENT
        BEGIN:VEVENT
        UID:2
        DTSTART:20261027T130000Z
        DTEND:20261027T150000Z
        SUMMARY:Partiel de RDM - amphi
          thé A
        END:VEVENT
        BEGIN:VEVENT
        UID:3
        DTSTART;VALUE=DATE:20261101
        SUMMARY:Toussaint
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun `lecture d'un fichier ics d'ecole`() {
        val events = IcsCalendar.parse(ics, paris)
        assertEquals(2, events.size) // la journée entière (Toussaint) est ignorée
        val first = events[0]
        assertEquals("Mécanique des fluides", first.title)
        assertEquals("B204, bâtiment B", first.location)
        assertEquals(millis(2026, 10, 26, 8, 15), first.begin)
        assertEquals(millis(2026, 10, 26, 10, 15), first.end)
        // 13:00 UTC = 14:00 à Paris (heure d'hiver le 27 octobre)
        assertEquals(millis(2026, 10, 27, 14, 0), events[1].begin)
        // Ligne pliée : « amphi » + « thé A » (le 1er espace de la suite est retiré)
        assertEquals("Partiel de RDM - amphi thé A", events[1].title)
    }

    @Test
    fun `cours suivant et examen`() {
        val events = IcsCalendar.parse(ics, paris)
        val state = IcsCalendar.coursState(events, listOf("partiel"), now = millis(2026, 10, 26, 9, 0))
        assertEquals("Mécanique des fluides", state.next?.title)
        assertNotNull(state.exam)
        assertEquals("Partiel de RDM - amphi thé A", state.exam?.title)
    }

    @Test
    fun `webcal devient https`() {
        assertEquals("https://ecole.fr/cal.ics", IcsCalendar.normalizeUrl(" webcal://ecole.fr/cal.ics "))
    }
}
