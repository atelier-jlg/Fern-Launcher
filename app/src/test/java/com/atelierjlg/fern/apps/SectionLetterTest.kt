package com.atelierjlg.fern.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionLetterTest {

    @Test
    fun accentsAndDigits() {
        assertEquals("E", sectionLetter("Écran"))
        assertEquals("M", sectionLetter("météo"))
        assertEquals("#", sectionLetter("2048"))
        assertEquals("#", sectionLetter(""))
    }

    @Test
    fun searchRanking() {
        assertEquals(0, searchRank("meteo-france", "met"))
        assertEquals(1, searchRank("sncf connect", "con"))
        assertEquals(2, searchRank("whatsapp", "sap"))
        assertNull(searchRank("signal", "xyz"))
    }
}
