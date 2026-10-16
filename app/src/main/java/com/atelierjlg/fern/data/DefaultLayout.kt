package com.atelierjlg.fern.data

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.Telephony
import android.telecom.TelecomManager
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.normalizeForSearch
import com.atelierjlg.fern.apps.searchRank

/**
 * La disposition de départ, reprise de design/design-tokens.json.
 * Elle n'est posée qu'une fois (premier lancement) ; ensuite tout se modifie dans l'appli.
 *
 * Les applis sont cherchées par leur nom : si une appli n'est pas installée,
 * l'emplacement reste simplement vide.
 */
object DefaultLayout {

    fun seed(config: LauncherConfig, apps: List<AppEntry>, context: Context): LauncherConfig {
        fun byName(vararg names: String): String? = names.firstNotNullOfOrNull { findByName(apps, it) }
        fun byPackage(pkg: String?): String? = pkg?.let { p -> apps.firstOrNull { it.packageName == p }?.key }
        fun pack(title: String, vararg names: String) =
            PackBlock(id = newId(), title = title, apps = names.map { byName(it) }.padded())

        val dialer = byPackage(context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage)
        val sms = byPackage(Telephony.Sms.getDefaultSmsPackage(context))
        val camera = byPackage(
            context.packageManager.resolveActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 0)
                ?.activityInfo?.packageName,
        ) ?: byName("Appareil photo", "Camera")

        val space = Space(
            pages = listOf(
                HomePage(
                    id = "accueil",
                    title = "Accueil",
                    blocks = listOf(
                        ClockBlock(id = newId()),
                        AppRowBlock(
                            id = newId(),
                            apps = listOf(byName("WhatsApp"), byName("Instagram"), byName("Curve"), byName("Proton Mail", "Proton")),
                        ),
                    ),
                ),
                HomePage(
                    id = newId(),
                    title = "Journée",
                    blocks = listOf(
                        pack("Organisation", "Agenda", "Tâches", "Notes", "Obsidian"),
                        pack("Travail", "Claude", "Teams", "Miro", "Canva"),
                        pack("Études", "AnkiDroid", "NumWorks", "MJ PDF", "AIESB"),
                    ),
                ),
                HomePage(
                    id = newId(),
                    title = "Pratique",
                    blocks = listOf(
                        pack("Argent", "N26", "Crédit Coop", "tricount"),
                        pack("Déplacements", "Naolib", "SNCF Connect", "Maps", "Météo-France"),
                        pack("Courses", "Magasin U", "Jow", "leboncoin"),
                        pack("Urgence", "App-Elles", "StayingAlive"),
                    ),
                ),
                HomePage(id = newId(), title = "Les miens"),
            ),
            dock = listOf(dialer ?: byName("Fil", "Téléphone"), sms ?: byName("Messages"), byName("Signal"), camera),
        )
        return config.copy(seeded = true, spaces = listOf(space), activeSpaceId = space.id)
    }

    /** Cherche une appli dont le nom (ou un mot du nom) commence par `name`. */
    private fun findByName(apps: List<AppEntry>, name: String): String? {
        val q = name.normalizeForSearch()
        return apps
            .mapNotNull { app -> searchRank(app.searchKey, q)?.takeIf { it <= 1 }?.let { it to app } }
            .minByOrNull { it.first }
            ?.second?.key
    }

    private fun List<String?>.padded(): List<String?> = this + List((PACK_SIZE - size).coerceAtLeast(0)) { null }
}
