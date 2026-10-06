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
    val drawer: DrawerSettings = DrawerSettings(),
    val search: SearchSettings = SearchSettings(),
    val gestures: GestureSettings = GestureSettings(),
    /** Le thème actif. */
    val theme: NamedTheme = ThemePresets.EstampeNuit,
    /** Les thèmes enregistrés par Jules (ou importés). */
    val savedThemes: List<NamedTheme> = emptyList(),
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

@Serializable
enum class DrawerStyle { Grille, Liste }

@Serializable
enum class DrawerSort { Alphabetique, Frequence }

/** Réglages du tiroir. */
@Serializable
data class DrawerSettings(
    val style: DrawerStyle = DrawerStyle.Grille,
    val sort: DrawerSort = DrawerSort.Alphabetique,
    /** Nombre de colonnes en mode grille (4 ou 5). */
    val columns: Int = 4,
    /** Ouvrir directement l'appli quand la recherche ne donne qu'un résultat. */
    val autoLaunchSingleResult: Boolean = false,
)

/** Réglages de la recherche (glisser vers le bas). */
@Serializable
data class SearchSettings(
    /** Adresse de recherche web ; %s est remplacé par le texte cherché. */
    val webSearchUrl: String = "https://duckduckgo.com/?q=%s",
    /** Navigateur à utiliser (null = Firefox s'il est installé, sinon le navigateur par défaut). */
    val browserPackage: String? = null,
    /** Chercher aussi dans les contacts, l'agenda, les raccourcis, et faire les calculs. */
    val extended: Boolean = false,
)

/** Ce qu'un geste peut déclencher. */
@Serializable
enum class GestureAction(val label: String) {
    Rien("Rien"),
    Tiroir("Ouvrir le tiroir"),
    Recherche("Ouvrir la recherche"),
    Notifications("Ouvrir les notifications"),
    ReglagesRapides("Ouvrir les réglages rapides"),
    Verrouiller("Verrouiller l'écran"),
    Edition("Mode édition"),
    RoueRadiale("Roue d'applis"),
    Appli("Ouvrir une appli"),
}

/** Un geste → une action (et l'appli si l'action est « Ouvrir une appli »). */
@Serializable
data class GestureBinding(val action: GestureAction, val appKey: String? = null)

const val RADIAL_SIZE = 8

/** Réglages des gestes de l'accueil. */
@Serializable
data class GestureSettings(
    val swipeUp: GestureBinding = GestureBinding(GestureAction.Tiroir),
    val swipeDown: GestureBinding = GestureBinding(GestureAction.Recherche),
    val doubleTap: GestureBinding = GestureBinding(GestureAction.Verrouiller),
    val longPress: GestureBinding = GestureBinding(GestureAction.Edition),
    /** Les applis de la roue (jusqu'à 8). */
    val radialApps: List<String?> = List(RADIAL_SIZE) { null },
)

const val DEFAULT_SPACE_ID = "perso"

/** Les réglages JSON communs (lecture tolérante, écriture lisible). */
val FernJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    classDiscriminator = "type"
}
