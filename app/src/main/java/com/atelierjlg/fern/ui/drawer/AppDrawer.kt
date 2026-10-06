package com.atelierjlg.fern.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.search
import com.atelierjlg.fern.data.Destination
import com.atelierjlg.fern.data.SlotRef
import com.atelierjlg.fern.ui.common.AppIcon
import com.atelierjlg.fern.ui.common.FernSearchField
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern

/**
 * Le tiroir : toutes les applis en grille, avec la barre de recherche en bas
 * (accessible au pouce). Avec `focusSearch`, le clavier s'ouvre directement.
 *
 * Fermeture : bouton Retour, bouton Accueil, ou tirer la liste vers le bas
 * quand elle est déjà tout en haut.
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    focusSearch: Boolean,
    versionName: String,
    onLaunch: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onClose: () -> Unit,
    destinations: () -> List<Destination>,
    onAddTo: (SlotRef, AppEntry) -> Unit,
    onHide: (AppEntry) -> Unit,
    onRename: (AppEntry, String) -> Unit,
) {
    val colors = Fern.colors
    var addingApp by remember { mutableStateOf<AppEntry?>(null) }
    var renamingApp by remember { mutableStateOf<AppEntry?>(null) }
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) { apps.search(query) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    // En quittant le tiroir, on range le clavier.
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    val pullToClose = rememberPullToClose(onClose)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.nuit.copy(alpha = 0.97f))
            .systemBarsPadding()
            .imePadding(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Avec la recherche, le meilleur résultat est en bas, près du pouce.
            reverseLayout = query.isNotEmpty(),
            modifier = Modifier
                .weight(1f)
                .nestedScroll(pullToClose),
        ) {
            items(results, key = { it.key }) { app ->
                AppCell(
                    app = app,
                    onClick = { onLaunch(app) },
                    menu = listOf(
                        "Ajouter à…" to { addingApp = app },
                        "Renommer" to { renamingApp = app },
                        "Masquer" to { onHide(app) },
                        "Infos de l'appli" to { onAppInfo(app) },
                        "Désinstaller" to { onUninstall(app) },
                    ),
                )
            }
            if (results.isEmpty() && query.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Aucune appli pour « $query »".uppercase(),
                        style = Fern.type.libelle,
                        color = colors.moussePale,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            if (query.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Fern Launcher · v$versionName".uppercase(),
                        style = Fern.type.libelle,
                        color = colors.moussePale,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }

        FernSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Chercher une appli",
            focusRequester = focusRequester,
            onGo = { results.firstOrNull()?.let(onLaunch) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    addingApp?.let { app ->
        DestinationDialog(
            app = app,
            destinations = remember(app) { destinations() },
            onPick = { ref -> onAddTo(ref, app); addingApp = null },
            onDismiss = { addingApp = null },
        )
    }
    renamingApp?.let { app ->
        TextInputDialog(
            title = "Renommer « ${app.originalLabel} »",
            initial = app.label,
            onConfirm = { onRename(app, it); renamingApp = null },
            onDismiss = { renamingApp = null },
        )
    }
}

/** « Ajouter à… » : la liste des endroits où il reste une place. */
@Composable
private fun DestinationDialog(
    app: AppEntry,
    destinations: List<Destination>,
    onPick: (SlotRef) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter ${app.label} à…") },
        text = {
            if (destinations.isEmpty()) {
                Text("Plus aucune place libre. Crée un pack en mode édition (appui long sur l'accueil).")
            } else {
                LazyColumn {
                    items(destinations) { dest ->
                        TextButton(onClick = { onPick(dest.ref) }, modifier = Modifier.fillMaxWidth()) {
                            Text(dest.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/**
 * Détecte le geste « tirer vers le bas alors que la liste est déjà en haut »
 * et ferme le tiroir.
 */
@Composable
private fun rememberPullToClose(onClose: () -> Unit): NestedScrollConnection {
    val close by rememberUpdatedState(onClose)
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    return remember(threshold) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > threshold) {
                        pulled = 0f
                        close()
                    }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}

@Composable
private fun AppCell(
    app: AppEntry,
    onClick: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        AppIcon(
            app = app,
            iconSize = 50.dp,
            showLabel = true,
            onClick = onClick,
            onLongClick = { menuOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            for ((label, action) in menu) {
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        menuOpen = false
                        action()
                    },
                )
            }
        }
    }
}
