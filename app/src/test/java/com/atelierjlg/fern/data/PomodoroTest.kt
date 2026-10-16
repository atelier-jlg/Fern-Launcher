package com.atelierjlg.fern.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le Pomodoro et le mode Focus : travail → pause → arrêt, Focus remis comme avant. */
class PomodoroTest {

    private val min = 60_000L

    @Test
    fun `cycle complet avec Focus automatique`() {
        var c = LauncherConfig().startPomodoro(now = 0)
        assertEquals(PomodoroPhase.Travail, c.pomodoro.phase)
        assertEquals(25 * min, c.pomodoro.endsAt)
        assertTrue(c.focus.enabled)

        c = c.tickPomodoro(10 * min)
        assertEquals(PomodoroPhase.Travail, c.pomodoro.phase)

        c = c.tickPomodoro(25 * min)
        assertEquals(PomodoroPhase.Pause, c.pomodoro.phase)
        assertEquals(30 * min, c.pomodoro.endsAt)
        assertFalse(c.focus.enabled)

        c = c.tickPomodoro(30 * min)
        assertEquals(PomodoroPhase.Arret, c.pomodoro.phase)
    }

    @Test
    fun `retour apres une longue veille`() {
        val c = LauncherConfig().startPomodoro(now = 0).tickPomodoro(3 * 60 * min)
        assertEquals(PomodoroPhase.Arret, c.pomodoro.phase)
        assertFalse(c.focus.enabled)
    }

    @Test
    fun `Focus deja actif reste actif`() {
        val start = LauncherConfig(focus = FocusSettings(enabled = true))
        assertTrue(start.startPomodoro(0).stopPomodoro().focus.enabled)
        assertTrue(start.startPomodoro(0).tickPomodoro(25 * min).focus.enabled)
    }

    @Test
    fun `arret pendant le travail remet le Focus`() {
        val c = LauncherConfig().startPomodoro(0).stopPomodoro()
        assertEquals(PomodoroPhase.Arret, c.pomodoro.phase)
        assertFalse(c.focus.enabled)
    }

    @Test
    fun `relancer pendant le travail garde l'etat d'origine du Focus`() {
        val c = LauncherConfig().startPomodoro(0).startPomodoro(5 * min).stopPomodoro()
        assertFalse(c.focus.enabled)
    }

    @Test
    fun `taches du chat par jour`() {
        val c = LauncherConfig().toggleChore("2026-10-06", 0).toggleChore("2026-10-06", 1).toggleChore("2026-10-06", 0)
        assertEquals(setOf(1), c.chat.done["2026-10-06"])
    }

    @Test
    fun `widgets maison en demi largeur par defaut`() {
        val block = MaisonBlock("m", MaisonKind.Meteo)
        assertEquals(2, block.span)
        assertTrue(block.canChangeWidth)
    }
}
