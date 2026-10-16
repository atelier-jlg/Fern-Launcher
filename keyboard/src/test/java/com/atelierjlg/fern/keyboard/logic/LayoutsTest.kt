package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    @Test
    fun toutesLesRangeesOntLaMemeLargeur() {
        for (page in Page.entries) {
            for (numberRow in listOf(false, true)) {
                val rows = Layouts.rows(page, numberRow)
                val expected = if (page == Page.Numerique) 4f else 10f
                rows.forEachIndexed { i, row ->
                    assertEquals("$page rangée $i", expected, row.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
                }
            }
        }
    }

    @Test
    fun azerty() {
        val rows = Layouts.rows(Page.Lettres)
        assertEquals("azertyuiop", rows[0].joinToString("") { it.label })
        assertEquals("qsdfghjklm", rows[1].joinToString("") { it.label })
        assertEquals("wxcvbn'", rows[2].filter { it.action is KeyAction.Text }.joinToString("") { it.label })
    }

    @Test
    fun accentsALappuiLong() {
        val e = Layouts.rows(Page.Lettres)[0].first { it.label == "e" }
        assertEquals("é", e.popup.first())
        assertTrue("è" in e.popup && "ê" in e.popup)
        val c = Layouts.rows(Page.Lettres)[2].first { it.label == "c" }
        assertEquals("ç", c.popup.first())
    }

    @Test
    fun chiffresSansRangeeDeChiffres() {
        val top = Layouts.rows(Page.Lettres, numberRow = false)[0]
        assertEquals("1", top.first { it.label == "a" }.hint)
        assertEquals("0", top.first { it.label == "p" }.hint)
        // « p » n'a pas d'accent : son chiffre est la seule variante, donc celle par défaut.
        assertEquals(listOf("0"), top.first { it.label == "p" }.popup)
    }

    @Test
    fun rangeeDeChiffres() {
        val rows = Layouts.rows(Page.Lettres, numberRow = true)
        assertEquals(5, rows.size)
        assertEquals("1234567890", rows[0].joinToString("") { it.label })
        // Plus de chiffre caché sur les lettres.
        assertTrue(rows[1].all { it.hint == null })
    }

    @Test
    fun chaquePageAEffacerEtEntree() {
        for (page in Page.entries) {
            val keys = Layouts.rows(page).flatten().map { it.action }
            assertTrue("$page : effacer", KeyAction.Delete in keys)
            assertTrue("$page : entrée", KeyAction.Enter in keys)
            assertTrue("$page : espace", KeyAction.Space in keys)
        }
    }

    @Test
    fun onPeutToujoursRevenirAuxLettres() {
        for (page in Page.entries.filter { it != Page.Lettres }) {
            val goTo = Layouts.rows(page).flatten().map { it.action }.filterIsInstance<KeyAction.GoTo>()
            assertTrue("$page", goTo.any { it.page == Page.Lettres })
        }
        assertFalse(Layouts.rows(Page.Lettres).flatten().any { it.action == KeyAction.GoTo(Page.Lettres) })
    }
}
