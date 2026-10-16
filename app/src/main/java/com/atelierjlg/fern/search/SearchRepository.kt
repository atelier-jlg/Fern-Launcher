package com.atelierjlg.fern.search

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.atelierjlg.fern.apps.normalizeForSearch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Un contact trouvé. */
data class ContactResult(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phone: String?,
) {
    val uri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

/** Un événement d'agenda trouvé (ICSx⁵ / Proton Calendar, ou tout autre agenda Android). */
data class EventResult(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val whenLabel: String,
)

/** Un raccourci d'appli (« Nouveau message », « Itinéraire maison »…). */
data class ShortcutResult(
    val info: ShortcutInfo,
    val label: String,
    val appLabel: String,
    val icon: ImageBitmap?,
)

/**
 * Cherche ailleurs que dans les applis : contacts, agenda, raccourcis.
 * Toutes ces fonctions lisent des bases Android : à appeler hors du fil principal.
 */
class SearchRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val iconSizePx = (36 * context.resources.displayMetrics.density).roundToInt()

    fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ─── Contacts ───────────────────────────────────────────────────────────

    fun contacts(query: String, limit: Int = 5): List<ContactResult> {
        if (query.isBlank() || !hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val results = mutableListOf<ContactResult>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && results.size < limit) {
                    val id = c.getLong(0)
                    val hasPhone = c.getInt(3) > 0
                    results += ContactResult(
                        id = id,
                        lookupKey = c.getString(1) ?: continue,
                        name = c.getString(2) ?: continue,
                        phone = if (hasPhone) firstPhone(id) else null,
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Recherche de contacts impossible", it) }
        return results
    }

    private fun firstPhone(contactId: Long): String? = runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    // ─── Agenda ─────────────────────────────────────────────────────────────

    private val eventFormat = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.FRENCH)

    /** Les événements des 60 prochains jours (et des 7 derniers) dont le titre contient la recherche. */
    fun events(query: String, limit: Int = 5): List<EventResult> {
        if (query.isBlank() || !hasPermission(Manifest.permission.READ_CALENDAR)) return emptyList()
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now - 7 * day)
        ContentUris.appendId(builder, now + 60 * day)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )
        val results = mutableListOf<EventResult>()
        val q = query.normalizeForSearch()
        runCatching {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext() && results.size < limit) {
                    val title = c.getString(1) ?: continue
                    // Filtre sans accents ni majuscules (« reunion » trouve « Réunion »).
                    if (!title.normalizeForSearch().contains(q)) continue
                    val begin = c.getLong(2)
                    val allDay = c.getInt(3) == 1
                    val date = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault())
                    results += EventResult(
                        eventId = c.getLong(0),
                        title = title,
                        begin = begin,
                        whenLabel = if (allDay) {
                            date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH))
                        } else {
                            date.format(eventFormat)
                        },
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Recherche d'agenda impossible", it) }
        return results
    }

    /** Le prochain événement des 24 prochaines heures (pour le widget Contexte). */
    fun nextEvent(): EventResult? {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) return null
        val now = System.currentTimeMillis()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, now + 24L * 60 * 60 * 1000)
        return runCatching {
            context.contentResolver.query(
                builder.build(),
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY,
                ),
                "${CalendarContract.Instances.BEGIN} >= ? AND ${CalendarContract.Instances.ALL_DAY} = 0",
                arrayOf(now.toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val begin = c.getLong(2)
                EventResult(
                    eventId = c.getLong(0),
                    title = c.getString(1) ?: "",
                    begin = begin,
                    whenLabel = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)),
                )
            }
        }.getOrNull()
    }

    // ─── Raccourcis d'applis ────────────────────────────────────────────────

    /** Il faut que Fern soit le lanceur par défaut pour lire les raccourcis. */
    fun canReadShortcuts(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcuts(query: String, appLabels: Map<String, String>, limit: Int = 6): List<ShortcutResult> {
        if (query.isBlank() || !canReadShortcuts()) return emptyList()
        val q = query.normalizeForSearch()
        val shortcutQuery = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        val all = runCatching { launcherApps.getShortcuts(shortcutQuery, Process.myUserHandle()) }
            .getOrNull().orEmpty()
        return all.asSequence()
            .filter { it.isEnabled }
            .mapNotNull { info ->
                val label = (info.shortLabel ?: info.longLabel)?.toString() ?: return@mapNotNull null
                val appLabel = appLabels[info.`package`] ?: ""
                val matches = label.normalizeForSearch().contains(q) || appLabel.normalizeForSearch().startsWith(q)
                if (!matches) return@mapNotNull null
                ShortcutResult(info = info, label = label, appLabel = appLabel, icon = null)
            }
            .take(limit)
            .map { it.copy(icon = shortcutIcon(it.info)) }
            .toList()
    }

    private fun shortcutIcon(info: ShortcutInfo): ImageBitmap? = runCatching {
        launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
            ?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
    }.getOrNull()

    fun startShortcut(result: ShortcutResult) {
        runCatching { launcherApps.startShortcut(result.info, null, null) }
            .onFailure { Log.w(TAG, "Raccourci impossible à lancer", it) }
    }

    private companion object {
        const val TAG = "FernSearch"
    }
}
