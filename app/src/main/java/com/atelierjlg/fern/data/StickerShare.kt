package com.atelierjlg.fern.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Prête les stickers de Fern (les images que Jules a importées) à Fern Clavier, pour les envoyer
 * dans une conversation. Lecture seule, réservé aux applis signées avec la même clé (READ_THEME).
 *
 * - content://com.atelierjlg.fern.stickers/list        → une ligne par fichier (colonne « name »)
 * - content://com.atelierjlg.fern.stickers/file/<nom>  → le fichier lui-même
 */
class FernStickersProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun dir(): File? = context?.let { File(it.filesDir, "stickers") }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(arrayOf("name"))
        dir()?.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }?.forEach { cursor.addRow(arrayOf(it.name)) }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("lecture seule")
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        // Jamais de chemin : seulement un nom de fichier du dossier des stickers.
        if (name.contains('/') || name.contains("..")) throw FileNotFoundException()
        val file = File(dir() ?: throw FileNotFoundException(), name)
        if (!file.isFile) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
