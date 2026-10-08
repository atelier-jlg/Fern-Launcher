package com.atelierjlg.fern.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Toutes les couleurs d'un thème Fern, partagées par les trois applis
 * (Fern Launcher, Fern Messages, Fern Contact).
 *
 * C'est l'équivalent d'un jeu de variables CSS (`--nuit: #14241B;`).
 * Les noms affichés à Jules sont « universels » (Fond, Cartes, Texte…), voir ThemeData.
 */
@Immutable
data class FernColors(
    // Fonds
    val nuit: Color,
    val mousse: Color,
    val lierre: Color,
    val sousBois: Color,
    // Textes
    val creme: Color,
    val lichen: Color,
    val moussePale: Color,
    // Accents
    val roseCarmin: Color,
    val carmin: Color,
    val pistache: Color,
    // Trait de la vague du bandeau
    val vague: Color,
    /**
     * Couleurs des plaques d'icônes par famille (nom de la famille → plaque, picto).
     * Seul le lanceur s'en sert ; les autres applis peuvent l'ignorer.
     */
    val families: Map<String, Pair<Color, Color>> = emptyMap(),
)

/**
 * Lit une couleur « #RRGGBB » (ou « #AARRGGBB »), comme en CSS.
 * Écrit à la main (sans android.graphics.Color) pour pouvoir être testé hors du téléphone.
 */
fun parseHexColor(hex: String, fallback: Color = Color.Gray): Color {
    val digits = hex.trim().removePrefix("#")
    val value = digits.toLongOrNull(16) ?: return fallback
    return when (digits.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> fallback
    }
}

/** Les thèmes livrés avec les applis. Valeurs reprises de design/design-tokens.json. */
object FernPalettes {
    val EstampeNuit = FernColors(
        nuit = Color(0xFF14241B),
        mousse = Color(0xFF1E3427),
        lierre = Color(0xFF24402F),
        sousBois = Color(0xFF2E4A38),
        creme = Color(0xFFEFE5CF),
        lichen = Color(0xFFB5C2A5),
        moussePale = Color(0xFF7F9580),
        roseCarmin = Color(0xFFEC9AA0),
        carmin = Color(0xFF8E2733),
        pistache = Color(0xFFBFE3A3),
        vague = Color(0xFF98A0A8),
    )
}
