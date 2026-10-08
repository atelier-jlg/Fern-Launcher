package com.atelierjlg.fern.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalFernColors = staticCompositionLocalOf { FernPalettes.EstampeNuit }
private val LocalFernType = staticCompositionLocalOf { FernType.Default }

/** Accès au thème depuis n'importe quel écran : `Fern.colors.creme`, `Fern.type.horloge`… */
object Fern {
    val colors: FernColors
        @Composable get() = LocalFernColors.current
    val type: FernType
        @Composable get() = LocalFernType.current
}

/**
 * Applique un thème Fern à tout ce qui est dessiné à l'intérieur.
 * (Comme une feuille de style CSS posée sur la page entière.)
 */
@Composable
fun FernTheme(
    colors: FernColors = FernPalettes.EstampeNuit,
    content: @Composable () -> Unit,
) {
    // Les composants Material (menus contextuels, dialogues…) suivent aussi nos couleurs.
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
