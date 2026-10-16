package com.atelierjlg.fern.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyNamesTest {
    @Test
    fun `renommer puis revenir au nom d'origine`() {
        val c = LauncherConfig().renameFamily(Family.Loisirs, "  Détente ")
        assertEquals("Détente", c.familyNames[Family.Loisirs])
        assertTrue(c.renameFamily(Family.Loisirs, "").familyNames.isEmpty())
        assertTrue(c.renameFamily(Family.Loisirs, Family.Loisirs.label).familyNames.isEmpty())
    }
}
