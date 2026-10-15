package com.atelierjlg.fern.keyboard.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Les pictos propres au clavier (même style au trait que FernIcons : Lucide, grille 24 × 24). */
object KeyIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    /** Majuscule : une flèche creuse vers le haut. */
    val Shift = icon("shift", "M12 4L4 12h4v8h8v-8h4z")
    /** Majuscule verrouillée : la même, soulignée. */
    val ShiftLocked = icon("shift-lock", "M12 3L4 11h4v6h8v-6h4z", "M8 21h8")
    /** Retour à la ligne. */
    val Return = icon("corner-down-left", "M20 4v7a4 4 0 0 1-4 4H4", "M9 10l-5 5 5 5")
    /** Aller / suivant. */
    val ArrowRight = icon("arrow-right", "M5 12h14", "M12 5l7 7-7 7")
}
