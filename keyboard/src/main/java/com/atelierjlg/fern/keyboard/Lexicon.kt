package com.atelierjlg.fern.keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.atelierjlg.fern.keyboard.logic.Corrector
import com.atelierjlg.fern.keyboard.logic.Dictionary
import com.atelierjlg.fern.keyboard.logic.ExtraWords
import com.atelierjlg.fern.keyboard.logic.UserDictionary
import java.io.File
import java.util.concurrent.Executors

/**
 * Tout le vocabulaire du clavier, chargé une fois en arrière-plan :
 * - le dictionnaire français (assets/dico_fr.txt.gz, ≈ 1 s au premier démarrage) ;
 * - les mots appris (fichier mots-appris.tsv, dans le téléphone uniquement) ;
 * - les prénoms et noms des contacts (si Jules l'a autorisé).
 *
 * Les calculs de correction tournent aussi sur ce fil d'arrière-plan : la frappe ne rame jamais.
 */
object Lexicon {
    /** Un seul fil de travail : chargement, calculs, sauvegardes passent les uns après les autres. */
    val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "fern-lexique").apply { priority = Thread.NORM_PRIORITY - 1 } }

    @Volatile private var dictionary: Dictionary? = null
    @Volatile private var corrector: Corrector? = null
    @Volatile private var loading = false
    @Volatile var user: UserDictionary = UserDictionary()
        private set
    @Volatile private var contacts: Map<String, String> = emptyMap()
    @Volatile private var dirty = false

    fun corrector(): Corrector? = corrector

    private const val USER_FILE = "mots-appris.tsv"

    /** Lance le chargement s'il n'est pas déjà fait (sans bloquer). */
    fun ensureLoaded(context: Context, withContacts: Boolean) {
        val app = context.applicationContext
        if (dictionary == null && !loading) {
            loading = true
            worker.execute {
                runCatching {
                    val dict = app.assets.open("dico_fr.txt.gz").use { Dictionary.load(it) }
                    user = readUser(app)
                    contacts = if (withContacts) readContacts(app) else emptyMap()
                    dictionary = dict
                    rebuild()
                }
                loading = false
            }
        }
    }

    private fun rebuild() {
        val dict = dictionary ?: return
        corrector = Corrector(
            dict,
            ExtraWords { lower -> user.weight(lower) + if (lower in contacts) 3 else 0 },
            { user.knownWords().map { it.lowercase() } + contacts.keys },
        )
    }

    /** Recharge (ou oublie) les noms des contacts, après un changement de réglage. */
    fun reloadContacts(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        worker.execute {
            contacts = if (enabled) readContacts(app) else emptyMap()
            rebuild()
        }
    }

    /**
     * Modifie les mots appris. Toujours sur le fil d'arrière-plan : les calculs de correction lisent
     * les mêmes données, on évite ainsi que deux fils y touchent en même temps.
     */
    fun edit(block: (UserDictionary) -> Unit) {
        worker.execute {
            runCatching { block(user) }
            dirty = true
        }
    }

    /** Sauvegarde les mots appris si quelque chose a changé. */
    fun saveUser(context: Context) {
        val app = context.applicationContext
        worker.execute {
            if (!dirty) return@execute
            dirty = false
            runCatching {
                val file = File(app.filesDir, USER_FILE)
                val tmp = File(app.filesDir, "$USER_FILE.tmp")
                tmp.writeText(user.serialize())
                tmp.renameTo(file)
            }
        }
    }

    /** Efface tout ce qui a été appris. */
    fun forgetAll(context: Context) {
        val app = context.applicationContext
        worker.execute {
            user.clear()
            runCatching { File(app.filesDir, USER_FILE).delete() }
            dirty = false
            rebuild()
        }
    }

    /** Pour l'écran de réglages : nombre de mots appris (lit le fichier si le clavier n'est pas lancé). */
    fun learnedCount(context: Context): Int = runCatching {
        if (dictionary != null) user.wordCount else readUser(context.applicationContext).wordCount
    }.getOrDefault(0)

    private fun readUser(context: Context): UserDictionary {
        val file = File(context.filesDir, USER_FILE)
        return if (file.exists()) runCatching { UserDictionary.parse(file.readText()) }.getOrDefault(UserDictionary()) else UserDictionary()
    }

    private fun readContacts(context: Context): Map<String, String> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyMap()
        val out = HashMap<String, String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    name.split(' ', '-', '.', ',').forEach { part ->
                        if (part.length >= 2 && part.all { it.isLetter() || it == '\'' }) out[part.lowercase()] = part
                    }
                }
            }
        }
        return out
    }
}
