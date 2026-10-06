package com.atelierjlg.fern.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.DrawerItem
import com.atelierjlg.fern.apps.buildDrawerItems
import com.atelierjlg.fern.apps.search
import com.atelierjlg.fern.data.Destination
import com.atelierjlg.fern.data.DrawerSettings
import com.atelierjlg.fern.data.DrawerSort
import com.atelierjlg.fern.data.DrawerStyle
import com.atelierjlg.fern.data.Family
import com.atelierjlg.fern.data.SlotRef
import com.atelierjlg.fern.ui.common.AppIcon
import com.atelierjlg.fern.ui.common.AppRow
import com.atelierjlg.fern.ui.common.FernSearchField
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.common.GearButton
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.search.SearchExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tout ce que le tiroir sait faire, regroupé pour ne pas avoir 15 paramètres. */
class DrawerActions(
    val onLaunch: (AppEntry) -> Unit,
    val onAppInfo: (AppEntry) -> Unit,
    val onUninstall: (AppEntry) -> Unit,
    val onClose: () -> Unit,
    val destinations: () -> List<Destination>,
    val onAddTo: (SlotRef, AppEntry) -> Unit,
    val onSetHidden: (AppEntry, Boolean) -> Unit,
    val onRename: (AppEntry, String) -> Unit,
    val onSettings: ((DrawerSettings) -> DrawerSettings) -> Unit,
    val onOpenSettings: () -> Unit,
    /** Range une appli dans une famille (null = automatique). */
    val onSetFamily: (AppEntry, Family?) -> Unit,
)

/**
 * Le tiroir : toutes les applis, avec la barre de recherche en bas (accessible au pouce).
 * Avec `focusSearch`, le clavier s'ouvre directement.
 *
 * Fermeture : bouton Retour, bouton Accueil, ou tirer la liste vers le bas
 * quand elle est déjà tout en haut.
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    hiddenApps: List<AppEntry>,
    settings: DrawerSettings,
    launchCounts: Map<String, Int>,
    focusSearch: Boolean,
    versionName: String,
    actions: DrawerActions,
    searchExtras: suspend (String) -> SearchExtras,
    searchVersion: Int,
    searchActions: SearchActions,
    webLabel: String,
) {
    val colors = Fern.colors
    var query by remember { mutableStateOf("") }
    var addingApp by remember { mutableStateOf<AppEntry?>(null) }
    var renamingApp by remember { mutableStateOf<AppEntry?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var familyApp by remember { mutableStateOf<AppEntry?>(null) }

    val searching = query.isNotBlank()
    val results = remember(apps, query) { apps.search(query) }
    val familyNames = com.atelierjlg.fern.ui.theme.LocalFamilyNames.current
    val drawerItems = remember(apps, settings.sort, launchCounts, familyNames) {
        buildDrawerItems(apps, settings.sort, launchCounts, familyNames)
    }
    // Contacts, agenda, raccourcis, calcul : cherchés en arrière-plan, 150 ms après la dernière touche.
    val extras by produceState(SearchExtras.Empty, query, searchVersion) {
        value = if (query.isBlank()) {
            SearchExtras.Empty
        } else {
            delay(150)
            searchExtras(query)
        }
    }

    // Option « ouverture directe » : un seul résultat → on le lance.
    LaunchedEffect(results, searching) {
        if (settings.autoLaunchSingleResult && searching && results.size == 1) actions.onLaunch(results[0])
    }

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

    val gridState = rememberLazyGridState()
    val pullToClose = rememberPullToClose(actions.onClose)
    val columns = if (settings.style == DrawerStyle.Liste) 1 else settings.gridColumns.coerceIn(3, 6)
    val showSections = settings.sort == DrawerSort.Alphabetique

    // Le menu d'appui long d'une appli (identique dans la grille et dans les résultats).
    val appCell: @Composable (AppEntry, Boolean) -> Unit = { app, asRow ->
        AppCell(
            app = app,
            asRow = asRow,
            onClick = { actions.onLaunch(app) },
            menu = listOf(
                "Ajouter à…" to { addingApp = app },
                "Renommer" to { renamingApp = app },
                "Famille…" to { familyApp = app },
                "Masquer" to { actions.onSetHidden(app, true) },
                "Infos de l'appli" to { actions.onAppInfo(app) },
                "Désinstaller" to { actions.onUninstall(app) },
            ),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.nuit.copy(alpha = 0.97f))
            .systemBarsPadding()
            .imePadding(),
    ) {
        if (!searching) {
            DrawerChips(settings = settings, onSettings = actions.onSettings, onOpenSettings = actions.onOpenSettings)
        }

        if (searching) {
            SearchResults(
                query = query,
                apps = results,
                extras = extras,
                webLabel = webLabel,
                fromBottom = true,
                appCell = { app -> appCell(app, false) },
                actions = searchActions,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = if (showSections) 4.dp else 20.dp,
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (columns == 1) 0.dp else 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(pullToClose),
                ) {
                    items(
                        drawerItems,
                        key = { it.id },
                        span = { item -> if (item is DrawerItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                    ) { item ->
                        when (item) {
                            is DrawerItem.Header -> Text(
                                text = item.letter.uppercase(),
                                style = Fern.type.libelle,
                                color = colors.roseCarmin,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                            )
                            is DrawerItem.App -> appCell(item.app, columns == 1)
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DrawerFooter(
                            hiddenCount = hiddenApps.size,
                            versionName = versionName,
                            onShowHidden = { showHidden = true },
                        )
                    }
                }
                if (showSections) {
                    AlphabetScroller(items = drawerItems, gridState = gridState)
                }
            }
        }

        FernSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Recherche",
            focusRequester = focusRequester,
            onGo = {
                val first = results.firstOrNull()
                if (first != null) actions.onLaunch(first) else if (searching) searchActions.onWebSearch(query)
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

    addingApp?.let { app ->
        DestinationDialog(
            app = app,
            destinations = remember(app) { actions.destinations() },
            onPick = { ref -> actions.onAddTo(ref, app); addingApp = null },
            onDismiss = { addingApp = null },
        )
    }
    renamingApp?.let { app ->
        TextInputDialog(
            title = "Renommer « ${app.originalLabel} »",
            initial = app.label,
            onConfirm = { actions.onRename(app, it); renamingApp = null },
            onDismiss = { renamingApp = null },
        )
    }
    familyApp?.let { app ->
        AlertDialog(
            onDismissRequest = { familyApp = null },
            title = { Text("Famille de ${app.label}") },
            text = {
                LazyColumn {
                    items(listOf<Family?>(null) + Family.entries) { family ->
                        TextButton(
                            onClick = { actions.onSetFamily(app, family); familyApp = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                (if (family == app.family) "● " else "") + (family?.let { familyNames[it] ?: it.label } ?: "Automatique"),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { familyApp = null }) { Text("Annuler") } },
        )
    }
    if (showHidden) {
        HiddenAppsDialog(
            hiddenApps = hiddenApps,
            onUnhide = { actions.onSetHidden(it, false) },
            onDismiss = { showHidden = false },
        )
    }
}

/** Les pastilles en haut du tiroir : tri et affichage. */
@Composable
private fun DrawerChips(
    settings: DrawerSettings,
    onSettings: ((DrawerSettings) -> DrawerSettings) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        PillButton(
            text = when (settings.sort) {
                DrawerSort.Alphabetique -> "A–Z"
                DrawerSort.Familles -> "Familles"
                DrawerSort.Frequence -> "Fréquence"
            },
            onClick = {
                onSettings {
                    it.copy(
                        sort = when (it.sort) {
                            DrawerSort.Alphabetique -> DrawerSort.Familles
                            DrawerSort.Familles -> DrawerSort.Frequence
                            DrawerSort.Frequence -> DrawerSort.Alphabetique
                        },
                    )
                }
            },
        )
        PillButton(
            text = if (settings.style == DrawerStyle.Grille) "Grille" else "Liste",
            onClick = {
                onSettings {
                    it.copy(style = if (it.style == DrawerStyle.Grille) DrawerStyle.Liste else DrawerStyle.Grille)
                }
            },
        )
        // Collé à droite : l'engrenage ouvre les Paramètres.
        Spacer(Modifier.weight(1f))
        GearButton(onClick = onOpenSettings)
    }
}

@Composable
private fun DrawerFooter(hiddenCount: Int, versionName: String, onShowHidden: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        if (hiddenCount > 0) {
            PillButton("Applis masquées · $hiddenCount", onClick = onShowHidden)
        }
        Text(
            text = "Fern Launcher · v$versionName".uppercase(),
            style = Fern.type.libelle,
            color = Fern.colors.moussePale,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * La colonne A–Z à droite : toucher ou glisser sur une lettre fait défiler jusqu'à sa section.
 */
@Composable
private fun AlphabetScroller(items: List<DrawerItem>, gridState: LazyGridState) {
    val scope = rememberCoroutineScope()
    val headers = remember(items) {
        items.withIndex()
            .filter { it.value is DrawerItem.Header }
            .map { (it.value as DrawerItem.Header).letter to it.index }
    }
    if (headers.isEmpty()) return
    val currentHeaders by rememberUpdatedState(headers)
    var active by remember { mutableStateOf<String?>(null) }

    fun jumpTo(y: Float, height: Int) {
        val list = currentHeaders
        val i = ((y / height.coerceAtLeast(1)) * list.size).toInt().coerceIn(0, list.lastIndex)
        val (letter, index) = list[i]
        if (letter != active) {
            active = letter
            scope.launch { gridState.scrollToItem(index) }
        }
    }

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { offset ->
                    jumpTo(offset.y, size.height)
                    tryAwaitRelease()
                    active = null
                })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, _ ->
                    change.consume()
                    jumpTo(change.position.y, size.height)
                }
            },
    ) {
        for ((letter, _) in headers) {
            Text(
                text = letter,
                style = Fern.type.libelle,
                color = if (letter == active) Fern.colors.roseCarmin else Fern.colors.lichen,
            )
        }
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

/** La liste des applis masquées, pour les faire réapparaître. */
@Composable
private fun HiddenAppsDialog(
    hiddenApps: List<AppEntry>,
    onUnhide: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Applis masquées") },
        text = {
            if (hiddenApps.isEmpty()) {
                Text("Aucune appli masquée.")
            } else {
                LazyColumn {
                    items(hiddenApps, key = { it.key }) { app ->
                        AppRow(app = app, onClick = { onUnhide(app) }, trailing = "AFFICHER")
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

/** Une appli du tiroir (icône en grille, ou ligne en mode liste) avec son menu d'appui long. */
@Composable
private fun AppCell(
    app: AppEntry,
    asRow: Boolean,
    onClick: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        if (asRow) {
            AppRow(app = app, onClick = onClick, onLongClick = { menuOpen = true })
        } else {
            AppIcon(
                app = app,
                iconSize = 50.dp,
                showLabel = true,
                onClick = onClick,
                onLongClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
