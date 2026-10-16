package com.atelierjlg.fern.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.atelierjlg.fern.theme.FernThemeContract
import java.io.File

/*
 * Fern Launcher partage son thème actif avec Fern Messages et Fern Contact.
 *
 * 1. Quand le thème affiché change (Paramètres, Space…), [ThemeShare.publish] l'écrit dans
 *    un petit fichier et prévient les applis qui écoutent.
 * 2. Ces applis le lisent via [FernThemeProvider] (`content://com.atelierjlg.fern.theme/active`),
 *    réservé aux applis signées avec la même clé (voir le manifeste du module theme).
 */
object ThemeShare {
    private const val FILE_NAME = "shared-theme.json"

    /** Le texte publié : le même format que l'export d'un thème (« .json »). */
    fun encode(theme: NamedTheme): String =
        FernJson.encodeToString(ThemeFile.serializer(), ThemeFile(theme = theme))

    fun publish(context: Context, theme: NamedTheme) {
        val file = File(context.filesDir, FILE_NAME)
        val json = encode(theme)
        if (file.exists() && runCatching { file.readText() }.getOrNull() == json) return
        try {
            // Écriture dans un fichier temporaire puis renommage : jamais de fichier à moitié écrit.
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(json)
            tmp.renameTo(file)
            context.contentResolver.notifyChange(FernThemeContract.URI, null)
        } catch (e: Exception) {
            Log.w("FernThemeShare", "Impossible de publier le thème", e)
        }
    }

    fun read(context: Context): String =
        runCatching { File(context.filesDir, FILE_NAME).readText() }.getOrNull()
            ?: encode(ThemePresets.EstampeNuit)
}

/** Répond « le thème actif, en JSON » aux applis sœurs. Lecture seule. */
class FernThemeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val context = context ?: return MatrixCursor(arrayOf(FernThemeContract.COLUMN_JSON))
        return MatrixCursor(arrayOf(FernThemeContract.COLUMN_JSON)).apply {
            addRow(arrayOf(ThemeShare.read(context)))
        }
    }

    override fun getType(uri: Uri): String = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
