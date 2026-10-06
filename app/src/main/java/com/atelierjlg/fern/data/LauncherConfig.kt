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
    /** Bascule automatique de Space selon l'heure. */
    val spaceSchedule: SpaceSchedule = SpaceSchedule(),
    val focus: FocusSettings = FocusSettings(),
    val place: PlaceSettings = PlaceSettings(),
    val icons: IconSettings = IconSettings(),
    /** Familles choisies à la main (clé d'appli → famille) ; sinon classement automatique. */
    val appFamilies: Map<String, Family> = emptyMap(),
) {
    val activeSpace: Space
        get() = spaces.firstOrNull { it.id == activeSpaceId } ?: spaces.first()

    /** Le thème réellement affiché : celui du Space s'il en a un, sinon le thème général. */
    val effectiveTheme: NamedTheme
        get() = activeSpace.theme ?: theme
}

/** Une règle de planning : ce Space, ces jours-là, de telle heure à telle heure. */
@Serializable
data class SpaceRule(
    val id: String,
    val spaceId: String,
    /** Jours de la semaine, 1 = lundi … 7 = dimanche. */
    val days: Set<Int> = (1..7).toSet(),
    /** Minutes depuis minuit (8 h 30 = 510). Si fin < début, la règle passe minuit. */
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 18 * 60,
)

@Serializable
data class SpaceSchedule(
    val enabled: Boolean = false,
    val rules: List<SpaceRule> = emptyList(),
)

/** Mode Focus : certaines applis disparaissent tant qu'il est actif. */
@Serializable
data class FocusSettings(
    val enabled: Boolean = false,
    val blockedApps: Set<String> = emptySet(),
)

/**
 * Un « Space » : un jeu complet de pages + dock (+ éventuellement son thème).
 * Exemple : « Perso » et « Travail », avec bascule manuelle ou à heures fixes.
 */
@Serializable
data class Space(
    val id: String = DEFAULT_SPACE_ID,
    val name: String = "Perso",
    val pages: List<HomePage> = listOf(HomePage(id = "accueil", title = "Accueil", blocks = listOf(ClockBlock("horloge")))),
    /** Les applis du dock (null = emplacement vide). */
    val dock: List<String?> = List(DOCK_SIZE) { null },
    /** Un thème propre à ce Space (null = le thème général). */
    val theme: NamedTheme? = null,
)

@Serializable
data class HomePage(
    val id: String,
    val title: String,
    val blocks: List<HomeBlock> = emptyList(),
    /** Stickers posés par-dessus, en placement libre. */
    val stickers: List<Sticker> = emptyList(),
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

/** Un widget Android d'une autre appli (météo, agenda…). */
@Serializable
@SerialName("widget")
data class AppWidgetBlock(
    override val id: String,
    /** Numéro attribué par Android à ce widget. */
    val appWidgetId: Int,
    /** L'appli qui fournit le widget (pour l'afficher en mode édition). */
    val provider: String,
    val heightDp: Int = 180,
    /** true = demi-largeur (2×2), à côté d'un autre élément. */
    val half: Boolean = false,
) : HomeBlock()

/** Widget maison « Ciel » : arc du soleil le jour, phase de lune la nuit. */
@Serializable
@SerialName("ciel")
data class SkyBlock(override val id: String, val half: Boolean = false) : HomeBlock()

/** Widget maison « Musique » : ce qui joue, pochette en deux tons carmin et rose. */
@Serializable
@SerialName("musique")
data class MusicBlock(override val id: String, val half: Boolean = false) : HomeBlock()

/** Widget maison « Contexte » : moment de la journée, prochain événement, note du jour. */
@Serializable
@SerialName("contexte")
data class ContextBlock(override val id: String, val note: String = "", val half: Boolean = false) : HomeBlock()

/** Une demi-place vide : pour laisser de l'air (ou poser un sticker) à côté d'un pack. */
@Serializable
@SerialName("espace")
data class SpacerBlock(override val id: String) : HomeBlock()

/**
 * Un sticker posé librement sur une page (sans grille).
 * Position du centre en fraction de la page (0 à 1), taille en dp, rotation en degrés.
 */
@Serializable
data class Sticker(
    val id: String,
    /** Nom du fichier image dans le dossier privé `stickers/`. */
    val file: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val sizeDp: Float = 140f,
    val rotation: Float = 0f,
)

/** Où se trouve Jules (pour le soleil et la lune). */
@Serializable
data class PlaceSettings(
    val name: String = "Nantes",
    val latitude: Double = 47.2184,
    val longitude: Double = -1.5536,
)

@Serializable
enum class DrawerStyle { Grille, Liste }

@Serializable
enum class DrawerSort { Alphabetique, Frequence, Familles }

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
    SpaceSuivant("Passer au Space suivant"),
    Focus("Activer / couper le mode Focus"),
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
