package com.atelierjlg.fern.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.common.SearchText
import com.atelierjlg.fern.messages.MessagesViewModel
import com.atelierjlg.fern.messages.Recipient
import com.atelierjlg.fern.messages.Screen
import com.atelierjlg.fern.messages.data.Msg
import com.atelierjlg.fern.messages.data.formatListTime
import com.atelierjlg.fern.messages.data.formatScheduled
import com.atelierjlg.fern.messages.mms.MmsTransport.Attachment
import com.atelierjlg.fern.ui.kit.Avatar
import com.atelierjlg.fern.ui.kit.FernField
import com.atelierjlg.fern.ui.kit.IconButtonRound
import com.atelierjlg.fern.ui.kit.SectionLabel
import com.atelierjlg.fern.ui.kit.TopBar
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Nouveau message : un ou plusieurs destinataires (contacts ou numéros), puis le texte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(vm: MessagesViewModel, prefillNumber: String, prefillBody: String, prefillImage: String? = null, prefillVcard: String? = null) {
    val c = Fern.colors
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf(emptyList<Recipient>()) }
    var chosen by remember { mutableStateOf<List<Recipient>>(if (prefillNumber.isBlank()) emptyList() else prefillNumber.split(',', ';').map { Recipient("", it.trim(), null) }) }
    var query by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf(prefillBody) }
    var scheduling by remember { mutableStateOf(false) }
    var attachments by remember {
        mutableStateOf<List<Attachment>>(
            listOfNotNull(
                prefillImage?.let { Attachment.Photo(android.net.Uri.parse(it)) },
                prefillVcard?.let { Attachment.VcardFile(android.net.Uri.parse(it)) },
            ),
        )
    }
    LaunchedEffect(Unit) {
        all = vm.recipients()
        // Le numéro pré-rempli prend le nom du contact s'il est connu.
        chosen = chosen.map { r -> all.firstOrNull { PhoneNumbers.same(it.number, r.number) } ?: r }
    }
    val suggestions = remember(all, query) {
        if (query.isBlank()) emptyList() else all.filter {
            SearchText.matches(it.name, query) || PhoneNumbers.containsDigits(it.number, query)
        }.take(8)
    }

    /** Les destinataires, plus le numéro tapé s'il n'a pas été validé. */
    fun recipients(): List<String> =
        (chosen.map { it.number } + listOfNotNull(query.takeIf { q -> q.count(Char::isDigit) >= 3 })).distinct()

    fun sendNow(at: Long? = null) {
        val to = recipients()
        if (to.isEmpty() || (body.isBlank() && attachments.isEmpty())) return
        scope.launch {
            if (at != null) {
                vm.schedule(to, body, at, attachments)
                vm.back()
            } else {
                vm.send(to, body, attachments)
                // Le temps que le message soit écrit dans la base, puis on ouvre la conversation.
                delay(250)
                val threadId = vm.threadIdFor(to)
                vm.replaceTop(Screen.Thread(threadId))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = { vm.back() }) {
            Text("NOUVEAU MESSAGE", style = Fern.type.libelle, color = c.lichen, modifier = Modifier.padding(end = 16.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (chosen.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chosen.forEach { r ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(c.lierre).clickable { chosen = chosen - r }
                                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.name.ifBlank { PhoneNumbers.format(r.number) }, style = Fern.type.nomApp, color = c.creme)
                            Text("  ×", style = Fern.type.nomApp, color = c.lichen)
                        }
                    }
                }
            }
            FernField(
                query, { query = it }, if (chosen.isEmpty()) "À : nom ou numéro" else "Ajouter quelqu'un",
                Modifier.fillMaxWidth().padding(top = 8.dp), keyboardType = KeyboardType.Text, leading = FernIcons.User,
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            items(suggestions, key = { it.name + it.number }) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable {
                        chosen = chosen + r
                        query = ""
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(r.name, r.photoUri, 40.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(r.name, style = Fern.type.corps, color = c.creme, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PhoneNumbers.format(r.number), style = Fern.type.nomApp, color = c.lichen)
                    }
                }
            }
            if (query.count(Char::isDigit) >= 3 && suggestions.isEmpty()) {
                item {
                    Text(
                        "Envoyer au ${PhoneNumbers.format(query)}",
                        style = Fern.type.corps, color = c.pistache,
                        modifier = Modifier.clickable {
                            chosen = chosen + Recipient("", query.trim(), null)
                            query = ""
                        }.padding(vertical = 10.dp),
                    )
                }
            }
        }
        if (recipients().size > 1) {
            Text(
                "Conversation de groupe : envoyé en MMS à tout le monde.",
                style = Fern.type.nomApp, color = c.moussePale, modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Composer(
            body, { body = it }, enabled = recipients().isNotEmpty(), onSend = { sendNow() }, onSchedule = { scheduling = true },
            attachments = attachments, onAttachmentsChange = { attachments = it },
        )
    }

    if (scheduling) {
        ScheduleDialog(
            onDismiss = { scheduling = false },
            onPick = { scheduling = false; sendNow(it) },
            canExact = vm.canScheduleExact(),
            onAllowExact = { vm.exactAlarmIntent()?.let(vm::start) },
        )
    }
}

/** Recherche dans tous les messages (sans accents) et dans les noms des conversations. */
@Composable
fun SearchScreen(vm: MessagesViewModel) {
    val c = Fern.colors
    val conversations by vm.conversations.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Msg>()) }
    LaunchedEffect(query) {
        delay(250)
        results = if (query.length >= 2) vm.search(query) else emptyList()
    }
    val matchingConvs = remember(conversations, query) {
        if (query.isBlank()) emptyList() else conversations.filter {
            SearchText.matches(it.name, query) || it.addresses.any { a -> PhoneNumbers.containsDigits(a, query) }
        }
    }
    val names = remember(conversations) { conversations.associate { it.threadId to it.name } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButtonRound(FernIcons.Back, c.creme) { vm.back() }
            FernField(query, { query = it }, "Rechercher", Modifier.weight(1f).padding(end = 12.dp), leading = FernIcons.Search)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (matchingConvs.isNotEmpty()) {
                item { SectionLabel("Conversations") }
                items(matchingConvs, key = { "c${it.threadId}" }) { conv ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { vm.open(Screen.Thread(conv.threadId)) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(conv.name, conv.photoUri, 40.dp)
                        Text(conv.name, style = Fern.type.corps, color = c.creme, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (results.isNotEmpty()) {
                item { SectionLabel("Messages") }
                items(results, key = { it.key }) { m ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { vm.open(Screen.Thread(m.threadId)) }.padding(vertical = 8.dp),
                    ) {
                        Row {
                            Text(
                                names[m.threadId] ?: PhoneNumbers.format(m.address), style = Fern.type.nomApp, color = c.lichen,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatListTime(m.date), style = Fern.type.nomApp, color = c.moussePale)
                        }
                        Text(m.body, style = Fern.type.corps, color = c.creme, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (query.length >= 2 && results.isEmpty() && matchingConvs.isEmpty()) {
                item { Text("Rien trouvé.", style = Fern.type.corps, color = c.lichen, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }
}

/** Les messages programmés, avec possibilité d'annuler. */
@Composable
fun ScheduledScreen(vm: MessagesViewModel) {
    val c = Fern.colors
    val prefs by vm.prefs.collectAsState()
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf(emptyMap<Long, String>()) }
    LaunchedEffect(prefs.scheduled) {
        labels = prefs.scheduled.associate { it.id to vm.display(it.addresses).first }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = { vm.back() }) {
            Text("PROGRAMMÉS", style = Fern.type.libelle, color = c.lichen, modifier = Modifier.padding(end = 16.dp))
        }
        if (prefs.scheduled.isEmpty()) {
            Text(
                "Aucun message programmé. Pour en programmer un : « + » → Programmer, dans une conversation.",
                style = Fern.type.corps, color = c.lichen, modifier = Modifier.padding(20.dp),
            )
        }
        if (!vm.canScheduleExact() && prefs.scheduled.isNotEmpty()) {
            Text(
                "Les alarmes exactes ne sont pas autorisées : l'envoi peut avoir quelques minutes de retard.",
                style = Fern.type.nomApp, color = c.roseCarmin, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(prefs.scheduled.sortedBy { it.at }, key = { it.id }) { s ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.mousse).padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(labels[s.id] ?: s.addresses.joinToString(), style = Fern.type.corps, color = c.creme)
                            Text(formatScheduled(s.at).uppercase(), style = Fern.type.libelle, color = c.pistache)
                        }
                        IconButtonRound(FernIcons.Trash, c.roseCarmin) { scope.launch { vm.cancelScheduled(s.id) } }
                    }
                    Box(Modifier.padding(top = 8.dp)) {
                        Text(s.body, style = Fern.type.nomApp, color = c.lichen, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.attachments.isNotEmpty()) {
                        Text(
                            if (s.attachments.size > 1) "+ ${s.attachments.size} PIÈCES JOINTES" else "+ 1 PIÈCE JOINTE",
                            style = Fern.type.libelle, color = c.pistache, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
