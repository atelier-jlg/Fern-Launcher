package com.atelierjlg.fern.contact.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.provider.VoicemailContract.Voicemails

/** Un message vocal (messagerie « visuelle », si l'opérateur la propose). */
data class VoicemailEntry(
    val id: Long,
    val number: String,
    val date: Long,
    val durationSec: Long,
    val isRead: Boolean,
    val transcription: String?,
    val hasAudio: Boolean,
)

/**
 * La messagerie vocale visuelle d'Android. Beaucoup d'opérateurs français ne la proposent pas
 * aux applis tierces : la liste reste alors vide et on propose d'appeler la messagerie (le 888…).
 */
class VoicemailRepo(private val context: Context) {
    fun all(): List<VoicemailEntry> {
        val out = mutableListOf<VoicemailEntry>()
        runCatching {
            context.contentResolver.query(
                Voicemails.CONTENT_URI,
                arrayOf(Voicemails._ID, Voicemails.NUMBER, Voicemails.DATE, Voicemails.DURATION, Voicemails.IS_READ, Voicemails.TRANSCRIPTION, Voicemails.HAS_CONTENT),
                "${Voicemails.DELETED} = 0", null, "${Voicemails.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += VoicemailEntry(
                        c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getLong(3),
                        c.getInt(4) == 1, c.getString(5), c.getInt(6) == 1,
                    )
                }
            }
        }
        return out
    }

    fun markRead(id: Long) = runCatching {
        context.contentResolver.update(
            ContentUris.withAppendedId(Voicemails.CONTENT_URI, id), ContentValues().apply { put(Voicemails.IS_READ, 1) }, null, null,
        )
    }

    fun delete(id: Long) = runCatching { context.contentResolver.delete(ContentUris.withAppendedId(Voicemails.CONTENT_URI, id), null, null) }
}

/** Lecture d'un message vocal (un seul à la fois). */
object VoicemailPlayer {
    private var player: MediaPlayer? = null
    var playingId: Long? = null
        private set

    fun play(context: Context, id: Long, onEnd: () -> Unit): Boolean {
        stop()
        return runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
                setDataSource(context, ContentUris.withAppendedId(Voicemails.CONTENT_URI, id))
                setOnCompletionListener {
                    stop()
                    onEnd()
                }
                prepare()
                start()
            }
            playingId = id
            true
        }.getOrElse {
            stop()
            false
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingId = null
    }
}
