package com.atelierjlg.fern.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atelierjlg.fern.LauncherViewModel
import com.atelierjlg.fern.data.LauncherConfig
import com.atelierjlg.fern.data.NamedTheme
import com.atelierjlg.fern.data.Space
import com.atelierjlg.fern.data.SpaceRule
import com.atelierjlg.fern.data.ThemePresets
import com.atelierjlg.fern.data.newId
import com.atelierjlg.fern.ui.common.AppPicker
import com.atelierjlg.fern.ui.common.AppRow
import com.atelierjlg.fern.ui.common.ConfirmDialog
import com.atelierjlg.fern.ui.common.GlyphButton
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern

private val DAY_NAMES = listOf("L", "M", "M", "J", "V", "S", "D")

private fun minuteLabel(m: Int) = "%02d:%02d".format(m / 60, m % 60)

private fun daysLabel(days: Set<Int>) = when (days) {
    (1..7).toSet() -> "tous les jours"
    (1..5).toSet() -> "lun–ven"
    setOf(6, 7) -> "week-end"
    else -> days.sorted().joinToString(" ") { DAY_NAMES[it - 1] }
}

/** « 8:30 », « 08h30 », « 830 » → minutes depuis minuit, ou null. */
private fun parseTime(text: String): Int? {
    val digits = text.filter { it.isDigit() }
    if (digits.isEmpty() || digits.length > 4) return null
    val (h, m) = if (digits.length <= 2) digits.toInt() to 0 else digits.dropLast(2).toInt() to digits.takeLast(2).toInt()
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

/** Paramètres → Spaces : créer, renommer, thème par Space, planning horaire. */
@Composable
fun SpacesSection(vm: LauncherViewModel, config: LauncherConfig) {
    var naming by remember { mutableStateOf<Boolean?>(null) } // true = copie du Space actif
    var renaming by remember { mutableStateOf<Space?>(null) }
    var deleting by remember { mutableStateOf<Space?>(null) }
    var themeFor by remember { mutableStateOf<Space?>(null) }
    var addingRule by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Un Space = un jeu de pages + dock (+ son thème). Par exemple « Perso » et « Travail ».",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
        for (space in config.spaces) {
            val active = space.id == config.activeSpace.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (active) Fern.colors.lierre else Fern.colors.mousse, RoundedCornerShape(20.dp))
                    .clickable { vm.setActiveSpace(space.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text((if (active) "● " else "") + space.name, style = Fern.type.corps, color = Fern.colors.creme)
                    Text(
                        "Thème : " + (space.theme?.name ?: "général"),
                        style = Fern.type.nomApp,
                        color = Fern.colors.lichen,
                    )
                }
                GlyphButton("🎨", onClick = { themeFor = space })
                GlyphButton("✎", onClick = { renaming = space })
                GlyphButton("✕", onClick = { deleting = space }, enabled = config.spaces.size > 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("+ Space vide", onClick = { naming = false })
            PillButton("+ Copier celui-ci", onClick = { naming = true })
        }

        Text(
            "PLANNING",
            style = Fern.type.libelle,
            color = Fern.colors.roseCarmin,
            modifier = Modifier.padding(top = 14.dp, start = 4.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Fern.colors.mousse, RoundedCornerShape(20.dp))
                .clickable { vm.updateSchedule { it.copy(enabled = !it.enabled) } }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("Changer de Space selon l'heure", style = Fern.type.corps, color = Fern.colors.creme, modifier = Modifier.weight(1f))
            Text(if (config.spaceSchedule.enabled) "OUI" else "NON", style = Fern.type.libelle, color = Fern.colors.pistache)
        }
        for (rule in config.spaceSchedule.rules) {
            val name = config.spaces.firstOrNull { it.id == rule.spaceId }?.name ?: "?"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Fern.colors.mousse, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    "$name · ${daysLabel(rule.days)} · ${minuteLabel(rule.startMinute)}–${minuteLabel(rule.endMinute)}",
                    style = Fern.type.corps,
                    color = Fern.colors.creme,
                    modifier = Modifier.weight(1f),
                )
                GlyphButton("✕", onClick = { vm.updateSchedule { s -> s.copy(rules = s.rules.filterNot { it.id == rule.id }) } })
            }
        }
        PillButton("+ Règle", onClick = { addingRule = true })
    }

    naming?.let { copy ->
        TextInputDialog(
            title = if (copy) "Nom de la copie" else "Nom du nouveau Space",
            initial = "",
            confirmLabel = "Créer",
            onConfirm = { vm.addSpace(it, copy); naming = null },
            onDismiss = { naming = null },
        )
    }
    renaming?.let { space ->
        TextInputDialog(
            title = "Renommer le Space",
            initial = space.name,
            onConfirm = { vm.renameSpace(space.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { space ->
        ConfirmDialog(
            title = "Supprimer « ${space.name} » ?",
            message = "Ses pages, packs et son dock disparaissent. Les applis restent installées.",
            confirmLabel = "Supprimer",
            onConfirm = { vm.removeSpace(space.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    themeFor?.let { space ->
        val options: List<NamedTheme?> = listOf<NamedTheme?>(null) + ThemePresets.all + config.savedThemes
        AlertDialog(
            onDismissRequest = { themeFor = null },
            title = { Text("Thème de « ${space.name} »") },
            text = {
                Column {
                    for (t in options) {
                        TextButton(onClick = { vm.setSpaceTheme(space.id, t); themeFor = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(t?.name ?: "Thème général", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeFor = null }) { Text("Annuler") } },
        )
    }
    if (addingRule) {
        RuleDialog(
            spaces = config.spaces,
            onConfirm = { rule -> vm.updateSchedule { it.copy(rules = it.rules + rule) }; addingRule = false },
            onDismiss = { addingRule = false },
        )
    }
}

@Composable
private fun RuleDialog(spaces: List<Space>, onConfirm: (SpaceRule) -> Unit, onDismiss: () -> Unit) {
    var spaceId by remember { mutableStateOf(spaces.first().id) }
    var days by remember { mutableStateOf((1..5).toSet()) }
    var start by remember { mutableStateOf("08:00") }
    var end by remember { mutableStateOf("18:00") }
    val startMin = parseTime(start)
    val endMin = parseTime(end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle règle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Space")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in spaces) {
                        PillButton(s.name, onClick = { spaceId = s.id }, accent = s.id == spaceId)
                    }
                }
                Text("Jours")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 1..7) {
                        PillButton(
                            DAY_NAMES[d - 1],
                            onClick = { days = if (d in days) days - d else days + d },
                            accent = d in days,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Début") },
                        isError = startMin == null,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("Fin") },
                        isError = endMin == null,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = startMin != null && endMin != null && days.isNotEmpty(),
                onClick = {
                    onConfirm(SpaceRule(id = newId(), spaceId = spaceId, days = days, startMinute = startMin!!, endMinute = endMin!!))
                },
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Paramètres → Focus : les applis qui disparaissent quand le mode Focus est actif. */
@Composable
fun FocusSection(vm: LauncherViewModel, config: LauncherConfig) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val blocked = apps.all.filter { it.key in config.focus.blockedApps }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (config.focus.enabled) Fern.colors.lierre else Fern.colors.mousse, RoundedCornerShape(20.dp))
                .clickable { vm.toggleFocus() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("Mode Focus", style = Fern.type.corps, color = Fern.colors.creme, modifier = Modifier.weight(1f))
            Text(if (config.focus.enabled) "ACTIF" else "COUPÉ", style = Fern.type.libelle, color = Fern.colors.pistache)
        }
        Text(
            "Tant qu'il est actif, ces applis disparaissent de l'accueil, du tiroir et de la recherche. " +
                "Astuce : attribue « Activer / couper le mode Focus » à un geste.",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
        for (app in blocked) {
            AppRow(app = app, onClick = { vm.updateFocus { it.copy(blockedApps = it.blockedApps - app.key) } }, trailing = "RETIRER")
        }
        PillButton("+ Ajouter une appli", onClick = { picking = true })
    }

    if (picking) {
        AppPicker(
            apps = apps.all.filterNot { it.key in config.focus.blockedApps },
            current = null,
            onPick = { app ->
                if (app != null) vm.updateFocus { it.copy(blockedApps = it.blockedApps + app.key) }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
