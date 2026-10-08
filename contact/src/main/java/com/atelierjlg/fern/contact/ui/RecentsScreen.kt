package com.atelierjlg.fern.contact.ui

import android.provider.CallLog.Calls
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.contact.ContactViewModel
import com.atelierjlg.fern.contact.Screen
import com.atelierjlg.fern.contact.data.ContactSummary
import com.atelierjlg.fern.contact.data.RecentGroup
import com.atelierjlg.fern.contact.data.formatCallTime
import com.atelierjlg.fern.contact.data.formatDuration
import com.atelierjlg.fern.contact.data.groupRecents
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.launch

/** Journal d'appels : Tous / Manqués, appels regroupés, toucher = la fiche, bouton = rappeler. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(vm: ContactViewModel) {
    val c = Fern.colors
    val recents by vm.recents.collectAsState()
    val contacts by vm.contacts.collectAsState()
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<RecentGroup?>(null) }
    val groups = remember(recents, missedOnly) {
        groupRecents(if (missedOnly) recents.filter { it.type == Calls.MISSED_TYPE } else recents)
    }
    // Numéro → contact, pour afficher les noms à jour (et pas ceux gardés par le journal).
    val byKey = remember(contacts) {
        buildMap<String, ContactSummary> { contacts.forEach { ct -> ct.phones.forEach { put(PhoneNumbers.key(it), ct) } } }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Tous", !missedOnly) { missedOnly = false }
            Pill("Manqués", missedOnly) { missedOnly = true }
        }
        if (groups.isEmpty()) {
            Text(if (missedOnly) "Aucun appel manqué." else "Aucun appel.", style = Fern.type.corps, color = c.lichen, modifier = Modifier.padding(top = 16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(groups, key = { it.latest.id }) { group ->
                val contact = contactFor(byKey, group.number)
                RecentRow(
                    group, contact,
                    onClick = { if (contact != null) vm.open(Screen.Detail(contact.id)) else selected = group },
                    onLongClick = { selected = group },
                    onCall = { vm.call(group.number) },
                )
            }
        }
    }

    selected?.let { group ->
        val contact = contactFor(byKey, group.number)
        RecentActions(vm, group, contact, onDismiss = { selected = null })
    }
}

private fun contactFor(byKey: Map<String, ContactSummary>, number: String): ContactSummary? =
    byKey[PhoneNumbers.key(number)]?.takeIf { ct -> ct.phones.any { PhoneNumbers.same(it, number) } }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(group: RecentGroup, contact: ContactSummary?, onClick: () -> Unit, onLongClick: () -> Unit, onCall: () -> Unit) {
    val c = Fern.colors
    val e = group.latest
    val (icon, tint) = when (e.type) {
        Calls.MISSED_TYPE -> FernIcons.PhoneMissed to c.roseCarmin
        Calls.OUTGOING_TYPE -> FernIcons.PhoneOutgoing to c.lichen
        Calls.REJECTED_TYPE, Calls.BLOCKED_TYPE -> FernIcons.PhoneOff to c.moussePale
        else -> FernIcons.PhoneIncoming to c.lichen
    }
    val name = contact?.name ?: e.cachedName?.takeIf { it.isNotBlank() } ?: PhoneNumbers.format(e.number).ifBlank { "Numéro masqué" }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact?.name ?: "", contact?.photoUri, 44.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                name + if (group.count > 1) "  (${group.count})" else "",
                style = Fern.type.corps,
                color = if (group.isMissed) c.roseCarmin else c.creme,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FernIcon(icon, tint, size = 14.dp)
                val duration = formatDuration(e.durationSec)
                Text(
                    "  " + formatCallTime(e.date) + if (duration.isNotEmpty()) " · $duration" else "",
                    style = Fern.type.nomApp, color = c.lichen, maxLines = 1,
                )
            }
        }
        if (e.number.isNotBlank()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onCall),
                contentAlignment = Alignment.Center,
            ) { FernIcon(FernIcons.Phone, c.pistache) }
        }
    }
}

/** Les actions sur un appel du journal (numéro inconnu ou appui long). */
@Composable
private fun RecentActions(vm: ContactViewModel, group: RecentGroup, contact: ContactSummary?, onDismiss: () -> Unit) {
    val c = Fern.colors
    val scope = rememberCoroutineScope()
    val number = group.number
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.mousse,
        title = { Text(contact?.name ?: PhoneNumbers.format(number).ifBlank { "Numéro masqué" }, style = Fern.type.corps, color = c.creme) },
        text = {
            Column {
                if (number.isNotBlank()) {
                    ActionRow(FernIcons.Phone, "Appeler") { onDismiss(); vm.call(number) }
                    ActionRow(FernIcons.Message, "Envoyer un message") { onDismiss(); vm.message(number) }
                    if (contact == null) ActionRow(FernIcons.UserPlus, "Créer un contact") { onDismiss(); vm.open(Screen.Edit(null, number)) }
                    ActionRow(FernIcons.Copy, "Copier le numéro") {
                        onDismiss()
                        val cm = vm.getApplication<android.app.Application>().getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Numéro", number))
                    }
                    if (vm.canBlock()) {
                        ActionRow(FernIcons.Block, "Bloquer ce numéro", tint = c.roseCarmin) {
                            onDismiss()
                            scope.launch { vm.setBlocked(listOf(number), true); vm.toast("Numéro bloqué") }
                        }
                    }
                }
                ActionRow(FernIcons.Trash, if (group.count > 1) "Effacer ces ${group.count} appels" else "Effacer du journal", tint = c.roseCarmin) {
                    onDismiss()
                    vm.deleteCalls(group.entries.map { it.id })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = c.lichen) } },
    )
}
