package com.atelierjlg.fern.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.search
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
) {
    val colors = Fern.colors
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
                    onAppInfo = { onAppInfo(app) },
                    onUninstall = { onUninstall(app) },
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

        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onGo = { results.firstOrNull()?.let(onLaunch) },
            focusRequester = focusRequester,
        )
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: AppEntry,
    onClick: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
                .padding(vertical = 4.dp),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                style = Fern.type.nomApp,
                color = Fern.colors.creme,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Infos de l'appli") },
                onClick = {
                    menuOpen = false
                    onAppInfo()
                },
            )
            DropdownMenuItem(
                text = { Text("Désinstaller") },
                onClick = {
                    menuOpen = false
                    onUninstall()
                },
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onGo: () -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = Fern.colors
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = Fern.type.corps.copy(color = colors.creme),
        cursorBrush = SolidColor(colors.roseCarmin),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(colors.lierre, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Chercher une appli",
                            style = Fern.type.corps,
                            color = colors.lichen,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}
