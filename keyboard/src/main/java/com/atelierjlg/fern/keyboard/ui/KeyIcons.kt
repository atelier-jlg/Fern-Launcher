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
    /** Ouvrir / fermer la barre d'outils. */
    val Grid = icon(
        "grid",
        "M5 3h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M16 3h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M5 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
        "M16 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
    )
    val Clipboard = icon(
        "clipboard",
        "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
        "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z",
    )
    val Smile = icon("smile", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z", "M8 14s1.5 2 4 2 4-2 4-2", "M9 9h.01", "M15 9h.01")
    val Pin = icon(
        "pin",
        "M12 17v5",
        "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z",
    )
    /** Code de vérification (un bouclier). */
    val Shield = icon("shield", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", "M9 12l2 2 4-4")
}
