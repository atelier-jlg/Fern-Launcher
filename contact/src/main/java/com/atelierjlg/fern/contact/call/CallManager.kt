package com.atelierjlg.fern.contact.call

import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.VideoProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Ce qu'on affiche d'un appel. */
data class CallInfo(
    val call: Call,
    val state: Int,
    val number: String,
    /** Nom fourni par le réseau ou par les contacts (peut être vide). */
    val name: String,
    val photoUri: String?,
    val connectTimeMillis: Long,
    val canHold: Boolean,
    val canMerge: Boolean,
    val isConference: Boolean,
    /** Choix de la SIM en attente (double SIM sans SIM par défaut). */
    val accountsToChoose: List<PhoneAccountHandle>,
) {
    val isRinging get() = state == Call.STATE_RINGING
    val isActive get() = state == Call.STATE_ACTIVE
    val isHeld get() = state == Call.STATE_HOLDING
    val isDialing get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_NEW
    val isSelectingSim get() = state == Call.STATE_SELECT_PHONE_ACCOUNT
    val isEnded get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
    val title get() = name.ifBlank { number.ifBlank { "Numéro masqué" } }
}

/**
 * Le « tableau de bord » des appels en cours, partagé entre le service d'appel (côté système)
 * et l'écran d'appel. Contrairement à l'ancienne version, il garde TOUS les appels :
 * un deuxième appel n'efface plus le premier.
 */
object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var service: InCallService? = null
        internal set

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio.asStateFlow()

    /** Noms et photos trouvés dans les contacts, par numéro. */
    private val contacts = HashMap<String, Pair<String, String?>>()

    private val tracked = mutableListOf<Call>()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = refresh()
        override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = refresh()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
    }

    internal fun add(call: Call) {
        if (call !in tracked) {
            tracked += call
            call.registerCallback(callback)
        }
        refresh()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        tracked -= call
        refresh()
    }

    /** Le service d'appel s'arrête : on oublie tout (pas d'appel « fantôme » la fois suivante). */
    internal fun clear() {
        tracked.forEach { runCatching { it.unregisterCallback(callback) } }
        tracked.clear()
        contacts.clear()
        refresh()
    }

    internal fun setAudio(state: CallAudioState?) {
        _audio.value = state
    }

    internal fun rememberContact(number: String, name: String, photo: String?) {
        contacts[number] = name to photo
        refresh()
    }

    fun refresh() {
        // Les appels « enfants » d'une conférence sont montrés dans leur parent.
        _calls.value = tracked.filter { it.parent == null }.map { it.toInfo() }
    }

    private fun Call.toInfo(): CallInfo {
        val d = details
        val number = d?.handle?.schemeSpecificPart.orEmpty()
        val known = contacts[number]
        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d?.contactDisplayName else null
        val accounts = if (state == Call.STATE_SELECT_PHONE_ACCOUNT) availableAccounts() else emptyList()
        return CallInfo(
            call = this,
            state = state,
            number = number,
            name = known?.first ?: network ?: d?.callerDisplayName.orEmpty(),
            photoUri = known?.second,
            connectTimeMillis = d?.connectTimeMillis ?: 0L,
            canHold = d?.can(Call.Details.CAPABILITY_HOLD) == true,
            canMerge = d?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true || conferenceableCalls.isNotEmpty(),
            isConference = d?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true,
            accountsToChoose = accounts,
        )
    }

    @Suppress("DEPRECATION")
    private fun Call.availableAccounts(): List<PhoneAccountHandle> {
        val fromCall = details?.intentExtras?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)
            ?: details?.extras?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)
        return fromCall.orEmpty()
    }

    // ─── Commandes (depuis l'écran d'appel ou la notification) ───────────────

    fun answer(info: CallInfo) = info.call.answer(VideoProfile.STATE_AUDIO_ONLY)

    /** Refuser un appel qui sonne (avec un SMS de réponse, plus tard). */
    fun reject(info: CallInfo, message: String? = null) =
        if (message != null) info.call.reject(true, message) else info.call.reject(false, null)

    fun hangUp(info: CallInfo) = if (info.isRinging) reject(info) else info.call.disconnect()

    /** Raccroche l'appel en cours et répond à celui qui sonne. */
    fun endAndAnswer(ringing: CallInfo) {
        calls.value.filter { it.isActive || it.isHeld }.forEach { it.call.disconnect() }
        scope.launch {
            delay(400)
            ringing.call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    fun toggleHold(info: CallInfo) = if (info.isHeld) info.call.unhold() else info.call.hold()

    /** Basculer entre deux appels : reprendre celui en attente (le réseau met l'autre en attente). */
    fun swap() {
        calls.value.firstOrNull { it.isHeld }?.call?.unhold()
    }

    /** Réunir les deux appels en conférence. */
    fun merge() {
        val active = calls.value.firstOrNull { it.isActive } ?: return
        val other = active.call.conferenceableCalls.firstOrNull()
        if (other != null) active.call.conference(other) else active.call.mergeConference()
    }

    fun selectAccount(info: CallInfo, account: PhoneAccountHandle, remember: Boolean) =
        info.call.phoneAccountSelected(account, remember)

    fun setMuted(muted: Boolean) = service?.setMuted(muted)

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) = service?.setAudioRoute(route)

    /** Touche du clavier pendant l'appel (serveur vocal, code…). */
    fun dtmf(info: CallInfo, digit: Char) {
        info.call.playDtmfTone(digit)
        scope.launch {
            delay(160)
            info.call.stopDtmfTone()
        }
    }

    /** Petit utilitaire pour les appels passés avec une SIM choisie d'avance. */
    fun extrasFor(account: PhoneAccountHandle?): Bundle = Bundle().apply {
        if (account != null) putParcelable(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
    }
}
