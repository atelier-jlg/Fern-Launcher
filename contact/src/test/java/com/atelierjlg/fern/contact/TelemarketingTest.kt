package com.atelierjlg.fern.contact

import com.atelierjlg.fern.contact.data.Telemarketing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemarketingTest {
    @Test
    fun prefixesArcep() {
        assertTrue(Telemarketing.matches("01 62 12 34 56"))
        assertTrue(Telemarketing.matches("+33 9 48 00 00 00"))
        assertTrue(Telemarketing.matches("0033377123456"))
        assertFalse(Telemarketing.matches("01 61 12 34 56"))
        assertFalse(Telemarketing.matches("06 12 34 56 78"))
        assertFalse(Telemarketing.matches("3631"))
    }
}
