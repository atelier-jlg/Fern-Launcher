package com.atelierjlg.fern.messages.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Comment prévenir pour une conversation. */
enum class NotifyMode(val label: String) { Son("Avec son"), Silencieux("Silencieux"), Aucune("Désactivées") }

/** Un message à envoyer plus tard. */
@Serializable
data class Scheduled(
    val id: Long,
    val addresses: List<String>,
    val body: String,
    val at: Long,
)

/** Ce que Fern Messages retient en plus des SMS eux-mêmes (fichier JSON dans l'appli). */
@Serializable
data class MessagesPrefs(
    val archived: Set<Long> = emptySet(),
    val notify: Map<Long, NotifyMode> = emptyMap(),
    /** Brouillons : texte commencé mais pas envoyé, par conversation. */
    val drafts: Map<Long, String> = emptyMap(),
    val scheduled: List<Scheduled> = emptyList(),
)

/**
 * Lecture / écriture de [MessagesPrefs]. Petit fichier, lu et écrit en entier à chaque fois
 * (synchronisé : les récepteurs de SMS et l'appli peuvent y toucher en même temps).
 */
object PrefsStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val KEY = "prefs"

    @Synchronized
    fun read(context: Context): MessagesPrefs {
        val text = context.getSharedPreferences("fern-messages", Context.MODE_PRIVATE).getString(KEY, null) ?: return MessagesPrefs()
        return runCatching { json.decodeFromString(MessagesPrefs.serializer(), text) }.getOrDefault(MessagesPrefs())
    }

    @Synchronized
    fun update(context: Context, transform: (MessagesPrefs) -> MessagesPrefs): MessagesPrefs {
        val new = transform(read(context))
        context.getSharedPreferences("fern-messages", Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(MessagesPrefs.serializer(), new)).commit()
        return new
    }
}
