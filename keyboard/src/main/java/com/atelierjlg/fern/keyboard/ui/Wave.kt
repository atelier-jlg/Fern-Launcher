package com.atelierjlg.fern.keyboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.keyboard.logic.WaveShape
import com.atelierjlg.fern.ui.theme.Fern

/**
 * Le haut du clavier : une vague douce, et deux filets d'écume qui s'en détachent puis y reviennent.
 * À chaque ouverture du clavier, l'écume « ondule » un court instant puis se fige (pas d'animation
 * continue : ça userait la batterie).
 */
@Composable
fun Wave(openings: Int, animate: Boolean, modifier: Modifier = Modifier) {
    val c = Fern.colors
    val progress = remember { Animatable(1f) }
    LaunchedEffect(openings, animate) {
        if (animate && openings > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    Canvas(modifier.fillMaxWidth().height(WaveShape.HEIGHT_DP.dp)) {
        val shape = WaveShape(size.width, density)
        val p = progress.value
        // Pendant l'animation, la vague glisse un peu vers la gauche avant de se poser.
        val shift = (1f - p) * -0.9f

        // Le corps de la vague (même couleur que le clavier).
        val body = Path().apply {
            moveTo(0f, size.height)
            val step = 3.dp.toPx()
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

        // Les filets d'écume.
        for (foam in shape.foams) {
            val pts = shape.foamOutline(foam, progress = p, shift = shift)
            val path = Path().apply {
                moveTo(pts[0], pts[1])
                var i = 2
                while (i < pts.size) {
                    lineTo(pts[i], pts[i + 1])
                    i += 2
                }
                close()
            }
            drawPath(path, c.lichen.copy(alpha = foam.alpha))
        }
    }
}
