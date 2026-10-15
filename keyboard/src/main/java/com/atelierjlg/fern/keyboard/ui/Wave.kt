package com.atelierjlg.fern.keyboard.ui

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.keyboard.logic.WaveShape
import com.atelierjlg.fern.ui.theme.Fern

/** Une courbe douce qui « se pose » : départ vif, arrivée très lente (comme une vague qui s'étale). */
private val Settle = CubicBezierEasing(0.2f, 0.7f, 0.1f, 1f)

/**
 * Le haut du clavier : une vague douce et son écume.
 *
 * L'écume est dessinée en trois couches très légères :
 * 1. un voile flou (comme une brume d'embruns),
 * 2. un fin liseré sur la crête, d'où l'écume semble naître,
 * 3. les deux brins, dessinés comme UNE seule forme (pas de surépaisseur là où ils se rejoignent),
 *    avec des bords adoucis et un fondu de transparence aux deux bouts.
 *
 * À chaque ouverture du clavier, l'écume se soulève et s'étoffe un court instant puis se fige
 * (pas d'animation continue : ça userait la batterie).
 */
@Composable
fun Wave(openings: Int, animate: Boolean, modifier: Modifier = Modifier) {
    val c = Fern.colors
    val progress = remember { Animatable(1f) }
    LaunchedEffect(openings, animate) {
        if (animate && openings > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 1300, easing = Settle))
        } else {
            progress.snapTo(1f)
        }
    }
    Canvas(modifier.fillMaxWidth().height(WaveShape.HEIGHT_DP.dp)) {
        val shape = WaveShape(size.width, density)
        val p = progress.value
        // Pendant l'ouverture, la vague glisse un peu vers la gauche avant de se poser.
        val shift = (1f - p) * -0.7f

        // ─── Le corps de la vague (même couleur que le clavier) ───
        val step = 3.dp.toPx()
        val body = Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            while (x < size.width) {
                lineTo(x, shape.edge(x, shift))
                x += step
            }
            lineTo(size.width, shape.edge(size.width, shift))
            lineTo(size.width, size.height)
            close()
        }
        drawPath(body, c.mousse)

        // ─── Le dégradé de l'écume : invisible aux bouts, plein au milieu ───
        val start = shape.foamStart
        val end = shape.foamEnd
        val fade = size.width * 0.10f
        fun foamShader(alpha: Float) = LinearGradientShader(
            from = Offset(start, 0f),
            to = Offset(end, 0f),
            colors = listOf(
                c.lichen.copy(alpha = 0f),
                c.lichen.copy(alpha = alpha),
                c.lichen.copy(alpha = alpha),
                c.lichen.copy(alpha = 0f),
            ),
            colorStops = listOf(0f, fade / (end - start), 1f - fade / (end - start), 1f),
        )

        // Les deux brins dans un seul chemin : ils se fondent l'un dans l'autre.
        val foam = Path()
        for (pts in shape.foamOutlines(progress = p, shift = shift)) {
            foam.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) {
                foam.lineTo(pts[i], pts[i + 1])
                i += 2
            }
            foam.close()
        }

        // Le liseré de crête, sur la partie écumeuse du bord.
        val crest = Path().apply {
            moveTo(start, shape.edge(start, shift))
            var x = start
            while (x < end) {
                x += step
                lineTo(x.coerceAtMost(end), shape.edge(x.coerceAtMost(end), shift))
            }
        }

        drawIntoCanvas { canvas ->
            // 1. Voile flou.
            val mist = Paint().apply { shader = foamShader(0.18f * p) }
            mist.asFrameworkPaint().maskFilter = BlurMaskFilter(3.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(foam, mist)

            // 2. Liseré de crête.
            val line = Paint().apply {
                shader = foamShader(0.32f * p)
                style = PaintingStyle.Stroke
                strokeWidth = 1.dp.toPx()
            }
            line.asFrameworkPaint().maskFilter = BlurMaskFilter(0.5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(crest, line)

            // 3. Les brins, aux bords adoucis.
            val strands = Paint().apply { shader = foamShader(0.78f * (0.3f + 0.7f * p)) }
            strands.asFrameworkPaint().maskFilter = BlurMaskFilter(0.45.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(foam, strands)
        }
    }
}
