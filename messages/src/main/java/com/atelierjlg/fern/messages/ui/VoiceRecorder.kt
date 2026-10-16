package com.atelierjlg.fern.messages.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Enregistre un message vocal au format AMR (celui des MMS : environ 1,6 Ko par seconde,
 * donc près de 3 minutes tiennent dans un MMS de 300 Ko).
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    val startedAt = System.currentTimeMillis()

    fun start(): Boolean = runCatching {
        val dir = File(context.cacheDir, "vocal").apply { mkdirs() }
        val f = File(dir, "vocal-${System.currentTimeMillis()}.amr")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        r.setMaxDuration(MAX_MS)
        r.setOutputFile(f.path)
        r.prepare()
        r.start()
        recorder = r
        file = f
        true
    }.getOrElse {
        release()
        false
    }

    /** Arrête et renvoie le fichier (null si trop court ou raté). */
    fun stop(): File? {
        runCatching { recorder?.stop() }
        release()
        return file?.takeIf { it.exists() && it.length() > 200 }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        release()
        file?.delete()
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }

    companion object {
        const val MAX_MS = 170_000
    }
}
