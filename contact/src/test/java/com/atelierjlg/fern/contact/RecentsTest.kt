package com.atelierjlg.fern.contact

import android.provider.CallLog.Calls
import com.atelierjlg.fern.contact.data.CallEntry
import com.atelierjlg.fern.contact.data.formatCallTime
import com.atelierjlg.fern.contact.data.formatDuration
import com.atelierjlg.fern.contact.data.groupRecents
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(day: Int, h: Int) = LocalDateTime.of(2026, 10, day, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun e(id: Long, n: String, type: Int, day: Int, h: Int) = CallEntry(id, n, null, type, at(day, h), 30)

    @Test
    fun regroupement() {
        val list = listOf(
            e(1, "0612345678", Calls.MISSED_TYPE, 8, 18),
            e(2, "+33612345678", Calls.MISSED_TYPE, 8, 17),
            e(3, "0612345678", Calls.INCOMING_TYPE, 8, 16),
            e(4, "0612345678", Calls.INCOMING_TYPE, 7, 16),
            e(5, "0700000000", Calls.OUTGOING_TYPE, 7, 15),
        )
        val groups = groupRecents(list, zone)
        assertEquals(listOf(2, 1, 1, 1), groups.map { it.count })
        assertEquals(true, groups[0].isMissed)
    }

    @Test
    fun dates() {
        val now = at(8, 20)
        assertEquals("Aujourd'hui · 18:00", formatCallTime(at(8, 18), now, zone))
        assertEquals("Hier · 16:00", formatCallTime(at(7, 16), now, zone))
        assertEquals("lun. 5 oct. · 15:00", formatCallTime(at(5, 15), now, zone))
    }

    @Test
    fun durees() {
        assertEquals("", formatDuration(0))
        assertEquals("42 s", formatDuration(42))
        assertEquals("1 min 05", formatDuration(65))
        assertEquals("1 h 02", formatDuration(3720))
    }
}
