package com.atelierjlg.fern.apps

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import java.text.Normalizer

/**
 * Une appli affichable dans le lanceur.
 *
 * `component` désigne l'écran à ouvrir, `user` le profil (perso ou travail) :
 * une même appli peut exister dans les deux profils.
 */
data class AppEntry(
    /** Le nom affiché (éventuellement renommé par Jules). */
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /**
     * Identifiant stable, enregistré dans la configuration (packs, dock, applis masquées…).
     * Format : `paquet/Activité@numéroDeProfil`, voir [appKey].
     */
    val key: String,
    /** Le nom d'origine de l'appli, avant renommage. */
    val originalLabel: String = label,
) {
    /** Le nom sans accents ni majuscules, pour la recherche (« Météo » ↔ « meteo »). */
    val searchKey: String = label.normalizeForSearch()
}

/** Construit l'identifiant stable d'une appli. */
fun appKey(component: ComponentName, userSerial: Long): String =
    "${component.flattenToString()}@$userSerial"

/** `com.exemple/.Main@0` → `com.exemple@0` : sert de secours si l'activité principale a changé. */
fun packageKeyOf(appKey: String): String =
    appKey.substringBefore('/') + "@" + appKey.substringAfterLast('@')

private val combiningMarks = Regex("\\p{Mn}+")

fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase()
        .trim()

/** Le rang d'un nom pour une recherche (0 = meilleur), ou null s'il ne correspond pas. */
fun searchRank(searchKey: String, normalizedQuery: String): Int? = when {
    searchKey.startsWith(normalizedQuery) -> 0
    searchKey.split(' ', '-', '.', '_').any { it.startsWith(normalizedQuery) } -> 1
    searchKey.contains(normalizedQuery) -> 2
    else -> null
}

/**
 * Filtre et trie les applis pour une recherche : d'abord celles dont le nom
 * commence par la recherche, puis celles dont un mot commence par elle,
 * puis celles qui la contiennent ailleurs.
 */
fun List<AppEntry>.search(query: String): List<AppEntry> {
    val q = query.normalizeForSearch()
    if (q.isEmpty()) return this
    return mapNotNull { app -> searchRank(app.searchKey, q)?.let { it to app } }
        .sortedBy { it.first }
        .map { it.second }
}
