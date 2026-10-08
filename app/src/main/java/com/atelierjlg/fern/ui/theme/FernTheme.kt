package com.atelierjlg.fern.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.atelierjlg.fern.data.Family
import com.atelierjlg.fern.data.IconMode
import com.atelierjlg.fern.data.ThemeColors

/*
 * Ce qui est propre au lanceur dans le thème. Le reste (FernColors, FernType, FernTheme,
 * `Fern.colors`) vit dans le module commun `theme/`, partagé avec Fern Messages et Fern Contact.
 */

/** Couleur de la plaque d'icône d'une famille (celle du thème, sinon celle du guide). */
fun FernColors.plate(family: Family): Color =
    families[family.name]?.first ?: parseHexColor(family.plate)

/** Couleur du picto d'une famille. */
fun FernColors.glyph(family: Family): Color =
    families[family.name]?.second ?: parseHexColor(family.trait, Color.White)

/** Convertit un thème enregistré (« #RRGGBB ») en couleurs Compose. */
fun ThemeColors.toFernColors(): FernColors {
    fun c(hex: String, fallback: Color) = parseHexColor(hex, fallback)
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
        families = Family.entries.associate { f ->
            val fc = familyColors(f)
            f.name to (parseHexColor(fc.plate) to parseHexColor(fc.trait, Color.White))
        },
    )
}

/** Les noms de familles choisis par Jules (voir `displayName`). */
val LocalFamilyNames = staticCompositionLocalOf { emptyMap<Family, String>() }

/** Le nom affiché d'une famille : celui choisi dans Paramètres → Familles, sinon celui du guide. */
@Composable
fun Family.displayName(): String = LocalFamilyNames.current[this] ?: label

/** Le style d'icônes en cours (origine, pack ou plaques Fern). */
val LocalIconMode = staticCompositionLocalOf { IconMode.Origine }
