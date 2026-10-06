package com.atelierjlg.fern.apps

import com.atelierjlg.fern.data.DrawerSort

/** Un élément de la liste du tiroir : un en-tête de section (« A ») ou une appli. */
sealed class DrawerItem {
    abstract val id: String

    data class Header(val letter: String) : DrawerItem() {
        override val id = "section-$letter"
    }

    data class App(val app: AppEntry) : DrawerItem() {
        override val id = app.key
    }
}

/** La lettre de section d'un nom : « Écran » → « E », « 2048 » → « # ». */
fun sectionLetter(label: String): String {
    val first = label.normalizeForSearch().firstOrNull() ?: return "#"
    return if (first in 'a'..'z') first.uppercaseChar().toString() else "#"
}

/**
 * Prépare la liste du tiroir.
 * - Alphabétique : sections A, B, C… (les applis arrivent déjà triées).
 * - Fréquence : les plus lancées d'abord, sans sections.
 */
fun buildDrawerItems(
    apps: List<AppEntry>,
    sort: DrawerSort,
    launchCounts: Map<String, Int>,
): List<DrawerItem> = when (sort) {
    DrawerSort.Frequence ->
        apps.sortedByDescending { launchCounts[it.key] ?: 0 }.map { DrawerItem.App(it) }

    DrawerSort.Alphabetique -> buildList {
        var current: String? = null
        for (app in apps) {
            val letter = sectionLetter(app.label)
            if (letter != current) {
                add(DrawerItem.Header(letter))
                current = letter
            }
            add(DrawerItem.App(app))
        }
    }
}
