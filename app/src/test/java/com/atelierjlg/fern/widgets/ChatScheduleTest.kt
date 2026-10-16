package com.atelierjlg.fern.widgets

import com.atelierjlg.fern.data.ChatSettings
import com.atelierjlg.fern.data.ChoreFreq
import com.atelierjlg.fern.data.ChoreRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Les rythmes des tâches du chat. 6 octobre 2026 = un mardi ; 3 et 10 octobre = des samedis. */
class ChatScheduleTest {

    private val gamelle = ChoreRule(ChoreFreq.DeuxParJour)
    private val litiere = ChoreRule(ChoreFreq.Hebdomadaire, day = 6, hour = 18)

    @Test
    fun `gamelle - matin puis soir, remise a zero a midi et minuit`() {
        assertEquals("2026-10-06-matin", ChatSchedule.periodKey(gamelle, LocalDateTime.of(2026, 10, 6, 0, 0)))
        assertEquals("2026-10-06-matin", ChatSchedule.periodKey(gamelle, LocalDateTime.of(2026, 10, 6, 11, 59)))
        assertEquals("2026-10-06-soir", ChatSchedule.periodKey(gamelle, LocalDateTime.of(2026, 10, 6, 12, 0)))
        assertEquals("2026-10-07-matin", ChatSchedule.periodKey(gamelle, LocalDateTime.of(2026, 10, 7, 0, 1)))
    }

    @Test
    fun `litiere - une semaine, du samedi 18 h au samedi 18 h`() {
        assertEquals("2026-10-03-semaine", ChatSchedule.periodKey(litiere, LocalDateTime.of(2026, 10, 6, 13, 0)))
        assertEquals("2026-10-03-semaine", ChatSchedule.periodKey(litiere, LocalDateTime.of(2026, 10, 10, 17, 59)))
        assertEquals("2026-10-10-semaine", ChatSchedule.periodKey(litiere, LocalDateTime.of(2026, 10, 10, 18, 0)))
        assertEquals("2026-10-10-semaine", ChatSchedule.periodKey(litiere, LocalDateTime.of(2026, 10, 11, 9, 0)))
    }

    @Test
    fun `case cochee le matin, decochee l'apres-midi`() {
        val settings = ChatSettings(
            chores = listOf("Gamelle", "Litière"),
            rules = listOf(gamelle, litiere),
            done = mapOf("2026-10-06-matin" to setOf(0), "2026-10-03-semaine" to setOf(1)),
        )
        assertTrue(ChatSchedule.isDone(settings, 0, LocalDateTime.of(2026, 10, 6, 8, 0)))
        assertFalse(ChatSchedule.isDone(settings, 0, LocalDateTime.of(2026, 10, 6, 13, 0)))
        assertTrue(ChatSchedule.isDone(settings, 1, LocalDateTime.of(2026, 10, 9, 20, 0)))
        assertFalse(ChatSchedule.isDone(settings, 1, LocalDateTime.of(2026, 10, 10, 19, 0)))
    }
}
