package com.atelierjlg.fern.theme

import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.common.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test
    fun memeNumeroEcritDifferemment() {
        assertTrue(PhoneNumbers.same("+33 6 12 34 56 78", "06 12 34 56 78"))
        assertTrue(PhoneNumbers.same("0033612345678", "0612345678"))
        assertFalse(PhoneNumbers.same("0612345678", "0612345679"))
        assertTrue(PhoneNumbers.same("3631", "3631"))
        assertFalse(PhoneNumbers.same("3631", "13631"))
        assertFalse(PhoneNumbers.same("", ""))
    }

    @Test
    fun affichage() {
        assertEquals("06 12 34 56 78", PhoneNumbers.format("+33612345678"))
        assertEquals("06 12 34 56 78", PhoneNumbers.format("0612345678"))
        assertEquals("01 23 45 67 89", PhoneNumbers.format("0033123456789"))
        assertEquals("3631", PhoneNumbers.format("3631"))
        assertEquals("+44 207 946 095 8", PhoneNumbers.format("+442079460958"))
    }

    @Test
    fun morceauDeNumero() {
        assertTrue(PhoneNumbers.containsDigits("06 12 34 56 78", "1234"))
        assertTrue(PhoneNumbers.containsDigits("+33 6 12 34 56 78", "0612"))
        assertFalse(PhoneNumbers.containsDigits("06 12 34 56 78", ""))
        assertFalse(PhoneNumbers.containsDigits("06 12 34 56 78", "999"))
    }

    @Test
    fun rechercheSansAccents() {
        assertEquals("eloise", SearchText.fold("Éloïse"))
        assertTrue(SearchText.matches("Éloïse Martin", "eloi mar"))
        assertTrue(SearchText.matches("Éloïse Martin", "MARTIN"))
        assertFalse(SearchText.matches("Éloïse Martin", "paul"))
        assertEquals("E", SearchText.initial("Élodie"))
        assertEquals("#", SearchText.initial("3 Suisses"))
        assertEquals("#", SearchText.initial(""))
    }

    @Test
    fun clavierT9() {
        assertEquals("58537", SearchText.t9("Jules"))
        assertTrue(SearchText.t9Matches("Jules Martin", "585"))
        assertTrue(SearchText.t9Matches("Jules Martin", "627"))
        assertTrue(SearchText.t9Matches("Éloïse", "356"))
        assertFalse(SearchText.t9Matches("Jules", "5"))
        assertFalse(SearchText.t9Matches("Jules", "999"))
    }
}
