package com.atelierjlg.fern.contact

import com.atelierjlg.fern.contact.data.Birthdays
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdaysTest {
    @Test
    fun jour() {
        val d = LocalDate.of(2026, 5, 14)
        assertTrue(Birthdays.isOn("--05-14", d))
        assertTrue(Birthdays.isOn("1999-05-14", d))
        assertFalse(Birthdays.isOn("1999-05-15", d))
        assertTrue(Birthdays.isOn("2000-02-29", LocalDate.of(2026, 2, 28)))
        assertFalse(Birthdays.isOn("2000-02-29", LocalDate.of(2028, 2, 28)))
        assertEquals(27, Birthdays.age("1999-05-14", d))
        assertEquals(null, Birthdays.age("--05-14", d))
    }
}
