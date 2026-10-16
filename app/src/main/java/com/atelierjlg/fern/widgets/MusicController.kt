package com.atelierjlg.fern.widgets

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import com.atelierjlg.fern.system.FernNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ce qui joue en ce moment. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val packageName: String,
)

/**
 * Suit la musique en cours (n'importe quelle appli : lecteur, radio, podcasts…)
 * grâce aux « sessions média » d'Android.
 */
class MusicController(private val context: Context) {

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, FernNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private var current: MediaController? = null
    private var started = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { refreshSessions() }

    /** À appeler au démarrage et au retour sur Fern (l'accès a pu être accordé entre-temps). */
    fun start() {
        val access = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        _hasAccess.value = access
        if (!access || started) {
            if (started) refreshSessions()
            return
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            started = true
            refreshSessions()
        } catch (e: SecurityException) {
            Log.w(TAG, "Accès aux sessions refusé", e)
            _hasAccess.value = false
        }
    }

    fun stop() {
        if (started) runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        current?.unregisterCallback(controllerCallback)
        current = null
        started = false
    }

    private fun refreshSessions() {
        val sessions = runCatching { sessionManager.getActiveSessions(listener) }.getOrDefault(emptyList())
        // On préfère une session qui joue ; sinon la plus récente.
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (best?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(controllerCallback)
            current = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val controller = current
        val metadata = controller?.metadata
        if (controller == null || metadata == null) {
            _nowPlaying.value = null
            return
        }
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        _nowPlaying.value = NowPlaying(
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            art = art?.asImageBitmap(),
            playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = controller.packageName,
        )
    }

    fun playPause() {
        val c = current ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = current?.transportControls?.skipToNext()

    fun previous() = current?.transportControls?.skipToPrevious()

    private companion object {
        const val TAG = "FernMusic"
    }
}
