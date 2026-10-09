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

    @Test
    fun racinesPiEtMultiplicationImplicite() {
        assertEquals("799,63961031", calc("32*23+45√2"))
        assertEquals("5", calc("√(9+16)"))
        assertEquals("13", calc("√16+9"))
        assertEquals("6,28318531", calc("2π"))
        assertEquals("21", calc("3(1+2)+(1+2)(3+1)"))
        assertEquals("9", calc("3²"))
        assertEquals("1,41421356", calc("sqrt 2"))
        assertEquals("1,41421356", calc("√2"))
        assertNull(calc("√-4"))
    }
}
