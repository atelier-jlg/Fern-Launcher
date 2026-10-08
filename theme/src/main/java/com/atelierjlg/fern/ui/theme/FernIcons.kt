package com.atelierjlg.fern.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Les pictos des applis Fern, au trait (style Lucide / Feather, licence ISC) :
 * dessinés dans une grille 24 × 24, trait de 2, bouts arrondis. Inclus dans l'appli,
 * donc rien à télécharger. Comme un `<svg>` en HTML : chaque chaîne est un attribut `d`.
 */
object FernIcons {
    private fun icon(name: String, vararg paths: String, width: Float = 2f): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    private const val PHONE =
        "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"

    val Phone = icon("phone", PHONE)
    val PhoneIncoming = icon("phone-incoming", PHONE, "M16 2v6h6", "M23 1l-7 7")
    val PhoneOutgoing = icon("phone-outgoing", PHONE, "M23 7V1h-6", "M16 8l7-7")
    val PhoneMissed = icon("phone-missed", PHONE, "M23 1l-6 6", "M17 1l6 6")
    val PhoneOff = icon(
        "phone-off",
        "M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.42 19.42 0 0 1-3.33-2.67m-2.67-3.34a19.79 19.79 0 0 1-3.07-8.63A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91",
        "M23 1L1 23",
    )
    val Message = icon("message", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z")
    val Mic = icon("mic", "M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v4", "M8 23h8")
    val MicOff = icon(
        "mic-off", "M1 1l22 22", "M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6",
        "M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23", "M12 19v4", "M8 23h8",
    )
    val Speaker = icon("volume", "M11 5L6 9H2v6h4l5 4V5z", "M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07")
    val Bluetooth = icon("bluetooth", "M6.5 6.5l11 11L12 23V1l5.5 5.5-11 11")
    val Headphones = icon(
        "headphones", "M3 18v-6a9 9 0 0 1 18 0v6",
        "M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z",
    )
    val Earpiece = icon("smartphone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M12 18h.01")
    val Pause = icon("pause", "M6 4h4v16H6z", "M14 4h4v16h-4z")
    val Dialpad = icon(
        "dialpad",
        "M6 4h.01M12 4h.01M18 4h.01M6 10h.01M12 10h.01M18 10h.01M6 16h.01M12 16h.01M18 16h.01M12 22h.01",
        width = 3.2f,
    )
    val Backspace = icon("delete", "M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z", "M18 9l-6 6", "M12 9l6 6")
    val Star = icon("star", "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z")
    val Edit = icon("edit", "M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z")
    val Trash = icon(
        "trash", "M3 6h18",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M10 11v6", "M14 11v6",
    )
    val Search = icon("search", "M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0z", "M21 21l-4.35-4.35")
    val Back = icon("arrow-left", "M19 12H5", "M12 19l-7-7 7-7")
    val User = icon("user", "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0z")
    val UserPlus = icon(
        "user-plus", "M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2",
        "M12.5 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0z", "M20 8v6", "M23 11h-6",
    )
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val Close = icon("x", "M18 6L6 18", "M6 6l12 12")
    val Check = icon("check", "M20 6L9 17l-5-5")
    val Swap = icon("repeat", "M17 1l4 4-4 4", "M3 11V9a4 4 0 0 1 4-4h14", "M7 23l-4-4 4-4", "M21 13v2a4 4 0 0 1-4 4H3")
    val Merge = icon("merge", "M21 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M9 6a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M6 21V9a9 9 0 0 0 9 9")
    val Block = icon("slash", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z", "M4.93 4.93l14.14 14.14")
    val Camera = icon(
        "camera", "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z",
        "M16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0z",
    )
    val Mail = icon("mail", "M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z", "M22 6l-10 7L2 6")
    val Gift = icon(
        "gift", "M20 12v10H4V12", "M2 7h20v5H2z", "M12 22V7",
        "M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z", "M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z",
    )
    val Note = icon("file-text", "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6", "M16 13H8", "M16 17H8")
    val Briefcase = icon(
        "briefcase", "M20 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z",
        "M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16",
    )
    val Clock = icon("clock", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z", "M12 6v6l4 2")
    val Settings = icon(
        "sliders", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3", "M1 14h6M9 8h6M17 16h6",
    )
    val Voicemail = icon(
        "voicemail", "M10 11.5a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0z", "M23 11.5a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0z",
        "M5.5 16h13",
    )
    val Copy = icon(
        "copy", "M20 9h-9a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2z",
        "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1",
    )
}

/** Un picto Fern, de la couleur demandée. */
@Composable
fun FernIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}
