package com.atelierjlg.fern.apps

import com.atelierjlg.fern.data.LauncherConfig
import com.atelierjlg.fern.data.currentlyHidden
import java.text.Collator
import java.util.Locale

/**
 * La liste des applis telle que Fern l'affiche : renommages appliqués, applis masquées à part,
 * et un dictionnaire pour retrouver une appli à partir de sa clé (packs, dock…).
 */
class AppIndex private constructor(
    /** Toutes les applis, y compris masquées (pour les réglages). */
    val all: List<AppEntry>,
    /** Les applis visibles dans le tiroir et la recherche. */
    val visible: List<AppEntry>,
    /** Les applis masquées. */
    val hidden: List<AppEntry>,
    private val byKey: Map<String, AppEntry>,
    private val byPackage: Map<String, AppEntry>,
    /** Applis bloquées par le mode Focus (invisibles partout tant qu'il est actif). */
    private val blocked: Set<String>,
) {
    /** Retrouve une appli enregistrée ; si son activité a changé (mise à jour), on la retrouve par son paquet. */
    fun find(key: String?): AppEntry? {
        if (key == null) return null
        val app = byKey[key] ?: byPackage[packageKeyOf(key)] ?: return null
        return if (app.key in blocked) null else app
    }

    companion object {
        val Empty = AppIndex(emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap(), emptySet())

        fun build(apps: List<AppEntry>, config: LauncherConfig): AppIndex {
            val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }
            val renamed = apps
                .map { app ->
                    val renamed = config.renamedApps[app.key]?.let { app.copy(label = it) } ?: app
                    config.appFamilies[app.key]?.let { renamed.copy(family = it) } ?: renamed
                }
                .sortedWith(compareBy(collator) { it.label })
            return AppIndex(
                all = renamed,
                visible = renamed.filterNot { it.key in config.currentlyHidden },
                hidden = renamed.filter { it.key in config.hiddenApps },
                byKey = renamed.associateBy { it.key },
                byPackage = renamed.associateBy { packageKeyOf(it.key) },
                blocked = if (config.focus.enabled) config.focus.blockedApps else emptySet(),
            )
        }
    }
}
