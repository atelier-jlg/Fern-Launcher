package com.atelierjlg.fern.theme

import android.net.Uri
import com.atelierjlg.fern.ui.theme.FernColors
import com.atelierjlg.fern.ui.theme.FernPalettes
import com.atelierjlg.fern.ui.theme.parseHexColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Le thème partagé entre Fern Launcher et ses applis sœurs.
 *
 * Fern Launcher publie son thème actif via un « fournisseur de contenu » (ContentProvider) :
 * une sorte de petite adresse web interne au téléphone, `content://com.atelierjlg.fern.theme/active`,
 * qui répond avec le thème en JSON. Seules les applis signées avec la même clé que Fern
 * ont le droit de la lire (permission « signature »).
 */
object FernThemeContract {
    /** L'appli qui publie le thème. */
    const val LAUNCHER_PACKAGE = "com.atelierjlg.fern"
    const val AUTHORITY = "com.atelierjlg.fern.theme"
    const val PERMISSION = "com.atelierjlg.fern.permission.READ_THEME"
    const val COLUMN_JSON = "json"
    val URI: Uri = Uri.parse("content://$AUTHORITY/active")
}

/*
 * Le format lu par les applis sœurs. C'est le même fichier que l'export de thème de Fern
 * (`{"fernTheme":1,"theme":{"name":…,"colors":{…}}}`) : les champs en plus sont ignorés,
 * les champs manquants prennent la valeur d'Estampe nuit. Ajouter une couleur ne casse rien.
 */

@Serializable
data class SharedColors(
    val nuit: String = "#14241B",
    val mousse: String = "#1E3427",
    val lierre: String = "#24402F",
    val sousBois: String = "#2E4A38",
    val creme: String = "#EFE5CF",
    val lichen: String = "#B5C2A5",
    val moussePale: String = "#7F9580",
    val roseCarmin: String = "#EC9AA0",
    val carmin: String = "#8E2733",
    val pistache: String = "#BFE3A3",
    val vague: String = "#98A0A8",
)

@Serializable
data class SharedNamedTheme(
    val name: String = "Estampe nuit",
    val colors: SharedColors = SharedColors(),
)

@Serializable
data class SharedThemeFile(
    val fernTheme: Int = 1,
    val theme: SharedNamedTheme = SharedNamedTheme(),
)

/** Le lecteur JSON : tolérant (champs inconnus ignorés). */
val SharedThemeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun parseSharedTheme(json: String): SharedNamedTheme? =
    runCatching { SharedThemeJson.decodeFromString(SharedThemeFile.serializer(), json).theme }.getOrNull()

/** Convertit les couleurs écrites « #RRGGBB » en couleurs Compose. */
fun SharedColors.toFernColors(): FernColors {
    val d = FernPalettes.EstampeNuit
    return FernColors(
        nuit = parseHexColor(nuit, d.nuit),
        mousse = parseHexColor(mousse, d.mousse),
        lierre = parseHexColor(lierre, d.lierre),
        sousBois = parseHexColor(sousBois, d.sousBois),
        creme = parseHexColor(creme, d.creme),
        lichen = parseHexColor(lichen, d.lichen),
        moussePale = parseHexColor(moussePale, d.moussePale),
        roseCarmin = parseHexColor(roseCarmin, d.roseCarmin),
        carmin = parseHexColor(carmin, d.carmin),
        pistache = parseHexColor(pistache, d.pistache),
        vague = parseHexColor(vague, d.vague),
    )
}
