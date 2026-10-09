package com.atelierjlg.fern.widgets

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray

/**
 * Lit le nombre de cartes à réviser dans AnkiDroid (son « API » officielle pour les autres applis).
 * Il faut qu'AnkiDroid soit installé et que Jules autorise Fern une fois.
 */
object Anki {
    const val PACKAGE = "com.ichi2.anki"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private val DECKS: Uri = Uri.parse("content://com.ichi2.anki.flashcards/decks")

    sealed class State {
        data object NotInstalled : State()
        data object NoPermission : State()
        data class Due(val cards: Int) : State()
    }

    fun state(context: Context): State {
        val installed = runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
        if (!installed) return State.NotInstalled
        if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return State.NoPermission
        }
        return State.Due(dueCards(context))
    }

    /**
     * Total des cartes à voir aujourd'hui : on additionne les paquets « racine »
     * (un sous-paquet Maths::Algèbre est déjà compté dans Maths).
     */
    private fun dueCards(context: Context): Int = runCatching {
        context.contentResolver.query(DECKS, null, null, null, null)?.use { c ->
            val nameCol = c.getColumnIndex("deck_name")
            val countsCol = c.getColumnIndex("deck_count")
            var total = 0
            while (c.moveToNext()) {
                val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                if ("::" in name || countsCol < 0) continue
                // Format : [à apprendre, à revoir, nouvelles]
                val counts = JSONArray(c.getString(countsCol) ?: "[]")
                for (i in 0 until counts.length()) total += counts.optInt(i)
            }
            total
        } ?: 0
    }.onFailure { Log.w("FernAnki", "Lecture AnkiDroid impossible", it) }.getOrDefault(0)
}
