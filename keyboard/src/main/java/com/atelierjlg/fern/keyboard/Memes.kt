package com.atelierjlg.fern.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.atelierjlg.fern.keyboard.logic.MemeLibrary
import java.io.File
import java.util.UUID

/**
 * Les memes et stickers de Jules, rangés dans le dossier privé du clavier (memes/).
 * - import depuis la galerie (sélecteur de photos d'Android) ;
 * - import des stickers de Fern Launcher (prêtés par Fern, même clé de signature) ;
 * - miniatures gardées en mémoire pour un défilement fluide.
 *
 * La liste (MemeLibrary) n'est modifiée que sur le fil principal ; les fichiers sur le fil de Lexicon.
 */
object Memes {
    @Volatile var library = MemeLibrary()
        private set
    private var loaded = false
    private val listeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val thumbs = LruCache<String, Bitmap>(60)

    private const val LIST_FILE = "memes.tsv"
    private val FERN_STICKERS: Uri = Uri.parse("content://com.atelierjlg.fern.stickers/list")

    fun dir(context: Context) = File(context.applicationContext.filesDir, "memes").apply { mkdirs() }
    fun file(context: Context, meme: MemeLibrary.Meme) = File(dir(context), meme.file)

    /** Prévient quand la liste change (l'interface se met à jour). Renvoie de quoi arrêter. */
    fun observe(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    private fun changed() = listeners.toList().forEach { it() }

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val app = context.applicationContext
        Lexicon.worker.execute {
            val lib = runCatching { MemeLibrary.parse(File(app.filesDir, LIST_FILE).readText()) }.getOrDefault(MemeLibrary())
            main.post {
                library = lib
                changed()
            }
        }
    }

    private fun save(context: Context) {
        val app = context.applicationContext
        val text = library.serialize()
        Lexicon.worker.execute {
            runCatching {
                val tmp = File(app.filesDir, "$LIST_FILE.tmp")
                tmp.writeText(text)
                tmp.renameTo(File(app.filesDir, LIST_FILE))
            }
        }
    }

    /** Copie des images (galerie) dans la bibliothèque. `done` reçoit le nombre d'images ajoutées. */
    fun importUris(context: Context, uris: List<Uri>, done: (Int) -> Unit = {}) {
        val app = context.applicationContext
        Lexicon.worker.execute {
            val added = uris.mapNotNull { uri -> runCatching { copyIn(app, uri, fromFern = null) }.getOrNull() }
            main.post {
                added.forEach { library.add(it) }
                save(app)
                changed()
                done(added.size)
            }
        }
    }

    /** Récupère les stickers de Fern Launcher pas encore importés. */
    fun importFromFern(context: Context, done: (Int) -> Unit) {
        val app = context.applicationContext
        val already = library.all.mapNotNull { it.fromFern }.toSet()
        Lexicon.worker.execute {
            val names = runCatching {
                app.contentResolver.query(FERN_STICKERS, null, null, null, null)?.use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
            }.getOrNull()
            val added = names.orEmpty().filter { it !in already }.mapNotNull { name ->
                val uri = Uri.parse("content://com.atelierjlg.fern.stickers/file/$name")
                runCatching { copyIn(app, uri, fromFern = name) }.getOrNull()
            }
            main.post {
                added.forEach { library.add(it) }
                save(app)
                changed()
                done(if (names == null) -1 else added.size)
            }
        }
    }

    private fun copyIn(context: Context, uri: Uri, fromFern: String?): MemeLibrary.Meme {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("illisible")
        require(bytes.size in 1..MAX_BYTES) { "trop lourd" }
        val mime = MemeLibrary.sniffMime(bytes.copyOf(minOf(16, bytes.size)))
        val id = UUID.randomUUID().toString().take(12)
        val name = "$id.${MemeLibrary.extension(mime)}"
        File(dir(context), name).writeBytes(bytes)
        return MemeLibrary.Meme(id = id, file = name, mime = mime, added = System.currentTimeMillis(), fromFern = fromFern)
    }

    fun setTags(context: Context, id: String, text: String) {
        library.setTags(id, text)
        save(context)
        changed()
    }

    fun used(context: Context, id: String) {
        library.used(id)
        save(context)
        changed()
    }

    fun delete(context: Context, id: String) {
        val m = library.remove(id) ?: return
        thumbs.remove(m.file)
        val f = File(dir(context), m.file)
        Lexicon.worker.execute { runCatching { f.delete() } }
        save(context)
        changed()
    }

    /** Miniature (≈ 240 px), depuis le cache si possible. À appeler hors du fil principal. */
    fun thumbnail(context: Context, meme: MemeLibrary.Meme): Bitmap? {
        thumbs.get(meme.file)?.let { return it }
        val f = File(dir(context), meme.file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        thumbs.put(meme.file, bmp)
        return bmp
    }

    private const val THUMB_PX = 240
    private const val MAX_BYTES = 15 * 1024 * 1024
}
