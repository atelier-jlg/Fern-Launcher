package com.atelierjlg.fern.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.search
import com.atelierjlg.fern.ui.theme.Fern

/**
 * Le sélecteur d'applis plein écran : pour remplir un emplacement d'un pack ou du dock.
 * `current` = l'appli déjà en place (on peut alors la retirer).
 */
@Composable
fun AppPicker(
    apps: List<AppEntry>,
    current: AppEntry?,
    onPick: (AppEntry?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Fern.colors
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) { apps.search(query) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.nuit)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Choisir une appli".uppercase(),
                    style = Fern.type.libelle,
                    color = colors.lichen,
                    modifier = Modifier.weight(1f),
                )
                GlyphButton("✕", onClick = onDismiss)
            }
            Spacer(Modifier.height(12.dp))
            FernSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Chercher",
                onGo = { results.firstOrNull()?.let(onPick) },
            )
            if (current != null) {
                Spacer(Modifier.height(12.dp))
                PillButton("Retirer ${current.label}", onClick = { onPick(null) })
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(results, key = { it.key }) { app ->
                    AppRow(app = app, onClick = { onPick(app) })
                }
            }
        }
    }
}

/** Une ligne « icône + nom », utilisée dans les listes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppEntry,
    onClick: () -> Unit,
    trailing: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
    ) {
        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Text(app.label, style = Fern.type.corps, color = Fern.colors.creme, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = Fern.type.libelle, color = Fern.colors.moussePale)
        }
    }
}
