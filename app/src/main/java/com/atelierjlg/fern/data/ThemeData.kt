package com.atelierjlg.fern.data

import kotlinx.serialization.Serializable

/*
 * Les thèmes, sous une forme qui s'enregistre en JSON et se partage en fichier.
 * Les couleurs sont écrites en texte « #RRGGBB », comme en CSS.
 */

@Serializable
data class ThemeColors(
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
    /** Couleurs des familles modifiées dans ce thème (les autres gardent celles du guide). */
    val families: Map<Family, FamilyColors> = emptyMap(),
) {
    fun familyColors(family: Family): FamilyColors = families[family] ?: FamilyColors(family.plate, family.trait)

    /** Les couleurs sous forme de liste (nom affiché, clé, valeur), pour l'éditeur. */
    fun entries(): List<Triple<String, String, String>> = listOf(
        Triple("Fond (nuit)", "nuit", nuit),
        Triple("Cartes (mousse)", "mousse", mousse),
        Triple("Pilules (lierre)", "lierre", lierre),
        Triple("Séparateurs (sous-bois)", "sousBois", sousBois),
        Triple("Texte (crème)", "creme", creme),
        Triple("Texte secondaire (lichen)", "lichen", lichen),
        Triple("Texte discret (mousse pâle)", "moussePale", moussePale),
        Triple("Accent (rose carmin)", "roseCarmin", roseCarmin),
        Triple("Accent foncé (carmin)", "carmin", carmin),
        Triple("Accent clair (pistache)", "pistache", pistache),
    ) + Family.entries.flatMap { f ->
        val c = familyColors(f)
        listOf(
            Triple("Plaque · ${f.label}", "plaque:${f.name}", c.plate),
            Triple("Picto · ${f.label}", "trait:${f.name}", c.trait),
        )
    }

    fun with(key: String, hex: String): ThemeColors = when (key) {
        "nuit" -> copy(nuit = hex)
        "mousse" -> copy(mousse = hex)
        "lierre" -> copy(lierre = hex)
        "sousBois" -> copy(sousBois = hex)
        "creme" -> copy(creme = hex)
        "lichen" -> copy(lichen = hex)
        "moussePale" -> copy(moussePale = hex)
        "roseCarmin" -> copy(roseCarmin = hex)
        "carmin" -> copy(carmin = hex)
        "pistache" -> copy(pistache = hex)
        "vague" -> copy(vague = hex)
        else -> withFamily(key, hex)
    }

    private fun withFamily(key: String, hex: String): ThemeColors {
        val family = Family.entries.firstOrNull { it.name == key.substringAfter(':') } ?: return this
        val current = familyColors(family)
        val updated = when (key.substringBefore(':')) {
            "plaque" -> current.copy(plate = hex)
            "trait" -> current.copy(trait = hex)
            else -> return this
        }
        return copy(families = families + (family to updated))
    }
}

/** Un thème avec un nom. C'est ce qu'on exporte pour l'envoyer à un proche. */
@Serializable
data class NamedTheme(
    val name: String,
    val colors: ThemeColors,
)

/** Le format du fichier de thème partagé (`.json`). */
@Serializable
data class ThemeFile(
    val fernTheme: Int = 1,
    val theme: NamedTheme,
)

/** Les thèmes livrés avec Fern. */
object ThemePresets {
    val EstampeNuit = NamedTheme("Estampe nuit", ThemeColors())

    /** La proposition « recalée sur le fond pixel art » (vert-bleu), à tester. */
    val NuitTeal = NamedTheme(
        "Nuit teal",
        ThemeColors(
            nuit = "#0E1A1A",
            mousse = "#12201F",
            lierre = "#1B302D",
            sousBois = "#2C4441",
            lichen = "#B2C4BC",
            moussePale = "#7A918C",
            vague = "#B5C2A5",
        ),
    )

    val SousBois = NamedTheme(
        "Sous-bois",
        ThemeColors(
            nuit = "#1A1D17",
            mousse = "#262B21",
            lierre = "#30372A",
            sousBois = "#3B4433",
            creme = "#F2E8D5",
            lichen = "#C3C7AE",
            moussePale = "#8B917A",
            roseCarmin = "#E8A3A0",
            carmin = "#9A3A3A",
            pistache = "#C9E3A8",
        ),
    )

    val all = listOf(EstampeNuit, NuitTeal, SousBois)
}

/** Vérifie qu'un texte est une couleur « #RRGGBB » valide. */
fun isValidHex(hex: String): Boolean = Regex("^#[0-9A-Fa-f]{6}$").matches(hex)
