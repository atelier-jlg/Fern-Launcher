package com.atelierjlg.fern.keyboard.voice

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.Executors

/** Copie d'un modèle Whisper (fichier choisi par Jules) dans le dossier privé du clavier. */
object ModelImport {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * @param progress appelé de temps en temps avec le nombre de Mo copiés.
     * @param done le nom du fichier si tout s'est bien passé, null sinon.
     */
    fun copy(context: Context, uri: Uri, progress: (Long) -> Unit, done: (String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val name = runCatching {
                app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: "modèle"
            val target = Dictation.modelFile(app)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "model.tmp")
            val ok = runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        var total = 0L
                        var lastMb = -1L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                            val mb = total / (1024 * 1024)
                            if (mb != lastMb && mb % 10 == 0L) {
                                lastMb = mb
                                main.post { progress(mb) }
                            }
                        }
                    }
                } ?: error("illisible")
                // Un modèle ggml commence par « lmgg » (0x67676d6c à l'envers) : on vérifie que c'est le bon fichier.
                val head = tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
                require(String(head, Charsets.US_ASCII) == "lmgg") { "pas un modèle Whisper" }
                tmp.renameTo(target)
            }.getOrDefault(false)
            if (!ok) tmp.delete()
            main.post { done(if (ok) name.removeSuffix(".bin") else null) }
        }
    }
}
