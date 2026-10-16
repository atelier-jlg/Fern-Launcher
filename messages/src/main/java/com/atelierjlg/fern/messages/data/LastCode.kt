package com.atelierjlg.fern.messages.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Le dernier code de vérification reçu par SMS, prêté à Fern Clavier pour qu'il le propose
 * d'un toucher (comme Gboard avec Google Messages).
 *
 * Lisible seulement par les applis signées avec la même clé (permission READ_CODES, niveau
 * signature), et seulement pendant 10 minutes après la réception.
 */
object LastCode {
    private const val PREFS = "dernier-code"
    const val VALID_MS = 10 * 60_000L

    fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.codes/latest")

    fun save(context: Context, code: String, time: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("code", code).putLong("time", time).apply()
        runCatching { context.contentResolver.notifyChange(uri(context), null) }
    }

    /** Le code et l'heure de réception (millisecondes), s'il est encore frais. */
    fun read(context: Context, now: Long = System.currentTimeMillis()): Pair<String, Long>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = p.getString("code", null) ?: return null
        val time = p.getLong("time", 0L)
        return if (now - time in 0..VALID_MS) code to time else null
    }
}

/** content://com.atelierjlg.fern.messages.codes/latest → une ligne (code, time) ou rien. */
class CodesProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(arrayOf("code", "time"))
        val ctx = context ?: return cursor
        LastCode.read(ctx)?.let { (code, time) -> cursor.addRow(arrayOf<Any>(code, time)) }
        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
