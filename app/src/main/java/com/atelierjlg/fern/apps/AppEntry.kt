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
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
) {
    /** Identifiant unique, utile pour les listes et pour mémoriser des réglages par appli. */
    val key: String = "${component.flattenToString()}#$user"

    /** Le nom sans accents ni majuscules, pour la recherche (« Météo » ↔ « meteo »). */
    val searchKey: String = label.normalizeForSearch()
}

private val combiningMarks = Regex("\\p{Mn}+")

fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase()
        .trim()

/**
 * Filtre et trie les applis pour une recherche : d'abord celles dont le nom
 * commence par la recherche, puis celles dont un mot commence par elle,
 * puis celles qui la contiennent ailleurs.
 */
fun List<AppEntry>.search(query: String): List<AppEntry> {
    val q = query.normalizeForSearch()
    if (q.isEmpty()) return this
    return mapNotNull { app ->
        val key = app.searchKey
        val rank = when {
            key.startsWith(q) -> 0
            key.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 1
            key.contains(q) -> 2
            else -> return@mapNotNull null
        }
        rank to app
    }.sortedBy { it.first }.map { it.second }
}
