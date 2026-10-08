package com.atelierjlg.fern.messages.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.messages.MessagesViewModel
import com.atelierjlg.fern.messages.Screen
import com.atelierjlg.fern.messages.data.Conversation
import com.atelierjlg.fern.messages.data.formatListTime
import com.atelierjlg.fern.ui.kit.Avatar
import com.atelierjlg.fern.ui.kit.IconButtonRound
import com.atelierjlg.fern.ui.kit.Pill
import com.atelierjlg.fern.ui.kit.TopBar
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.launch

/**
 * La liste des conversations (ou des archives). Appui long = sélection :
 * archiver, marquer comme lu, bloquer, supprimer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(vm: MessagesViewModel, archived: Boolean) {
    val c = Fern.colors
    val all by vm.conversations.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val setup by vm.setup.collectAsState()
    val shown = remember(all, prefs.archived, archived) { all.filter { (it.threadId in prefs.archived) == archived } }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            when {
                selection.isNotEmpty() -> SelectionBar(
                    count = selection.size,
                    archived = archived,
                    onClose = { selection = emptySet() },
                    onArchive = { vm.setArchived(selection, !archived); selection = emptySet() },
                    onRead = { vm.markThreadsRead(selection); selection = emptySet() },
                    onBlock = {
                        val numbers = shown.filter { it.threadId in selection }.flatMap { it.addresses }
                        scope.launch { vm.block(numbers) }
                        selection = emptySet()
                    },
                    onDelete = { confirmDelete = true },
                )
                archived -> TopBar(onBack = { vm.back() }) {
                    Text("ARCHIVES", style = Fern.type.libelle, color = c.lichen, modifier = Modifier.padding(end = 16.dp))
                }
                else -> Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Messages", style = Fern.type.titreWidget, color = c.creme, modifier = Modifier.weight(1f))
                    IconButtonRound(FernIcons.Search, c.lichen) { vm.open(Screen.Search) }
                    IconButtonRound(FernIcons.Clock, c.lichen) { vm.open(Screen.Scheduled) }
                    IconButtonRound(FernIcons.Archive, c.lichen) { vm.open(Screen.Archived) }
                }
            }
            if (!archived && !setup.complete) SetupCard(vm, setup)
            if (shown.isEmpty()) {
                Text(
                    if (archived) "Aucune conversation archivée." else "Aucune conversation.",
                    style = Fern.type.corps, color = c.lichen, modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
                items(shown, key = { it.threadId }) { conv ->
                    val selected = conv.threadId in selection
                    ConversationRow(
                        conv,
                        draft = prefs.drafts[conv.threadId],
                        selected = selected,
                        onClick = {
                            if (selection.isNotEmpty()) {
                                selection = if (selected) selection - conv.threadId else selection + conv.threadId
                            } else {
                                vm.open(Screen.Thread(conv.threadId))
                            }
                        },
                        onLongClick = { selection = selection + conv.threadId },
                    )
                }
            }
        }
        if (!archived && selection.isEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(c.pistache)
                    .clickable { vm.open(Screen.Compose()) },
                contentAlignment = Alignment.Center,
            ) { FernIcon(FernIcons.Edit, c.nuit, size = 26.dp) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.mousse,
            title = {
                Text(
                    if (selection.size > 1) "Supprimer ${selection.size} conversations ?" else "Supprimer la conversation ?",
                    style = Fern.type.corps, color = c.creme,
                )
            },
            text = { Text("Tous les messages (SMS et MMS) seront effacés du téléphone. C'est définitif.", style = Fern.type.nomApp, color = c.lichen) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteThreads(selection)
                    selection = emptySet()
                    confirmDelete = false
                }) { Text("Supprimer", color = c.roseCarmin) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler", color = c.lichen) } },
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    archived: Boolean,
    onClose: () -> Unit,
    onArchive: () -> Unit,
    onRead: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Fern.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonRound(FernIcons.Close, c.creme, onClose)
        Text("$count", style = Fern.type.titreWidget, color = c.creme, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        IconButtonRound(FernIcons.Check, c.lichen, onRead)
        IconButtonRound(FernIcons.Archive, if (archived) c.pistache else c.lichen, onArchive)
        IconButtonRound(FernIcons.Block, c.lichen, onBlock)
        IconButtonRound(FernIcons.Trash, c.roseCarmin, onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(conv: Conversation, draft: String?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Fern.colors
    val unread = conv.unread > 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) c.lierre else c.nuit)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(conv.name, conv.photoUri, 48.dp)
            if (selected) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(c.pistache),
                    contentAlignment = Alignment.Center,
                ) { FernIcon(FernIcons.Check, c.nuit) }
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conv.name,
                    style = Fern.type.corps.copy(fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.SemiBold),
                    color = c.creme,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatListTime(conv.date), style = Fern.type.nomApp, color = if (unread) c.pistache else c.moussePale)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = if (!draft.isNullOrBlank()) "Brouillon : $draft" else conv.snippet
                Text(
                    preview.replace('\n', ' '),
                    style = Fern.type.nomApp,
                    color = when {
                        !draft.isNullOrBlank() -> c.roseCarmin
                        unread -> c.creme
                        else -> c.lichen
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Box(
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(c.pistache).padding(horizontal = 7.dp, vertical = 1.dp),
                    ) { Text("${conv.unread}", style = Fern.type.libelle, color = c.nuit) }
                }
            }
        }
    }
}

/** Petite pilule de filtre réutilisée par la recherche. */
@Composable
fun FilterRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label -> Pill(label, selected = i == selected) { onSelect(i) } }
    }
}
