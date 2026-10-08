package com.atelierjlg.fern.messages.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.messages.MessagesViewModel
import com.atelierjlg.fern.messages.data.Msg
import com.atelierjlg.fern.messages.data.MsgStatus
import com.atelierjlg.fern.messages.data.NotifyMode
import com.atelierjlg.fern.messages.data.Otp
import com.atelierjlg.fern.messages.data.dayOf
import com.atelierjlg.fern.messages.data.formatDayHeader
import com.atelierjlg.fern.messages.data.formatHour
import com.atelierjlg.fern.ui.kit.ActionRow
import com.atelierjlg.fern.ui.kit.Avatar
import com.atelierjlg.fern.ui.kit.IconButtonRound
import com.atelierjlg.fern.ui.kit.Pill
import com.atelierjlg.fern.ui.kit.rememberPhoto
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.launch

/** Une conversation : bulles, codes à copier, statut d'envoi, zone d'écriture (brouillon gardé). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadScreen(vm: MessagesViewModel, threadId: Long) {
    val c = Fern.colors
    val scope = rememberCoroutineScope()
    val version by vm.version.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var addresses by remember { mutableStateOf(emptyList<String>()) }
    var title by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(emptyList<Msg>()) }
    var names by remember { mutableStateOf(emptyMap<String, String>()) }
    var draft by remember { mutableStateOf(prefs.drafts[threadId].orEmpty()) }
    var menu by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Msg?>(null) }
    var scheduling by remember { mutableStateOf(false) }
    var confirmDeleteThread by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Relu à chaque changement dans la base (message reçu, envoyé, statut…).
    LaunchedEffect(threadId, version) {
        if (addresses.isEmpty()) {
            addresses = vm.addresses(threadId)
            val (n, p) = vm.display(addresses)
            title = n
            photo = p
            if (addresses.size > 1) names = addresses.associateWith { vm.contactName(it) ?: PhoneNumbers.format(it) }
        }
        messages = vm.messages(threadId)
        vm.markRead(threadId)
    }
    // En quittant : on garde le brouillon.
    val currentDraft by rememberUpdatedState(draft)
    DisposableEffect(threadId) { onDispose { vm.saveDraft(threadId, currentDraft) } }

    // Du plus récent (en bas) au plus ancien : la liste est « à l'envers ».
    val reversed = remember(messages) { messages.asReversed() }
    val lastOutgoing = remember(messages) { messages.lastOrNull { it.outgoing }?.key }

    Column(Modifier.fillMaxSize()) {
        // En-tête
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButtonRound(FernIcons.Back, c.creme) { vm.back() }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = addresses.size == 1) { vm.openContact(addresses.first()) }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(title, photo, 36.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(title, style = Fern.type.corps, color = c.creme, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (addresses.size == 1 && title != PhoneNumbers.format(addresses.first())) {
                        Text(PhoneNumbers.format(addresses.first()), style = Fern.type.nomApp, color = c.lichen)
                    }
                }
            }
            if (addresses.size == 1) IconButtonRound(FernIcons.Phone, c.pistache) { vm.call(addresses.first()) }
            IconButtonRound(FernIcons.More, c.lichen) { menu = true }
        }

        // Messages
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            reversed.forEachIndexed { i, m ->
                item(key = m.key) {
                    Bubble(
                        m,
                        senderName = if (addresses.size > 1 && !m.outgoing) names[m.address] ?: PhoneNumbers.format(m.address) else null,
                        showStatus = m.key == lastOutgoing || m.status == MsgStatus.Failed,
                        onLongClick = { actionsFor = m },
                        onRetry = { vm.resend(m) },
                        onCopyCode = { vm.copy(it) },
                    )
                }
                // Séparateur de jour au-dessus du premier message de chaque jour.
                val older = reversed.getOrNull(i + 1)
                if (older == null || dayOf(older.date) != dayOf(m.date)) {
                    item(key = "jour-${m.key}") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(formatDayHeader(dayOf(m.date)).uppercase(), style = Fern.type.libelle, color = c.moussePale)
                        }
                    }
                }
            }
        }

        // Zone d'écriture
        Composer(
            text = draft,
            onText = { draft = it },
            enabled = addresses.isNotEmpty(),
            onSend = {
                vm.send(addresses, draft)
                draft = ""
                vm.saveDraft(threadId, "")
                scope.launch { listState.animateScrollToItem(0) }
            },
            onSchedule = { scheduling = true },
        )
    }

    if (menu) {
        val mode = prefs.notify[threadId] ?: NotifyMode.Son
        AlertDialog(
            onDismissRequest = { menu = false },
            containerColor = c.mousse,
            title = { Text(title, style = Fern.type.corps, color = c.creme) },
            text = {
                Column {
                    Text("NOTIFICATIONS", style = Fern.type.libelle, color = c.lichen, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NotifyMode.entries.forEach { m ->
                            Pill(m.label, selected = m == mode) { vm.setNotifyMode(threadId, m) }
                        }
                    }
                    if (addresses.size == 1) {
                        ActionRow(FernIcons.User, "Fiche du contact") { menu = false; vm.openContact(addresses.first()) }
                    }
                    ActionRow(FernIcons.Archive, if (threadId in prefs.archived) "Désarchiver" else "Archiver") {
                        menu = false
                        vm.setArchived(setOf(threadId), threadId !in prefs.archived)
                    }
                    ActionRow(FernIcons.Block, "Bloquer", tint = c.roseCarmin) {
                        menu = false
                        scope.launch { vm.block(addresses) }
                    }
                    ActionRow(FernIcons.Trash, "Supprimer la conversation", tint = c.roseCarmin) {
                        menu = false
                        confirmDeleteThread = true
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("Fermer", color = c.lichen) } },
        )
    }

    actionsFor?.let { m ->
        var confirm by remember(m.key) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = c.mousse,
            title = { Text(if (confirm) "Supprimer ce message ?" else "Message", style = Fern.type.corps, color = c.creme) },
            text = {
                Column {
                    if (!confirm) {
                        if (m.body.isNotBlank()) ActionRow(FernIcons.Copy, "Copier le texte") { actionsFor = null; vm.copy(m.body) }
                        if (m.status == MsgStatus.Failed) ActionRow(FernIcons.Send, "Renvoyer") { actionsFor = null; vm.resend(m) }
                        ActionRow(FernIcons.Trash, "Supprimer", tint = c.roseCarmin) { confirm = true }
                    } else {
                        Text("Il sera effacé du téléphone. C'est définitif.", style = Fern.type.nomApp, color = c.lichen)
                    }
                }
            },
            confirmButton = {
                if (confirm) TextButton(onClick = { vm.delete(listOf(m)); actionsFor = null }) { Text("Supprimer", color = c.roseCarmin) }
                else TextButton(onClick = { actionsFor = null }) { Text("Fermer", color = c.lichen) }
            },
        )
    }

    if (confirmDeleteThread) {
        AlertDialog(
            onDismissRequest = { confirmDeleteThread = false },
            containerColor = c.mousse,
            title = { Text("Supprimer la conversation ?", style = Fern.type.corps, color = c.creme) },
            text = { Text("Tous les messages (SMS et MMS) seront effacés du téléphone. C'est définitif.", style = Fern.type.nomApp, color = c.lichen) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteThread = false
                    vm.deleteThreads(setOf(threadId))
                    vm.back()
                }) { Text("Supprimer", color = c.roseCarmin) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteThread = false }) { Text("Annuler", color = c.lichen) } },
        )
    }

    if (scheduling) {
        ScheduleDialog(
            onDismiss = { scheduling = false },
            onPick = { at ->
                scheduling = false
                vm.schedule(addresses, draft, at)
                draft = ""
                vm.saveDraft(threadId, "")
            },
            canExact = vm.canScheduleExact(),
            onAllowExact = { vm.exactAlarmIntent()?.let(vm::start) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: Msg,
    senderName: String?,
    showStatus: Boolean,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    onCopyCode: (String) -> Unit,
) {
    val c = Fern.colors
    val code = remember(m.key) { if (!m.outgoing) Otp.find(m.body) else null }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        if (senderName != null) Text(senderName, style = Fern.type.nomApp, color = c.lichen, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 22.dp, topEnd = 22.dp,
                        bottomStart = if (m.outgoing) 22.dp else 6.dp, bottomEnd = if (m.outgoing) 6.dp else 22.dp,
                    ),
                )
                .background(
                    when {
                        m.status == MsgStatus.Failed -> c.carmin
                        m.outgoing -> c.lierre
                        else -> c.mousse
                    },
                )
                .combinedClickable(onClick = { if (m.status == MsgStatus.Failed) onRetry() }, onLongClick = onLongClick)
                .padding(if (m.images.isNotEmpty()) 4.dp else 0.dp),
        ) {
            m.images.forEach { uri ->
                val bitmap = rememberPhoto(uri.toString(), 900)
                if (bitmap != null) {
                    Image(
                        bitmap, "Photo",
                        Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.size(200.dp, 140.dp).clip(RoundedCornerShape(18.dp)).background(c.sousBois))
                }
            }
            if (m.otherParts > 0) {
                Text("Pièce jointe (pas encore affichée)", style = Fern.type.nomApp, color = c.lichen, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            if (m.body.isNotBlank()) {
                SelectionContainer {
                    Text(m.body, style = Fern.type.corps, color = c.creme, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
        if (code != null) {
            Pill("Copier $code", selected = true, modifier = Modifier.padding(top = 6.dp)) { onCopyCode(code) }
        }
        if (showStatus) {
            val status = when (m.status) {
                MsgStatus.Sending -> "Envoi…"
                MsgStatus.Failed -> "Échec · touche pour renvoyer"
                MsgStatus.Delivered -> "Remis · ${formatHour(m.date)}"
                MsgStatus.Sent -> "Envoyé · ${formatHour(m.date)}"
                MsgStatus.Received -> formatHour(m.date)
            }
            Text(
                status, style = Fern.type.nomApp,
                color = if (m.status == MsgStatus.Failed) c.roseCarmin else c.moussePale,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** La zone d'écriture : toucher l'avion envoie, appui long = programmer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Composer(text: String, onText: (String) -> Unit, enabled: Boolean, onSend: () -> Unit, onSchedule: () -> Unit) {
    val c = Fern.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        com.atelierjlg.fern.ui.kit.FernField(
            text, onText, "Message", Modifier.weight(1f), singleLine = false,
        )
        val canSend = enabled && text.isNotBlank()
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) c.pistache else c.mousse)
                .combinedClickable(enabled = canSend, onClick = onSend, onLongClick = onSchedule),
            contentAlignment = Alignment.Center,
        ) { com.atelierjlg.fern.ui.theme.FernIcon(FernIcons.Send, if (canSend) c.nuit else c.moussePale, size = 20.dp) }
    }
    if (text.length > 140) {
        // Au-delà de 160 caractères (70 avec des accents rares ou des emojis), le SMS part en plusieurs morceaux.
        val parts = remember(text) {
            runCatching { android.telephony.SmsMessage.calculateLength(text, false) }.getOrNull()
        }
        if (parts != null) {
            Text(
                "${parts[0]} SMS · ${parts[2]} caractères restants",
                style = Fern.type.nomApp, color = c.moussePale, modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
            )
        }
    }
}
