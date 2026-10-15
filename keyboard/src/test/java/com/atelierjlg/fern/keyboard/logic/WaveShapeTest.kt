package com.atelierjlg.fern.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveShapeTest {
    private val wave = WaveShape(width = 1116f, density = 2.7f)
    private fun at(f: Float) = f * wave.width

    @Test
    fun vagueDouce() {
        val ys = (0..1116 step 4).map { wave.edge(it.toFloat()) }
        val amplitude = (ys.max() - ys.min()) / 2f / wave.density
        assertEquals(WaveShape.AMPLITUDE_DP, amplitude, 0.1f)
        assertTrue(ys.all { it > 0f && it < wave.height })
    }

    @Test
    fun lEcumeNaitEtMeurtDansLeBord() {
        // Aux deux bouts, écart et épaisseur nuls : l'écume se fond dans le bord.
        assertEquals(0f, wave.gapMain(at(WaveShape.MAIN_FROM)), 0.001f)
        assertEquals(0f, wave.thicknessMain(at(WaveShape.MAIN_FROM)), 0.001f)
        assertEquals(0f, wave.gapMain(at(WaveShape.MAIN_TO)), 0.001f)
        assertEquals(0f, wave.gapBranch(at(WaveShape.BRANCH_TO)), 0.001f)
        assertEquals(0f, wave.thicknessBranch(at(WaveShape.BRANCH_TO)), 0.001f)
    }

    @Test
    fun leSecondBrinPartDuPremier() {
        // Au départ du second brin, il est confondu avec le premier (pas de ligne qui « sort de nulle part »).
        val x = at(WaveShape.BRANCH_FROM)
        assertEquals(wave.gapMain(x), wave.gapBranch(x), 0.001f)
        assertEquals(0f, wave.thicknessBranch(x), 0.001f)
        assertTrue(wave.gapMain(x) > 2f * wave.density)
    }

    @Test
    fun lesBrinsSeCroisent() {
        // Le second brin passe sous le premier, puis au-dessus : ils se croisent une fois.
        val xs = (0..200).map { at(WaveShape.BRANCH_SPLIT + (WaveShape.MAIN_TO - WaveShape.BRANCH_SPLIT) * it / 200f) }
        val diffs = xs.map { wave.gapBranch(it) - wave.gapMain(it) }
        assertTrue(diffs.first() < 0f)
        assertTrue(diffs.last() > 0f)
    }

    @Test
    fun pasDeCassure() {
        // Raccords doux : d'un point au suivant, l'écart ne saute jamais.
        val step = wave.width / 2000f
        var prevMain = wave.gapMain(0f)
        var prevBranch = wave.gapBranch(0f)
        for (i in 1..2000) {
            val x = i * step
            val m = wave.gapMain(x)
            val b = wave.gapBranch(x)
            assertTrue("principal à $x", abs(m - prevMain) < 0.3f)
            assertTrue("second brin à $x", abs(b - prevBranch) < 0.3f)
            prevMain = m
            prevBranch = b
        }
    }

    @Test
    fun ecumeCoucheeAuDebutDeLAnimation() {
        for (pts in wave.foamOutlines(progress = 0f, steps = 20)) {
            // Bord bas = première moitié : posé sur la vague.
            for (i in 0 until pts.size / 2 step 2) {
                assertEquals(wave.edge(pts[i]), pts[i + 1], 0.01f)
            }
        }
    }

    @Test
    fun lEcumeResteDansLaZone() {
        for (pts in wave.foamOutlines()) {
            for (i in pts.indices step 2) {
                assertTrue(!pts[i].isNaN() && !pts[i + 1].isNaN())
                assertTrue(pts[i] in 0f..wave.width)
                assertTrue(pts[i + 1] in 0f..wave.height)
            }
        }
    }
}
