package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveShapeTest {
    private val wave = WaveShape(width = 1116f, density = 2.7f)

    @Test
    fun vagueDouce() {
        val ys = (0..1116 step 4).map { wave.edge(it.toFloat()) }
        val amplitude = (ys.max() - ys.min()) / 2f / wave.density
        assertEquals(WaveShape.AMPLITUDE_DP, amplitude, 0.1f)
        // Le bord reste dans la zone dessinée.
        assertTrue(ys.all { it > 0f && it < wave.height })
    }

    @Test
    fun lEcumeSeDetacheEtRevient() {
        for (foam in wave.foams) {
            val pts = wave.foamOutline(foam, steps = 40)
            val n = pts.size / 4 // points du bord bas
            // Aux deux bouts, le filet touche le bord de la vague.
            val xStart = pts[0]
            val xEnd = pts[(n - 1) * 2]
            assertEquals(wave.edge(xStart), pts[1], 0.01f)
            assertEquals(wave.edge(xEnd), pts[(n - 1) * 2 + 1], 0.01f)
            // Au milieu, il s'en écarte d'environ son écart (en dp).
            val mid = n / 2
            val gap = wave.edge(pts[mid * 2]) - pts[mid * 2 + 1]
            assertEquals(foam.gapDp * wave.density, gap, foam.gapDp * wave.density * 0.05f)
        }
    }

    @Test
    fun ecumeInvisibleAuDebutDeLAnimation() {
        val pts = wave.foamOutline(wave.foams[0], progress = 0f, steps = 20)
        for (i in pts.indices step 2) {
            assertEquals(wave.edge(pts[i]), pts[i + 1], 0.01f)
        }
    }

    @Test
    fun lEcumeResteDansLaZone() {
        for (foam in wave.foams) {
            val pts = wave.foamOutline(foam)
            for (i in pts.indices step 2) {
                assertTrue(pts[i] in 0f..wave.width)
                assertTrue(pts[i + 1] in 0f..wave.height)
            }
        }
    }
}
