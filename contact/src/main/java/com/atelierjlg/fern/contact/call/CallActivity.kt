package com.atelierjlg.fern.contact.call

import android.content.Intent
import android.os.Bundle
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.contact.MainActivity
import com.atelierjlg.fern.theme.FernSharedTheme
import com.atelierjlg.fern.ui.kit.*
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.delay

/**
 * L'écran d'appel (entrant, en cours, choix de SIM). Il s'affiche par-dessus l'écran
 * verrouillé et allume l'écran, comme l'appli Téléphone d'origine.
 */
class CallActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAction(intent)
    }

    /** « Répondre » depuis la notification. */
    private fun handleAction(intent: Intent?) {
        if (intent?.action == CallNotifications.ACTION_ANSWER) {
            CallManager.calls.value.firstOrNull { it.isRinging }?.let { CallManager.answer(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        handleAction(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            FernSharedTheme {
                val calls by CallManager.calls.collectAsState()
                val audio by CallManager.audio.collectAsState()
                // Plus aucun appel : « Appel terminé » un instant, puis on ferme.
                LaunchedEffect(calls.isEmpty()) {
                    if (calls.isEmpty()) {
                        delay(1200)
                        finish()
                    }
                }
                CallScreen(calls, audio, onAddCall = {
                    startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DIALPAD, true))
                })
            }
        }
    }
}

@Composable
private fun CallScreen(calls: List<CallInfo>, audio: CallAudioState?, onAddCall: () -> Unit) {
    val c = Fern.colors
    val ringing = calls.firstOrNull { it.isRinging }
    val selecting = calls.firstOrNull { it.isSelectingSim }
    val main = ringing ?: selecting ?: calls.firstOrNull { it.isActive } ?: calls.firstOrNull { !it.isEnded } ?: calls.firstOrNull()
    val others = calls.filter { it !== main && !it.isEnded }
    var keypad by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.nuit)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // L'autre appel (en attente) : bandeau en haut.
        others.forEach { other -> OtherCallBanner(other) }
        Spacer(Modifier.height(if (others.isEmpty()) 48.dp else 16.dp))

        if (main == null) {
            Spacer(Modifier.weight(1f))
            Text("Appel terminé", style = Fern.type.titreWidget, color = c.creme)
            Spacer(Modifier.weight(1f))
            return@Column
        }

        Text(statusText(main), style = Fern.type.libelle, color = c.lichen)
        Spacer(Modifier.height(20.dp))
        PulsingAvatar(main.name, main.photoUri, ringing = main.isRinging)
        Spacer(Modifier.height(20.dp))
        Text(
            if (main.name.isBlank()) PhoneNumbers.format(main.title) else main.title,
            style = Fern.type.titreWidget,
            color = c.creme,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (main.name.isNotBlank() && main.number.isNotBlank()) {
            Text(PhoneNumbers.format(main.number), style = Fern.type.corps, color = c.lichen)
        }
        Spacer(Modifier.weight(1f))

        when {
            main.isRinging -> IncomingControls(main, hasOtherCall = others.any { it.isActive || it.isHeld })
            main.isSelectingSim -> SimChooser(main)
            keypad -> DtmfPad(main, onClose = { keypad = false })
            else -> InCallControls(main, others, audio, onKeypad = { keypad = true }, onAddCall = onAddCall)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** La photo de l'appelant ; quand ça sonne, des ondes s'en échappent (comme un caillou dans l'eau). */
@Composable
private fun PulsingAvatar(name: String, photo: String?, ringing: Boolean) {
    val c = Fern.colors
    val waves = rememberInfiniteTransition(label = "ondes")
    val t by waves.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "onde")
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        if (ringing) {
            listOf(0f, 0.5f).forEach { delay ->
                val p = (t + delay) % 1f
                Box(
                    Modifier
                        .size(112.dp)
                        .graphicsLayer(scaleX = 1f + p * 0.6f, scaleY = 1f + p * 0.6f, alpha = (1f - p) * 0.5f)
                        .background(c.pistache, CircleShape),
                )
            }
        }
        Avatar(name, photo, 112.dp)
    }
}

private fun statusText(call: CallInfo): String = when {
    call.isRinging -> "APPEL ENTRANT"
    call.isSelectingSim -> "AVEC QUELLE SIM ?"
    call.isDialing -> "APPEL EN COURS…"
    call.isHeld -> "EN ATTENTE"
    call.isEnded -> "APPEL TERMINÉ"
    call.isConference -> "CONFÉRENCE"
    else -> "EN COMMUNICATION"
}

/** Chrono « 02:14 » d'un appel en cours. */
@Composable
private fun Chrono(call: CallInfo) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.connectTimeMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    if (!call.isActive || call.connectTimeMillis <= 0) return
    val s = ((now - call.connectTimeMillis) / 1000).coerceAtLeast(0)
    val text = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    Text(text, style = Fern.type.corps.copy(fontSize = 20.sp), color = Fern.colors.pistache)
}

@Composable
private fun OtherCallBanner(call: CallInfo) {
    val c = Fern.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.mousse)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(call.name, call.photoUri, 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(call.title, style = Fern.type.corps, color = c.creme, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    call.isHeld -> "En attente"
                    call.isRinging -> "Appel entrant"
                    else -> "En cours"
                },
                style = Fern.type.nomApp, color = c.lichen,
            )
        }
        if (call.isHeld) {
            Text(
                "REPRENDRE",
                style = Fern.type.libelle,
                color = c.pistache,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { CallManager.swap() }.padding(10.dp),
            )
        }
    }
}

@Composable
private fun IncomingControls(call: CallInfo, hasOtherCall: Boolean) {
    val c = Fern.colors
    if (hasOtherCall) {
        // Double appel : on choisit quoi faire de l'appel en cours.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(FernIcons.PhoneOff, "Refuser", c.carmin, c.creme) { CallManager.reject(call) }
            RoundAction(FernIcons.Pause, "Mettre en\nattente", c.lierre, c.creme) { CallManager.answer(call) }
            RoundAction(FernIcons.Phone, "Raccrocher\net répondre", c.pistache, c.nuit) { CallManager.endAndAnswer(call) }
        }
    } else {
        // Refuser avec un message (envoyé par l'appli SMS : Fern Messages).
        val context = LocalContext.current
        val replies = remember { com.atelierjlg.fern.contact.data.ContactPrefs.quickReplies(context) }
        var showReplies by remember { mutableStateOf(false) }
        if (showReplies && call.number.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                replies.forEach { r ->
                    Text(
                        r, style = Fern.type.corps, color = c.creme,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.mousse)
                            .clickable { CallManager.reject(call, r) }.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            RoundAction(FernIcons.PhoneOff, "Refuser", c.carmin, c.creme, size = 76.dp) { CallManager.reject(call) }
            if (call.number.isNotBlank()) {
                RoundAction(FernIcons.Message, "Message", if (showReplies) c.pistache else c.mousse, if (showReplies) c.nuit else c.creme) {
                    showReplies = !showReplies
                }
            }
            // « Répondre » respire doucement pour attirer l'œil.
            val breath = rememberInfiniteTransition(label = "respire")
            val s by breath.animateFloat(1f, 1.08f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "taille")
            RoundAction(FernIcons.Phone, "Répondre", c.pistache, c.nuit, Modifier.graphicsLayer(scaleX = s, scaleY = s), size = 76.dp) {
                CallManager.answer(call)
            }
        }
    }
}

@Composable
private fun SimChooser(call: CallInfo) {
    val c = Fern.colors
    val context = LocalContext.current
    val telecom = remember { context.getSystemService(TelecomManager::class.java) }
    var keep by remember { mutableStateOf(false) }
    val accounts: List<PhoneAccountHandle> = call.accountsToChoose.ifEmpty {
        runCatching { telecom.callCapablePhoneAccounts }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        accounts.forEachIndexed { i, handle ->
            val label = runCatching { telecom.getPhoneAccount(handle)?.label?.toString() }.getOrNull() ?: "SIM ${i + 1}"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.mousse)
                    .clickable { CallManager.selectAccount(call, handle, keep) }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(c.pistache), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = Fern.type.libelle, color = c.nuit)
                }
                Text(label, style = Fern.type.corps, color = c.creme, modifier = Modifier.padding(start = 14.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Toujours utiliser cette SIM", style = Fern.type.corps, color = c.lichen, modifier = Modifier.weight(1f))
            Switch(
                checked = keep, onCheckedChange = { keep = it },
                colors = SwitchDefaults.colors(checkedTrackColor = c.pistache, checkedThumbColor = c.nuit),
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RoundAction(FernIcons.PhoneOff, "Annuler", c.carmin, c.creme) { CallManager.hangUp(call) }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InCallControls(
    call: CallInfo,
    others: List<CallInfo>,
    audio: CallAudioState?,
    onKeypad: () -> Unit,
    onAddCall: () -> Unit,
) {
    val c = Fern.colors
    var routes by remember { mutableStateOf(false) }
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val mask = audio?.supportedRouteMask ?: 0
    val hasBluetooth = mask and CallAudioState.ROUTE_BLUETOOTH != 0
    val routeIcon = when (route) {
        CallAudioState.ROUTE_SPEAKER -> FernIcons.Speaker
        CallAudioState.ROUTE_BLUETOOTH -> FernIcons.Bluetooth
        CallAudioState.ROUTE_WIRED_HEADSET -> FernIcons.Headphones
        else -> FernIcons.Speaker
    }
    val routeOn = route != CallAudioState.ROUTE_EARPIECE && route != CallAudioState.ROUTE_WIRED_HEADSET

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Chrono(call)
        Spacer(Modifier.height(24.dp))
        if (routes) {
            RouteChooser(mask, route, onDone = { routes = false })
            Spacer(Modifier.height(16.dp))
        }
        val held = others.any { it.isHeld }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Toggle(if (muted) FernIcons.MicOff else FernIcons.Mic, "Muet", muted) { CallManager.setMuted(!muted) }
            Toggle(FernIcons.Dialpad, "Clavier", false, onKeypad)
            Toggle(routeIcon, if (hasBluetooth) "Son" else "Haut-parleur", routeOn) {
                if (hasBluetooth) {
                    routes = !routes
                } else {
                    CallManager.setRoute(if (routeOn) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Toggle(FernIcons.Pause, if (call.isHeld) "Reprendre" else "Attente", call.isHeld, enabled = call.canHold) {
                CallManager.toggleHold(call)
            }
            if (held) {
                Toggle(FernIcons.Swap, "Basculer", false) { CallManager.swap() }
                Toggle(FernIcons.Merge, "Fusionner", false, enabled = call.canMerge) { CallManager.merge() }
            } else {
                Toggle(FernIcons.Plus, "Ajouter", false, onAddCall)
                Spacer(Modifier.width(64.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        RoundAction(FernIcons.PhoneOff, "", c.carmin, c.creme, size = 76.dp) { CallManager.hangUp(call) }
    }
}

@Composable
private fun Toggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) = Toggle(icon, label, on, enabled = true, onClick = onClick)

@Composable
private fun Toggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = Fern.colors
    RoundAction(icon, label, if (on) c.pistache else c.mousse, if (on) c.nuit else c.creme, enabled = enabled, onClick = onClick)
}

@Suppress("DEPRECATION")
@Composable
private fun RouteChooser(mask: Int, current: Int, onDone: () -> Unit) {
    val options = listOf(
        CallAudioState.ROUTE_EARPIECE to "Téléphone",
        CallAudioState.ROUTE_SPEAKER to "Haut-parleur",
        CallAudioState.ROUTE_BLUETOOTH to "Bluetooth",
        CallAudioState.ROUTE_WIRED_HEADSET to "Casque",
    ).filter { mask and it.first != 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        options.forEach { (route, label) ->
            Pill(label, selected = route == current) {
                CallManager.setRoute(route)
                onDone()
            }
        }
    }
}

/** Clavier pendant l'appel (serveurs vocaux : « tapez 1 »…). */
@Composable
private fun DtmfPad(call: CallInfo, onClose: () -> Unit) {
    val c = Fern.colors
    var typed by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(typed.ifEmpty { " " }, style = Fern.type.titreWidget, color = c.creme, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { d ->
                    Box(
                        Modifier
                            .padding(6.dp)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(c.mousse)
                            .clickable {
                                typed += d
                                CallManager.dtmf(call, d)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.toString(), style = Fern.type.titreWidget.copy(fontSize = 28.sp), color = c.creme)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(FernIcons.Close, "Fermer", c.mousse, c.creme, onClick = onClose)
            RoundAction(FernIcons.PhoneOff, "Raccrocher", c.carmin, c.creme) { CallManager.hangUp(call) }
        }
    }
}
