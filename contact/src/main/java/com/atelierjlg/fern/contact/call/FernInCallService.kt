package com.atelierjlg.fern.contact.call

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.atelierjlg.fern.contact.data.ContactsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Le service que le système appelle quand Fern Contact est l'appli Téléphone par défaut :
 * un appel arrive ou part → [onCallAdded], il se termine → [onCallRemoved].
 * C'est lui qui affiche les notifications d'appel et gère le capteur de proximité.
 */
class FernInCallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        // À chaque changement : notifications et capteur de proximité à jour.
        scope.launch {
            combine(CallManager.calls, CallManager.audio) { calls, audio -> calls to audio }.collect { (calls, audio) ->
                CallNotifications.update(this@FernInCallService, calls)
                updateProximity(calls, audio)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(call)
        resolveContact(call)
        // Appel sortant (ou choix de SIM) : on ouvre l'écran d'appel.
        // Appel entrant : c'est la notification plein écran qui s'en charge… sauf si elle ne peut pas
        // s'afficher (notifications coupées, plein écran refusé) : on ouvre alors l'écran directement.
        if (call.state != Call.STATE_RINGING || !CallNotifications.canShowIncoming(this)) openCallScreen(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.setAudio(audioState)
    }

    override fun onDestroy() {
        releaseProximity()
        CallManager.clear()
        CallNotifications.cancelAll(this)
        scope.cancel()
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }

    /** Cherche le nom et la photo de l'appelant dans les contacts. */
    private fun resolveContact(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: return
        scope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { ContactsRepo(this@FernInCallService).lookup(number) }.getOrNull() }
            if (found != null) CallManager.rememberContact(number, found.second, found.third)
        }
    }

    /**
     * Écran contre l'oreille → écran éteint (évite les touches avec la joue).
     * Seulement quand l'appel passe par l'écouteur (pas en haut-parleur ni en Bluetooth).
     */
    @Suppress("DEPRECATION")
    private fun updateProximity(calls: List<CallInfo>, audio: CallAudioState?) {
        val inCall = calls.any { it.isActive || it.isDialing }
        val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
        val earpiece = route == CallAudioState.ROUTE_EARPIECE || route == CallAudioState.ROUTE_WIRED_HEADSET
        if (inCall && earpiece) acquireProximity() else releaseProximity()
    }

    private fun acquireProximity() {
        if (proximity?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "fern:proximite").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseProximity() {
        proximity?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
        proximity = null
    }

    companion object {
        fun openCallScreen(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
    }
}
