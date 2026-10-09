package com.atelierjlg.fern.contact

import com.atelierjlg.fern.contact.data.parseBirthdayInput
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BirthdayParseTest {
    private val today = LocalDate.of(2026, 10, 9)
    private fun p(s: String) = parseBirthdayInput(s, today)

    @Test
    fun formats() {
        assertEquals("2005-01-18", p("20050118"))
        assertEquals("2005-01-18", p("18012005"))
        assertEquals("2005-01-18", p("18/01/2005"))
        assertEquals("2005-01-18", p("18-1-05"))
        assertEquals("1999-05-14", p("14.05.99"))
        assertEquals("1999-05-14", p("14 05 1999"))
        assertEquals("1999-05-14", p("1999-05-14"))
        assertEquals("1999-05-14", p("140599"))
        assertEquals("--05-14", p("14/05"))
        assertEquals("--05-14", p("1405"))
        assertEquals("1999-05-14", p("14 mai 1999"))
        assertEquals("--01-01", p("1er janvier"))
        assertEquals("--08-15", p("15 août"))
        assertEquals("--02-29", p("29/02"))
        assertEquals("", p("  "))
    }

    @Test
    fun refus() {
        assertEquals(null, p("31/13"))
        assertEquals(null, p("30/02/2001"))
        assertEquals(null, p("14/05/2099"))
        assertEquals(null, p("bonjour"))
        assertEquals(null, p("123"))
    }
}
