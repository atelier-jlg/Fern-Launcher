package com.atelierjlg.fern.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Toute la configuration de Fern, enregistrée dans un fichier JSON sur le téléphone.
 *
 * C'est l'équivalent d'un gros dictionnaire Python sauvegardé avec json.dump().
 * `@Serializable` dit à Kotlin de savoir convertir la classe en JSON et inversement.
 *
 * Règle d'or : chaque champ a une valeur par défaut. Ainsi, quand on ajoute un champ
 * dans une nouvelle version, l'ancien fichier se charge quand même.
 */

const val DOCK_SIZE = 4
const val PACK_SIZE = 4

@Serializable
data class LauncherConfig(
    val schema: Int = 1,
    /** false tant que la disposition de départ (packs de Jules) n'a pas été posée. */
    val seeded: Boolean = false,
    val spaces: List<Space> = listOf(Space()),
    val activeSpaceId: String = DEFAULT_SPACE_ID,
    /** Clés des applis cachées du tiroir et de la recherche. */
    val hiddenApps: Set<String> = emptySet(),
    /** Clé d'appli → nom choisi par Jules. */
    val renamedApps: Map<String, String> = emptyMap(),
    /** Clé d'appli → nombre de lancements (pour le tri par fréquence). */
    val launchCounts: Map<String, Int> = emptyMap(),
) {
    val activeSpace: Space
        get() = spaces.firstOrNull { it.id == activeSpaceId } ?: spaces.first()
}

/**
 * Un « Space » : un jeu complet de pages + dock. Pour l'instant il n'y en a qu'un ;
 * plusieurs Spaces (Perso, Travail…) viendront plus tard.
 */
@Serializable
data class Space(
    val id: String = DEFAULT_SPACE_ID,
    val name: String = "Perso",
    val pages: List<HomePage> = listOf(HomePage(id = "accueil", title = "Accueil", blocks = listOf(ClockBlock("horloge")))),
    /** Les applis du dock (null = emplacement vide). */
    val dock: List<String?> = List(DOCK_SIZE) { null },
)

@Serializable
data class HomePage(
    val id: String,
    val title: String,
    val blocks: List<HomeBlock> = emptyList(),
)

/** Un élément posé sur une page. `sealed` = la liste des types possibles est fermée. */
@Serializable
sealed class HomeBlock {
    abstract val id: String
}

/** Horloge + date. */
@Serializable
@SerialName("horloge")
data class ClockBlock(override val id: String) : HomeBlock()

/** Une rangée de 4 applis sans carte (les favoris de l'accueil). */
@Serializable
@SerialName("rangee")
data class AppRowBlock(
    override val id: String,
    val apps: List<String?> = List(PACK_SIZE) { null },
) : HomeBlock()

/** Un pack : une carte arrondie avec un titre et 4 applis en 2×2. */
@Serializable
@SerialName("pack")
data class PackBlock(
    override val id: String,
    val title: String,
    val apps: List<String?> = List(PACK_SIZE) { null },
) : HomeBlock()

const val DEFAULT_SPACE_ID = "perso"

/** Les réglages JSON communs (lecture tolérante, écriture lisible). */
val FernJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    classDiscriminator = "type"
}
