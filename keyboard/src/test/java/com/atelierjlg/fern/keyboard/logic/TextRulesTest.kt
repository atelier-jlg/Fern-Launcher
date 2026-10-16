package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRulesTest {
    @Test
    fun majusculeAuto() {
        assertTrue(TextRules.shouldCapitalize(""))
        assertTrue(TextRules.shouldCapitalize("Salut. "))
        assertTrue(TextRules.shouldCapitalize("Quoi ?! "))
        assertTrue(TextRules.shouldCapitalize("Bon… "))
        assertTrue(TextRules.shouldCapitalize("Ligne 1\n"))
        assertTrue(TextRules.shouldCapitalize("Ligne 1\n  "))
        assertFalse(TextRules.shouldCapitalize("Salut "))
        assertFalse(TextRules.shouldCapitalize("je m'ennuie"))
        assertFalse(TextRules.shouldCapitalize("Fin."))
        // Une abréviation finit aussi par un point : on garde la règle simple (comme les autres claviers).
        assertTrue(TextRules.shouldCapitalize("etc. "))
    }

    @Test
    fun pointAutoAvecDeuxEspaces() {
        assertTrue(TextRules.doubleSpaceToPeriod("On se voit ", lastWasSpace = true))
        assertTrue(TextRules.doubleSpaceToPeriod("à 20h ", lastWasSpace = true))
        assertTrue(TextRules.doubleSpaceToPeriod("(demain) ", lastWasSpace = true))
        // Pas juste après une touche autre que l'espace.
        assertFalse(TextRules.doubleSpaceToPeriod("On se voit ", lastWasSpace = false))
        // Pas après une ponctuation ou un autre espace.
        assertFalse(TextRules.doubleSpaceToPeriod("Super ! ", lastWasSpace = true))
        assertFalse(TextRules.doubleSpaceToPeriod("Mot  ", lastWasSpace = true))
        assertFalse(TextRules.doubleSpaceToPeriod(" ", lastWasSpace = true))
    }

    @Test
    fun toucheMajuscule() {
        assertEquals(ShiftMode.Once, TextRules.onShiftTap(ShiftMode.Off, 5_000))
        assertEquals(ShiftMode.Locked, TextRules.onShiftTap(ShiftMode.Once, 200))
        assertEquals(ShiftMode.Off, TextRules.onShiftTap(ShiftMode.Once, 900))
        assertEquals(ShiftMode.Off, TextRules.onShiftTap(ShiftMode.Locked, 100))
        assertEquals(ShiftMode.Off, TextRules.afterLetter(ShiftMode.Once))
        assertEquals(ShiftMode.Locked, TextRules.afterLetter(ShiftMode.Locked))
    }

    @Test
    fun accentsEnMajuscule() {
        assertEquals("É", TextRules.applyShift("é", ShiftMode.Once))
        assertEquals("Ç", TextRules.applyShift("ç", ShiftMode.Locked))
        assertEquals("œ", TextRules.applyShift("œ", ShiftMode.Off))
        assertEquals("3", TextRules.applyShift("3", ShiftMode.Locked))
    }
}
