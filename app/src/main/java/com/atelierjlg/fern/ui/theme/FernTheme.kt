package com.atelierjlg.fern.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.atelierjlg.fern.data.ThemeColors

/**
 * Toutes les couleurs d'un thème Fern.
 *
 * C'est l'équivalent d'un jeu de variables CSS (`--nuit: #14241B;`).
 * Plus tard, ces valeurs viendront des Paramètres et pourront être exportées
 * dans un fichier pour être partagées avec des proches.
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
)

/** Les thèmes livrés avec l'appli. Valeurs reprises de design/design-tokens.json. */
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

/** Convertit un thème enregistré (« #RRGGBB ») en couleurs Compose. */
fun ThemeColors.toFernColors(): FernColors {
    fun c(hex: String, fallback: Color) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
    val d = FernPalettes.EstampeNuit
    return FernColors(
        nuit = c(nuit, d.nuit),
        mousse = c(mousse, d.mousse),
        lierre = c(lierre, d.lierre),
        sousBois = c(sousBois, d.sousBois),
        creme = c(creme, d.creme),
        lichen = c(lichen, d.lichen),
        moussePale = c(moussePale, d.moussePale),
        roseCarmin = c(roseCarmin, d.roseCarmin),
        carmin = c(carmin, d.carmin),
        pistache = c(pistache, d.pistache),
        vague = c(vague, d.vague),
    )
}

private val LocalFernColors = staticCompositionLocalOf { FernPalettes.EstampeNuit }
private val LocalFernType = staticCompositionLocalOf { FernType.Default }

/** Accès au thème depuis n'importe quel écran : `Fern.colors.creme`, `Fern.type.horloge`… */
object Fern {
    val colors: FernColors
        @Composable get() = LocalFernColors.current
    val type: FernType
        @Composable get() = LocalFernType.current
}

@Composable
fun FernTheme(
    colors: FernColors = FernPalettes.EstampeNuit,
    content: @Composable () -> Unit,
) {
    // Les composants Material (menus contextuels…) suivent aussi nos couleurs.
    val material = darkColorScheme(
        primary = colors.pistache,
        onPrimary = colors.nuit,
        background = colors.nuit,
        onBackground = colors.creme,
        surface = colors.mousse,
        onSurface = colors.creme,
        surfaceContainer = colors.mousse,
        onSurfaceVariant = colors.lichen,
    )
    CompositionLocalProvider(
        LocalFernColors provides colors,
        LocalFernType provides FernType.Default,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
