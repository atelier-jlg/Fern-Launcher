package com.atelierjlg.fern.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.atelierjlg.fern.R

/**
 * Bricolage Grotesque est une police « variable » : un seul fichier, dont on règle
 * la graisse (weight) et la largeur (width, de 75 % à 100 %).
 */
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, width: Float = 100f) = FontFamily(
    Font(
        resId = R.font.bricolage_grotesque,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.width(width),
        ),
    ),
)

/** Les styles de texte de la charte (tailles reprises de design-tokens.json). */
@Immutable
data class FernType(
    val horloge: TextStyle,
    val titrePage: TextStyle,
    val titreWidget: TextStyle,
    val corps: TextStyle,
    /** À écrire en capitales : `"texte".uppercase()`. */
    val libelle: TextStyle,
    val nomApp: TextStyle,
) {
    companion object {
        private val etroitExtraBold = bricolage(weight = 800, width = 75f)

        val Default = FernType(
            horloge = TextStyle(fontFamily = etroitExtraBold, fontSize = 92.sp, lineHeight = 92.sp),
            titrePage = TextStyle(fontFamily = etroitExtraBold, fontSize = 58.sp, lineHeight = 60.sp),
            titreWidget = TextStyle(fontFamily = etroitExtraBold, fontSize = 32.sp, lineHeight = 34.sp),
            corps = TextStyle(fontFamily = bricolage(600), fontSize = 16.sp, lineHeight = 22.sp),
            libelle = TextStyle(fontFamily = bricolage(800), fontSize = 12.sp, letterSpacing = 1.6.sp),
            nomApp = TextStyle(fontFamily = bricolage(600), fontSize = 12.sp, lineHeight = 15.sp),
        )
    }
}
