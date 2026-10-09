package com.atelierjlg.fern.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.messages.MessagesViewModel
import com.atelierjlg.fern.messages.Screen
import com.atelierjlg.fern.ui.kit.Pill
import com.atelierjlg.fern.ui.kit.SectionLabel
import com.atelierjlg.fern.ui.kit.TopBar
import com.atelierjlg.fern.ui.theme.Fern

/** Les réglages de Fern Messages. */
@Composable
fun MessagesSettingsScreen(vm: MessagesViewModel) {
    val c = Fern.colors
    val prefs by vm.prefs.collectAsState()
    val setup by vm.setup.collectAsState()
    val pending by vm.pendingMms.collectAsState()
    val blocked by vm.blocked.collectAsState()
    var mine by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) {
        vm.loadBlocked()
        mine = vm.myNumbers()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = { vm.back() }) {
            Text("RÉGLAGES", style = Fern.type.libelle, color = c.lichen, modifier = Modifier.padding(end = 16.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!setup.complete) SetupCard(vm, setup)

            Card("Notifications") {
                Toggle(
                    "Masquer le texte sur l'écran verrouillé",
                    "Le téléphone verrouillé affiche seulement « Nouveau message » (pratique pour les codes).",
                    prefs.hideOnLockscreen, vm::setHideOnLockscreen,
                )
                Text(
                    "Son, vibreur, pastille : réglages d'Android. Par conversation : menu ⋮ dans la conversation.",
                    style = Fern.type.nomApp, color = c.lichen,
                )
                Pill("Sons et vibreur", false) { vm.start(vm.notificationSettingsIntent()) }
            }

            Card("Envoi") {
                Toggle(
                    "Accusés de remise",
                    "« Remis » sous le message quand le téléphone d'en face l'a reçu.",
                    prefs.deliveryReports, vm::setDeliveryReports,
                )
                Toggle(
                    "Plusieurs destinataires = groupe",
                    "Activé : une conversation de groupe (MMS), tout le monde voit les réponses. Désactivé : un SMS à chacun.",
                    prefs.groupAsMms, vm::setGroupAsMms,
                )
            }

            Card("MMS") {
                Text(
                    if (pending == 0) "Aucun MMS en attente de téléchargement."
                    else if (pending == 1) "1 MMS en attente de téléchargement." else "$pending MMS en attente de téléchargement.",
                    style = Fern.type.corps, color = c.creme,
                )
                Text("Il faut les données mobiles activées (même en Wi-Fi).", style = Fern.type.nomApp, color = c.lichen)
                if (pending > 0) Pill("Réessayer", true) { vm.retryMms(force = true) }
            }

            Card("Programmés et archives") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Programmés (${prefs.scheduled.size})", false) { vm.open(Screen.Scheduled) }
                    Pill("Archives (${prefs.archived.size})", false) { vm.open(Screen.Archived) }
                }
                if (!vm.canScheduleExact()) {
                    Text("Les alarmes exactes ne sont pas autorisées : un message programmé peut partir avec quelques minutes de retard.",
                        style = Fern.type.nomApp, color = c.roseCarmin)
                    Pill("Autoriser", true) { vm.exactAlarmIntent()?.let(vm::start) }
                }
            }

            Card("Numéros bloqués") {
                if (blocked.isEmpty()) {
                    Text("Aucun. Pour bloquer : menu ⋮ d'une conversation, ou appui long dans la liste.", style = Fern.type.nomApp, color = c.lichen)
                }
                blocked.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(PhoneNumbers.format(n), style = Fern.type.corps, color = c.creme, modifier = Modifier.weight(1f))
                        Pill("Débloquer", false) { vm.unblock(n) }
                    }
                }
                Text("Cette liste est celle d'Android : la même que dans Fern Contact.", style = Fern.type.nomApp, color = c.moussePale)
            }

            Card("Mes numéros") {
                Text(
                    if (mine.isEmpty()) "Inconnus. Remplis ta fiche « Moi » dans Fern Contact : ça évite que les MMS de groupe se coupent en deux."
                    else mine.joinToString(" · ") { PhoneNumbers.format(it) },
                    style = if (mine.isEmpty()) Fern.type.nomApp else Fern.type.corps,
                    color = if (mine.isEmpty()) c.lichen else c.creme,
                )
                Pill("Ma fiche dans Fern Contact", false) { vm.openMyCard() }
            }

            Text("Fern Messages v${vm.versionName()}", style = Fern.type.nomApp, color = c.moussePale, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Fern.colors.mousse).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(title, Modifier.padding(top = 0.dp))
        content()
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Fern.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = Fern.type.corps, color = c.creme)
            Text(detail, style = Fern.type.nomApp, color = c.lichen)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.pistache, checkedThumbColor = c.nuit, uncheckedTrackColor = c.lierre),
        )
    }
}
