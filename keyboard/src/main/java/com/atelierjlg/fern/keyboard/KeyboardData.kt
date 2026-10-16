package com.atelierjlg.fern.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.atelierjlg.fern.keyboard.logic.ClipHistory
import com.atelierjlg.fern.keyboard.logic.EmojiIndex
import com.atelierjlg.fern.keyboard.logic.EmojiMemory
import java.io.File

/**
 * Les autres données du clavier, rangées dans le téléphone :
 * - la liste des emoji (assets/emoji_fr.tsv) ;
 * - les emoji favoris et récents (emoji.tsv) ;
 * - l'historique du presse-papiers (presse-papiers.tsv).
 *
 * Lecture en arrière-plan (fil de Lexicon), puis tout se passe sur le fil principal (l'interface).
 */
object KeyboardData {
    @Volatile var emojis: EmojiIndex? = null
        private set
    var memory = EmojiMemory()
        private set
    var clips = ClipHistory()
        private set
    private var loaded = false
    /** Fichiers lus : avant, on n'écrit rien (sinon l'historique vide écraserait celui du fichier). */
    private var ready = false
    private val main = Handler(Looper.getMainLooper())

    private const val MEMORY_FILE = "emoji.tsv"
    private const val CLIPS_FILE = "presse-papiers.tsv"

    /** Charge tout (une fois). `then` est appelé sur le fil principal quand c'est prêt. */
    fun ensureLoaded(context: Context, keepClips: Int, then: () -> Unit) {
        if (loaded) {
            clips.setKeep(keepClips)
            then()
            return
        }
        loaded = true
        val app = context.applicationContext
        Lexicon.worker.execute {
            val index = runCatching {
                app.assets.open("emoji_fr.tsv").bufferedReader().useLines { EmojiIndex.parse(it.toList().asSequence()) }
            }.getOrNull()
            val mem = runCatching { EmojiMemory.parse(File(app.filesDir, MEMORY_FILE).readText()) }.getOrDefault(EmojiMemory())
            val history = runCatching { ClipHistory.parse(File(app.filesDir, CLIPS_FILE).readText(), keepClips) }
                .getOrDefault(ClipHistory(keepClips))
            main.post {
                emojis = index
                memory = mem
                // Ce qui a été copié pendant le chargement est gardé.
                val copiedMeanwhile = clips.entries.isNotEmpty()
                clips.entries.reversed().forEach { history.add(it.text, it.time) }
                clips = history
                ready = true
                if (copiedMeanwhile) saveClips(app)
                then()
            }
        }
    }

    fun saveMemory(context: Context) {
        if (ready) write(context, MEMORY_FILE, memory.serialize())
    }

    fun saveClips(context: Context) {
        if (ready) write(context, CLIPS_FILE, clips.serialize())
    }

    private fun write(context: Context, name: String, text: String) {
        val app = context.applicationContext
        Lexicon.worker.execute {
            runCatching {
                val tmp = File(app.filesDir, "$name.tmp")
                tmp.writeText(text)
                tmp.renameTo(File(app.filesDir, name))
            }
        }
    }
}
