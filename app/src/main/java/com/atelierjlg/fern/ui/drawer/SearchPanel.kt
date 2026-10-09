package com.atelierjlg.fern.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.search
import com.atelierjlg.fern.search.SearchExtras
import com.atelierjlg.fern.ui.common.AppIcon
import com.atelierjlg.fern.ui.common.FernSearchField
import com.atelierjlg.fern.ui.theme.Fern
import kotlinx.coroutines.delay

/**
 * Le panneau de recherche (glisser vers le bas sur l'accueil) : il descend du haut,
 * la barre de recherche en haut, le clavier ouvert.
 *
 * Résultats : les applis d'abord, puis « Chercher avec Firefox ».
 * Fermeture : Retour, Accueil, ou glisser vers le haut sur la zone du titre.
 */
@Composable
fun SearchPanel(
    apps: List<AppEntry>,
    autoLaunchSingleResult: Boolean,
    webLabel: String,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
    searchExtras: suspend (String) -> SearchExtras,
    searchVersion: Int,
    searchActions: SearchActions,
) {
    val colors = Fern.colors
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    val results = remember(apps, query) { if (query.isBlank()) emptyList() else apps.search(query) }
    val extras by produceState(SearchExtras.Empty, query, searchVersion) {
        value = if (query.isBlank()) {
            SearchExtras.Empty
        } else {
            delay(150)
            searchExtras(query)
        }
    }

    LaunchedEffect(results) {
        if (autoLaunchSingleResult && searching && results.size == 1) onLaunch(results[0])
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    val close by rememberUpdatedState(onClose)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.nuit.copy(alpha = 0.97f))
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Zone du haut : glisser vers le haut pour refermer.
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(threshold) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -threshold) close() },
                    ) { change, dy ->
                        change.consume()
                        total += dy
                    }
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            FernSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Rechercher parmi les applis / sur le web",
                focusRequester = focusRequester,
                onGo = {
                    val first = results.firstOrNull()
                    when {
                        // Un calcul : Entrée ouvre la calculatrice avec le calcul.
                        extras.calculation != null -> searchActions.onCalculator(query)
                        first != null -> onLaunch(first)
                        searching -> searchActions.onWebSearch(query)
                    }
                },
            )
        }

        if (searching) {
            SearchResults(
                query = query,
                apps = results,
                extras = extras,
                webLabel = webLabel,
                fromBottom = false,
                appCell = { app ->
                    AppIcon(
                        app = app,
                        iconSize = 50.dp,
                        showLabel = true,
                        onClick = { onLaunch(app) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                actions = searchActions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
