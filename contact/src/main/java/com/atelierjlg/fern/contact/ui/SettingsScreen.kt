package com.atelierjlg.fern.contact.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.atelierjlg.fern.contact.ContactViewModel
import com.atelierjlg.fern.contact.data.Telemarketing
import com.atelierjlg.fern.ui.kit.*
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcons

/** Réglages de Fern Contact : démarchage, numéros bloqués, anniversaires, réponses rapides, sauvegarde. */
@Composable
fun SettingsScreen(vm: ContactViewModel) {
    val c = Fern.colors
    val blocked by vm.blocked.collectAsState()
    var telemarketing by remember { mutableStateOf(vm.blockTelemarketing) }
    var birthdays by remember { mutableStateOf(vm.birthdayReminders) }
    var replies by remember { mutableStateOf(vm.quickReplies.let { it + List(3 - it.size.coerceAtMost(3)) { "" } }.take(3)) }
    LaunchedEffect(Unit) { vm.loadBlocked() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) vm.exportContacts(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importContacts(uri)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = {
            vm.quickReplies = replies
            vm.back()
        }) {
            Text("RÉGLAGES", style = Fern.type.libelle, color = c.lichen, modifier = Modifier.padding(end = 16.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                SectionLabel("Démarchage", Modifier.padding(top = 0.dp))
                ToggleRow(
                    "Bloquer le démarchage",
                    "Numéros réservés aux appels commerciaux (${Telemarketing.PREFIXES.take(4).joinToString(", ") { PhoneNumbers.format(it + "000000").take(5) }}…). Ils restent visibles dans le journal.",
                    telemarketing,
                ) {
                    telemarketing = it
                    vm.blockTelemarketing = it
                }
            }
            Card {
                SectionLabel("Numéros bloqués", Modifier.padding(top = 0.dp))
                if (blocked.isEmpty()) {
                    Text("Aucun. Pour bloquer : fiche du contact, ou appui long dans Récents.", style = Fern.type.nomApp, color = c.lichen)
                }
                blocked.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(PhoneNumbers.format(b.number), style = Fern.type.corps, color = c.creme, modifier = Modifier.weight(1f))
                        Pill("Débloquer", false) { vm.unblock(b.number) }
                    }
                }
            }
            Card {
                SectionLabel("Anniversaires", Modifier.padding(top = 0.dp))
                ToggleRow("Me le rappeler", "Une notification le matin du jour J (vers 9 h), avec Appeler et Écrire.", birthdays) {
                    birthdays = it
                    vm.birthdayReminders = it
                }
            }
            Card {
                SectionLabel("Refuser avec un message", Modifier.padding(top = 0.dp))
                Text("Proposés sur l'écran d'un appel entrant. Envoyés par Fern Messages.", style = Fern.type.nomApp, color = c.lichen)
                replies.forEachIndexed { i, r ->
                    FernField(r, { v -> replies = replies.toMutableList().also { it[i] = v } }, "Réponse ${i + 1}", Modifier.fillMaxWidth())
                }
            }
            Card {
                SectionLabel("Sauvegarde", Modifier.padding(top = 0.dp))
                Text("Tous les contacts dans un fichier .vcf, lisible par n'importe quel téléphone.", style = Fern.type.nomApp, color = c.lichen)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Exporter", true) { export.launch("contacts.vcf") }
                    Pill("Importer", false) { importer.launch(arrayOf("text/x-vcard", "text/vcard", "text/*")) }
                }
            }
            Text("Fern Contact v${vm.versionName()}", style = Fern.type.nomApp, color = c.moussePale, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Fern.colors.mousse).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
