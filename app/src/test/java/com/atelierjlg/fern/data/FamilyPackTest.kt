package com.atelierjlg.fern.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Le pack « famille » : les 4 applis les plus lancées, sinon l'ordre alphabétique. */
class FamilyPackTest {

    @Test
    fun `les plus lancees d'abord, puis l'ordre de depart`() {
        val keys = listOf("a", "b", "c", "d", "e", "f")
        val counts = mapOf("e" to 10, "c" to 3, "f" to 3)
        assertEquals(listOf("e", "c", "f", "a"), topByLaunches(keys, counts))
    }

    @Test
    fun `moins de 4 applis`() {
        assertEquals(listOf("x", "y"), topByLaunches(listOf("x", "y"), emptyMap()))
    }

    @Test
    fun `pack famille en demi-largeur`() {
        assertEquals(2, FamilyBlock("f", Family.Social).span)
    }
}
