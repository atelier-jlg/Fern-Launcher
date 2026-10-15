package com.atelierjlg.fern.keyboard.logic

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * La vague du haut du clavier et son écume, calculées en pixels.
 * Séparé du dessin (Wave.kt) pour pouvoir le tester : c'est juste de la trigonométrie.
 *
 * - Le bord : une sinusoïde douce (4 dp d'amplitude, deux ondulations sur la largeur).
 * - L'écume : un seul ruban qui naît du bord, se divise en deux brins qui se croisent, puis
 *   revient se fondre dans le bord. Tous les raccords sont « tangents » (les brins se rejoignent en
 *   douceur, sans angle ni coupure), et les deux brins sont dessinés comme une seule forme (pas de
 *   surépaisseur là où ils se superposent).
 */
class WaveShape(val width: Float, val density: Float) {
    /** Hauteur totale de la zone de la vague. */
    val height = HEIGHT_DP * density
    private val base = BASE_DP * density
    private val amplitude = AMPLITUDE_DP * density
    private val wavelength = width / 2f

    /** Hauteur du bord à l'abscisse x. `shift` fait glisser la vague (animation d'ouverture). */
    fun edge(x: Float, shift: Float = 0f): Float =
        base + amplitude * sin(2f * PI.toFloat() * x / wavelength + PHASE + shift)

    /** Écart (en px, vers le haut) du brin principal par rapport au bord. */
    fun gapMain(x: Float): Float = MAIN_GAP_DP * density * hann(frac(x), MAIN_FROM, MAIN_TO)

    /**
     * Écart du second brin : il part DU brin principal (au début ils ne font qu'un), s'en écarte,
     * le croise, puis finit seul en se fondant dans le bord.
     */
    fun gapBranch(x: Float): Float {
        val f = frac(x)
        val own = BRANCH_GAP_DP * density * hann(f, BRANCH_OWN_FROM, BRANCH_TO)
        val w = smoothstep(f, BRANCH_FROM, BRANCH_SPLIT)
        return gapMain(x) * (1f - w) + own * w
    }

    /** Épaisseur des brins : fine aux bouts, un peu plus épaisse au milieu. */
    fun thicknessMain(x: Float): Float = MAIN_THICK_DP * density * hann(frac(x), MAIN_FROM, MAIN_TO).pow(0.35f)

    fun thicknessBranch(x: Float): Float {
        val f = frac(x)
        // Le brin « sort » du principal : épaisseur nulle tant qu'ils sont confondus.
        val emerge = smoothstep(f, BRANCH_FROM, BRANCH_SPLIT).pow(0.7f)
        val ends = hann(f, BRANCH_FROM, BRANCH_TO).pow(0.35f)
        return BRANCH_THICK_DP * density * emerge * ends
    }

    /** Début et fin de l'écume (en px), pour le dégradé de transparence. */
    val foamStart get() = MAIN_FROM * width
    val foamEnd get() = BRANCH_TO * width

    /**
     * Les contours des deux brins, prêts à dessiner ensemble (une seule forme).
     * Chaque tableau : [x0, y0, x1, y1, …] — bord bas de gauche à droite, puis bord haut au retour.
     * @param progress 0 = écume couchée sur le bord (invisible), 1 = écume complète.
     */
    fun foamOutlines(progress: Float = 1f, shift: Float = 0f, steps: Int = 64): List<FloatArray> = listOf(
        outline(MAIN_FROM, MAIN_TO, progress, shift, steps, ::gapMain, ::thicknessMain),
        outline(BRANCH_FROM, BRANCH_TO, progress, shift, steps, ::gapBranch, ::thicknessBranch),
    )

    private fun outline(
        from: Float, to: Float, progress: Float, shift: Float, steps: Int,
        gap: (Float) -> Float, thickness: (Float) -> Float,
    ): FloatArray {
        val n = steps + 1
        val out = FloatArray(n * 4)
        // Pendant l'ouverture, l'écume se soulève (écart) un peu avant de s'étoffer (épaisseur).
        val lift = progress
        val body = 0.35f + 0.65f * progress
        for (i in 0 until n) {
            val x = (from + (to - from) * i / steps) * width
            val y = edge(x, shift) - gap(x) * lift
            val t = thickness(x) * body
            // Bord bas, de gauche à droite…
            out[i * 2] = x
            out[i * 2 + 1] = y
            // …et bord haut, de droite à gauche (rangé à l'envers).
            val j = n * 2 + (n - 1 - i) * 2
            out[j] = x
            out[j + 1] = y - t
        }
        return out
    }

    private fun frac(x: Float) = x / width

    companion object {
        const val HEIGHT_DP = 34f
        const val BASE_DP = 24f
        const val AMPLITUDE_DP = 4f
        const val PHASE = 0.6f

        // Brin principal
        const val MAIN_FROM = 0.04f
        const val MAIN_TO = 0.66f
        const val MAIN_GAP_DP = 6.5f
        const val MAIN_THICK_DP = 1.7f

        // Second brin : confondu avec le principal à partir de BRANCH_FROM, séparé à BRANCH_SPLIT,
        // sa propre courbe commence à BRANCH_OWN_FROM et se fond dans le bord à BRANCH_TO.
        const val BRANCH_FROM = 0.26f
        const val BRANCH_SPLIT = 0.46f
        const val BRANCH_OWN_FROM = 0.34f
        const val BRANCH_TO = 0.96f
        const val BRANCH_GAP_DP = 4.8f
        const val BRANCH_THICK_DP = 1.2f

        /** Bosse « en cloche » nulle et plate aux deux bouts (raccords sans angle). 0 hors de [a, b]. */
        fun hann(f: Float, a: Float, b: Float): Float {
            if (f <= a || f >= b) return 0f
            val s = sin(PI.toFloat() * (f - a) / (b - a))
            return s * s
        }

        /** Passage doux de 0 (avant a) à 1 (après b). */
        fun smoothstep(f: Float, a: Float, b: Float): Float {
            val t = ((f - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
