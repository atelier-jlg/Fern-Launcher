package com.atelierjlg.fern.contact.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.contact.ContactViewModel
import com.atelierjlg.fern.contact.Screen
import com.atelierjlg.fern.contact.data.ContactDetail
import com.atelierjlg.fern.contact.data.emailLabel
import com.atelierjlg.fern.contact.data.formatBirthday
import com.atelierjlg.fern.contact.data.phoneLabel
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.launch

/** La fiche d'un contact. */
@Composable
fun DetailScreen(vm: ContactViewModel, id: Long) {
    val c = Fern.colors
    val scope = rememberCoroutineScope()
    // Relu quand la liste change (après une modification, un favori…).
    val contacts by vm.contacts.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<ContactDetail?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id, contacts, refresh) {
        detail = vm.detail(id)
        loaded = true
        blocked = detail?.phones?.firstOrNull()?.let { vm.isBlocked(it.value) } == true
    }

    val d = detail
    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = { vm.back() }) {
            if (d != null) {
                IconButtonRound(FernIcons.Star, if (d.starred) c.pistache else c.lichen) {
                    vm.setStarred(d.id, !d.starred)
                    detail = d.copy(starred = !d.starred)
                }
                IconButtonRound(FernIcons.Edit, c.lichen) { vm.open(Screen.Edit(d.id)) }
            }
        }
        if (d == null) {
            if (loaded) Text("Contact introuvable.", style = Fern.type.corps, color = c.lichen, modifier = Modifier.padding(20.dp))
            return@Column
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(d.displayName, d.photoUri, 120.dp)
            Spacer(Modifier.height(16.dp))
            Text(d.displayName, style = Fern.type.titreWidget, color = c.creme, textAlign = TextAlign.Center)
            val sub = listOfNotNull(d.nickname?.value, d.organization?.company?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = Fern.type.corps, color = c.lichen, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))

            val main = d.phones.firstOrNull()?.value
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(FernIcons.Phone, "Appeler", c.pistache, c.nuit, enabled = main != null) { main?.let(vm::call) }
                RoundAction(FernIcons.Message, "Message", c.lierre, c.creme, enabled = main != null) { main?.let(vm::message) }
                RoundAction(FernIcons.Mail, "E-mail", c.lierre, c.creme, enabled = d.emails.isNotEmpty()) {
                    d.emails.firstOrNull()?.let { vm.email(it.value) }
                }
            }
            Spacer(Modifier.height(20.dp))

            if (d.phones.isNotEmpty()) {
                Card {
                    d.phones.forEach { p ->
                        ValueRow(FernIcons.Phone, PhoneNumbers.format(p.value), phoneLabel(p), onClick = { vm.call(p.value) }) {
                            IconButtonRound(FernIcons.Message, c.lichen) { vm.message(p.value) }
                        }
                    }
                }
            }
            if (d.emails.isNotEmpty()) {
                Card { d.emails.forEach { e -> ValueRow(FernIcons.Mail, e.value, emailLabel(e), onClick = { vm.email(e.value) }) } }
            }
            val extras = listOfNotNull(
                d.birthday?.let { Triple(FernIcons.Gift, formatBirthday(it.value), "Anniversaire") },
                d.organization?.takeIf { it.company.isNotBlank() || it.title.isNotBlank() }?.let {
                    Triple(FernIcons.Briefcase, listOf(it.company, it.title).filter(String::isNotBlank).joinToString(" · "), "Travail")
                },
                d.note?.let { Triple(FernIcons.Note, it.value, "Note") },
            )
            if (extras.isNotEmpty()) Card { extras.forEach { (icon, value, label) -> ValueRow(icon, value, label) } }

            Spacer(Modifier.height(8.dp))
            if (vm.canBlock() && d.phones.isNotEmpty()) {
                ActionRow(FernIcons.Block, if (blocked) "Débloquer" else "Bloquer", Modifier.fillMaxWidth(), tint = c.roseCarmin) {
                    scope.launch {
                        vm.setBlocked(d.phones.map { it.value }, !blocked)
                        refresh++
                    }
                }
            }
            ActionRow(FernIcons.Trash, "Supprimer le contact", Modifier.fillMaxWidth(), tint = c.roseCarmin) { confirmDelete = true }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && d != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.mousse,
            title = { Text("Supprimer ${d.displayName} ?", style = Fern.type.corps, color = c.creme) },
            text = { Text("Le contact sera effacé du téléphone. C'est définitif.", style = Fern.type.nomApp, color = c.lichen) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(d) }) { Text("Supprimer", color = c.roseCarmin) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler", color = c.lichen) } },
        )
    }
}

/** Barre du haut : retour à gauche, actions à droite. */
@Composable
fun TopBar(onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonRound(FernIcons.Back, Fern.colors.creme, onClick = onBack)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun IconButtonRound(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        FernIcon(icon, tint)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Fern.colors.mousse)
            .padding(vertical = 6.dp),
    ) { content() }
}

@Composable
private fun ValueRow(
    icon: ImageVector,
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = Fern.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FernIcon(icon, c.lichen, size = 20.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(value, style = Fern.type.corps, color = c.creme)
            Text(label.uppercase(), style = Fern.type.libelle, color = c.moussePale)
        }
        trailing()
    }
}
