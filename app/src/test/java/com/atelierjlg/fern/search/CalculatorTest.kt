package com.atelierjlg.fern.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {

    private fun calc(s: String) = Calculator.evaluate(s)?.let { Calculator.format(it) }

    @Test
    fun basics() {
        assertEquals("16", calc("12+4"))
        assertEquals("40", calc("12*3+4"))
        assertEquals("1,75", calc("(2,5+1)/2"))
        assertEquals("1024", calc("2^10"))
        assertEquals("-3", calc("-5+2"))
        assertEquals("15", calc("150*10%"))
        assertEquals("6", calc("2×3"))
    }

    @Test
    fun notACalculation() {
        assertNull(calc("whatsapp"))
        assertNull(calc("42"))
        assertNull(calc("1/0"))
        assertNull(calc("(1+2"))
        assertNull(calc("+"))
    }
}
