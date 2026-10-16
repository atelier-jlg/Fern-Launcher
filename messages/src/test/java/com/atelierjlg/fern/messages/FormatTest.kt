package com.atelierjlg.fern.messages

import com.atelierjlg.fern.messages.data.formatDayHeader
import com.atelierjlg.fern.messages.data.formatListTime
import com.atelierjlg.fern.messages.data.formatScheduled
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(m: Int, d: Int, h: Int, y: Int = 2026) = LocalDateTime.of(y, m, d, h, 5).atZone(zone).toInstant().toEpochMilli()
    private val now = at(10, 8, 20)

    @Test
    fun liste() {
        assertEquals("14:05", formatListTime(at(10, 8, 14), now, zone))
        assertEquals("Hier", formatListTime(at(10, 7, 14), now, zone))
        assertEquals("lun.", formatListTime(at(10, 5, 14), now, zone))
        assertEquals("6 sept.", formatListTime(at(9, 6, 14), now, zone))
        assertEquals("6 sept. 2025", formatListTime(at(9, 6, 14, 2025), now, zone))
    }

    @Test
    fun jours() {
        val today = LocalDate.of(2026, 10, 8)
        assertEquals("Aujourd'hui", formatDayHeader(today, today))
        assertEquals("Hier", formatDayHeader(today.minusDays(1), today))
        assertEquals("lundi 5 octobre", formatDayHeader(today.minusDays(3), today))
    }

    @Test
    fun programmes() {
        assertEquals("demain à 08:05", formatScheduled(at(10, 9, 8), now, zone))
        assertEquals("aujourd'hui à 22:05", formatScheduled(at(10, 8, 22), now, zone))
    }
}
