package com.atelierjlg.fern.theme

import androidx.compose.ui.graphics.Color
import com.atelierjlg.fern.ui.theme.FernPalettes
import com.atelierjlg.fern.ui.theme.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedThemeTest {
    @Test
    fun couleursHex() {
        assertEquals(Color(0xFF14241B), parseHexColor("#14241B"))
        assertEquals(Color(0xFF14241B), parseHexColor("14241b"))
        assertEquals(Color(0x8014241B), parseHexColor("#8014241B"))
        assertEquals(Color.Red, parseHexColor("#XYZ", Color.Red))
        assertEquals(Color.Red, parseHexColor("#1234", Color.Red))
    }

    @Test
    fun lectureDuFichierDeTheme() {
        val json = """{"fernTheme":1,"theme":{"name":"Lilas","colors":{"nuit":"#1E181F","creme":"#F3EFF6",
            "families":{"Social":{"plate":"#BFB2DB","trait":"#F5F2F8"}},"nouvelleCouleur":"#000000"}}}"""
        val theme = parseSharedTheme(json)!!
        assertEquals("Lilas", theme.name)
        val colors = theme.colors.toFernColors()
        assertEquals(Color(0xFF1E181F), colors.nuit)
        assertEquals(Color(0xFFF3EFF6), colors.creme)
        // Couleur absente : celle d'Estampe nuit.
        assertEquals(FernPalettes.EstampeNuit.pistache, colors.pistache)
    }

    @Test
    fun texteIllisible() {
        assertNull(parseSharedTheme("pas du json"))
        assertEquals("Estampe nuit", parseSharedTheme("{}")!!.name)
    }
}
