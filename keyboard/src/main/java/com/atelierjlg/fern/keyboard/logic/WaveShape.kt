package com.atelierjlg.fern.keyboard.logic

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * La vague du haut du clavier et ses filets d'écume, calculés en pixels.
 * Séparé du dessin (Wave.kt) pour pouvoir le tester : c'est juste de la trigonométrie.
 *
 * - Le bord : une sinusoïde douce (4 dp d'amplitude, deux ondulations sur la largeur).
 * - L'écume : de fins filets qui se détachent du bord, s'en écartent, puis y reviennent
 *   en s'affinant (épaisseur nulle aux deux bouts).
 */
class WaveShape(val width: Float, val density: Float) {
    /** Hauteur totale de la zone de la vague. */
    val height = HEIGHT_DP * density
    private val base = BASE_DP * density
    private val amplitude = AMPLITUDE_DP * density
    private val wavelength = width / 2f

    /** Un filet d'écume (positions en fraction de la largeur, tailles en dp). */
    data class Foam(val from: Float, val to: Float, val gapDp: Float, val thicknessDp: Float, val alpha: Float)

    /** Les deux filets du look B (« deux filets qui se chevauchent, le second plus pâle »). */
    val foams = listOf(
        Foam(from = 0.034f, to = 0.476f, gapDp = 6.5f, thicknessDp = 1.8f, alpha = 0.8f),
        Foam(from = 0.364f, to = 0.966f, gapDp = 4.5f, thicknessDp = 1.4f, alpha = 0.55f),
    )

    /** Hauteur du bord à l'abscisse x. `shift` fait glisser la vague (animation d'ouverture). */
    fun edge(x: Float, shift: Float = 0f): Float =
        base + amplitude * sin(2f * PI.toFloat() * x / wavelength + PHASE + shift)

    /**
     * Le contour d'un filet, prêt à dessiner : d'abord le bord bas de gauche à droite,
     * puis le bord haut de droite à gauche. Renvoie [x0, y0, x1, y1, …].
     * @param progress 0 = filet collé au bord (invisible), 1 = filet complet.
     */
    fun foamOutline(foam: Foam, progress: Float = 1f, shift: Float = 0f, steps: Int = 48): FloatArray {
        val x0 = foam.from * width
        val x1 = foam.to * width
        val bottom = FloatArray((steps + 1) * 2)
        val top = FloatArray((steps + 1) * 2)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = x0 + (x1 - x0) * t
            // sin(π) vaut « presque 0 » mais parfois négatif : une puissance de négatif donnerait NaN.
            val bump = sin(PI.toFloat() * t).coerceAtLeast(0f)
            val gap = foam.gapDp * density * progress * bump.pow(1.2f)
            val thick = foam.thicknessDp * density * progress * bump.pow(0.8f)
            val y = edge(x, shift) - gap
            bottom[i * 2] = x; bottom[i * 2 + 1] = y
            top[i * 2] = x; top[i * 2 + 1] = y - thick
        }
        val out = FloatArray(bottom.size + top.size)
        bottom.copyInto(out)
        // Le bord haut à l'envers, pour fermer la forme.
        for (i in 0..steps) {
            val j = steps - i
            out[bottom.size + i * 2] = top[j * 2]
            out[bottom.size + i * 2 + 1] = top[j * 2 + 1]
        }
        return out
    }

    companion object {
        const val HEIGHT_DP = 34f
        const val BASE_DP = 24f
        const val AMPLITUDE_DP = 4f
        const val PHASE = 0.6f
    }
}
