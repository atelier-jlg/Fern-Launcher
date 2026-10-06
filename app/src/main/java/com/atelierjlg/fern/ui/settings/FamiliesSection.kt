package com.atelierjlg.fern.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.data.Family
import com.atelierjlg.fern.data.LauncherConfig
import com.atelierjlg.fern.ui.common.AppPicker
import com.atelierjlg.fern.ui.common.AppRow
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.displayName

/**
 * Paramètres → Familles : renommer une famille, voir ses applis, en ajouter ou en déplacer.
 * Le classement automatique reste la base ; ce que tu choisis ici passe avant.
 * (Les couleurs des familles se règlent dans Paramètres → Thème.)
 */
@Composable
fun FamiliesSection(vm: LauncherViewModel, config: LauncherConfig) {
    val index by vm.apps.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Family?>(null) }
    var renaming by remember { mutableStateOf<Family?>(null) }
    var adding by remember { mutableStateOf<Family?>(null) }
    var moving by remember { mutableStateOf<AppEntry?>(null) }
    var editingColor by remember { mutableStateOf<Pair<String, String>?>(null) }
    val colors = Fern.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Touche une famille pour régler ses couleurs et voir ses applis. « Changer » déplace une appli ; " +
                "ce que tu choisis passe avant le classement automatique. Les couleurs font partie du thème actif.",
            style = Fern.type.nomApp,
            color = colors.lichen,
        )
        for (family in Family.entries) {
            val members = index.visible.filter { it.family == family }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (open == family) colors.lierre else colors.mousse, RoundedCornerShape(20.dp))
                    .clickable { open = if (open == family) null else family }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Box(Modifier.size(14.dp).background(colors.plate(family), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(family.displayName(), style = Fern.type.corps, color = colors.creme)
                    Text("${members.size} appli${if (members.size > 1) "s" else ""}", style = Fern.type.nomApp, color = colors.lichen)
                }
                Text(if (open == family) "▴" else "▾", style = Fern.type.corps, color = colors.lichen)
            }
            if (open == family) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 12.dp)) {
                    // Les couleurs de la famille (plaque + picto), enregistrées dans le thème actif.
                    for ((label, key, hex) in config.theme.colors.familyEntries(family)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.mousse, RoundedCornerShape(16.dp))
                                .clickable { editingColor = key to hex }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, style = Fern.type.corps, color = colors.creme)
                                Text(hex, style = Fern.type.nomApp, color = colors.lichen)
                            }
                            Swatch(hex)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Renommer", onClick = { renaming = family })
                        PillButton("+ Ajouter une appli", onClick = { adding = family }, accent = true)
                    }
                    for (app in members) {
                        val manual = config.appFamilies[app.key] != null
                        AppRow(app = app, onClick = { moving = app }, trailing = if (manual) "CHOISIE · CHANGER" else "CHANGER")
                    }
                }
            }
        }
    }

    editingColor?.let { (key, hex) ->
        ColorDialog(
            initial = hex,
            onConfirm = { vm.setThemeColor(key, it); editingColor = null },
            onDismiss = { editingColor = null },
        )
    }
    renaming?.let { family ->
        TextInputDialog(
            title = "Nom de la famille (vide = « ${family.label} »)",
            initial = config.familyNames[family] ?: family.label,
            onConfirm = { vm.renameFamily(family, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    adding?.let { family ->
        AppPicker(
            apps = index.visible.filter { it.family != family },
            current = null,
            onPick = { app ->
                if (app != null) vm.setAppFamily(app, family)
                adding = null
            },
            onDismiss = { adding = null },
        )
    }
    moving?.let { app ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Famille de ${app.label}") },
            text = {
                LazyColumn {
                    items(listOf<Family?>(null) + Family.entries) { family ->
                        TextButton(onClick = { vm.setAppFamily(app, family); moving = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                (if (family != null && family == app.family) "● " else "") +
                                    (family?.displayName() ?: "Automatique (classement de Fern)"),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moving = null }) { Text("Annuler") } },
        )
    }
}
