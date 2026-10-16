package com.atelierjlg.fern.keyboard.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atelierjlg.fern.keyboard.logic.SpeechText
import java.io.File
import java.util.concurrent.Executors

/** Le pont vers whisper.cpp (code natif, voir src/main/cpp). */
object WhisperLib {
    /** false si le code natif n'a pas pu être chargé (téléphone 32 bits…). */
    val available: Boolean = runCatching { System.loadLibrary("fernwhisper"); true }.getOrDefault(false)

    external fun initContext(path: String): Long
    external fun freeContext(ptr: Long)
    external fun transcribe(ptr: Long, audio: FloatArray, threads: Int, language: String, prompt: String): ByteArray
}

/**
 * La dictée hors ligne : on enregistre (16 kHz, mono), puis Whisper écrit le texte, sur le téléphone.
 * Rien ne sort du téléphone (le clavier n'a même pas la permission Internet).
 *
 * Le modèle (fichier de 150 Mo à 1,5 Go) est importé une fois par Jules dans les réglages ; il reste
 * chargé en mémoire tant que le clavier vit, pour que la dictée démarre vite.
 */
class Dictation(private val context: Context) {
    enum class State { Idle, Loading, Listening, Transcribing }

    var state by mutableStateOf(State.Idle)
        private set
    /** Volumes récents (0 à 1), pour dessiner l'onde. */
    var levels by mutableStateOf(List(BARS) { 0f })
        private set
    /** Message à afficher (erreur, conseil). */
    var message by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var recording = false
    /**
     * Numéro de la prise en cours. Chaque début ou abandon le change : un ancien travail
     * (enregistrement, transcription) qui voit un autre numéro s'arrête sans rien écrire.
     * Sinon, une dictée annulée pouvait écrire son texte dans la suivante.
     */
    @Volatile private var session = 0
    private var onText: ((String) -> Unit)? = null

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Commence à écouter. `autoStop` : s'arrête tout seul après un silence. */
    fun start(autoStop: Boolean, onText: (String) -> Unit) {
        if (state != State.Idle) return
        message = null
        if (!WhisperLib.available) {
            message = "La dictée n'est pas disponible sur ce téléphone."
            return
        }
        if (!hasPermission()) {
            message = "Autorise le micro dans les réglages du clavier."
            return
        }
        val model = modelFile(context)
        if (!model.exists()) {
            message = "Importe d'abord un modèle Whisper dans les réglages du clavier."
            return
        }
        this.onText = onText
        val mine = ++session
        recording = true
        state = if (contextPtr == 0L) State.Loading else State.Listening
        worker.execute {
            if (mine != session) return@execute
            if (contextPtr == 0L) {
                contextPtr = runCatching { WhisperLib.initContext(model.path) }.getOrDefault(0L)
                loadedFor = model.lastModified()
            }
            if (contextPtr == 0L) {
                main.post {
                    if (mine != session) return@post
                    state = State.Idle
                    message = "Le modèle n'a pas pu être chargé. Réimporte-le dans les réglages."
                }
                return@execute
            }
            // Annulé (ou déjà arrêté) pendant le chargement du modèle : on n'ouvre même pas le micro.
            if (mine != session || !recording) {
                main.post { if (mine == session) state = State.Idle }
                return@execute
            }
            main.post { if (mine == session) state = State.Listening }
            record(autoStop, mine)
        }
    }

    /** Fin de la prise de parole : on transcrit. */
    fun stop() {
        recording = false
    }

    /** Abandon : rien ne sera écrit. */
    fun cancel() {
        session++
        recording = false
        state = State.Idle
    }

    @SuppressLint("MissingPermission") // vérifiée dans start()
    private fun record(autoStop: Boolean, mine: Int) {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE / 5 * 2))
        }.getOrNull()
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            main.post {
                if (mine != session) return@post
                state = State.Idle
                message = "Le micro est occupé par une autre appli."
            }
            recorder?.release()
            return
        }
        val all = ShortArray(RATE * MAX_SECONDS)
        var total = 0
        val chunk = ShortArray(RATE / 20) // 50 ms
        var spoke = false
        var quietMs = 0
        recorder.startRecording()
        while (recording && mine == session && total < all.size) {
            val n = recorder.read(chunk, 0, minOf(chunk.size, all.size - total))
            if (n <= 0) break
            chunk.copyInto(all, total, 0, n)
            total += n
            val level = SpeechText.level(chunk, n)
            main.post { levels = levels.drop(1) + level }
            // Arrêt automatique : après avoir parlé, 1,6 s de silence.
            if (level > SPEECH_LEVEL) {
                spoke = true
                quietMs = 0
            } else {
                quietMs += n * 1000 / RATE
                // Après avoir parlé : 1,6 s de silence. Sans rien dire du tout (micro coupé…) : 6 s.
                if (autoStop && quietMs >= if (spoke) SILENCE_MS else NO_SPEECH_MS) break
            }
        }
        runCatching { recorder.stop() }
        recorder.release()
        if (mine != session) return
        recording = false
        if (!spoke || total < RATE / 2) {
            main.post {
                if (mine != session) return@post
                state = State.Idle
                message = "Je n'ai rien entendu."
            }
            return
        }
        main.post { if (mine == session) state = State.Transcribing }
        val audio = SpeechText.toFloat(all, total)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val text = runCatching {
            String(WhisperLib.transcribe(contextPtr, audio, threads, "fr", PROMPT), Charsets.UTF_8)
        }.getOrDefault("")
        val clean = SpeechText.clean(text)
        main.post {
            if (mine != session) return@post
            state = State.Idle
            if (clean.isEmpty()) message = "Je n'ai pas compris, réessaie." else onText?.invoke(clean)
        }
    }

    companion object {
        const val RATE = 16_000
        private const val MAX_SECONDS = 30
        private const val BARS = 24
        private const val SPEECH_LEVEL = 0.08f
        private const val SILENCE_MS = 1600
        private const val NO_SPEECH_MS = 6000
        private const val PROMPT = "Bonjour ! Oui, ça va, et toi ? Je m'ennuie un peu ce soir, on se voit vers 20 h ?"

        /** Un seul fil pour Whisper, et le modèle chargé une fois pour toutes. */
        private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "fern-dictee") }
        @Volatile private var contextPtr = 0L
        @Volatile private var loadedFor = 0L

        fun modelFile(context: Context) = File(context.applicationContext.filesDir, "whisper/model.bin")

        /** Le modèle a changé (import, suppression) : on libère l'ancien. */
        fun unloadModel() {
            worker.execute {
                if (contextPtr != 0L) runCatching { WhisperLib.freeContext(contextPtr) }
                contextPtr = 0L
                loadedFor = 0L
            }
        }
    }
}
