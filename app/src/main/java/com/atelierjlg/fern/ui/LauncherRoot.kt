package com.atelierjlg.fern.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atelierjlg.fern.BuildConfig
import com.atelierjlg.fern.LauncherViewModel
import com.atelierjlg.fern.OverlayMode
import com.atelierjlg.fern.data.slotValue
import com.atelierjlg.fern.ui.common.AppPicker
import com.atelierjlg.fern.ui.drawer.AppDrawer
import com.atelierjlg.fern.ui.drawer.DrawerActions
import com.atelierjlg.fern.ui.drawer.SearchActions
import com.atelierjlg.fern.ui.drawer.SearchPanel
import com.atelierjlg.fern.ui.home.HomeScreen
import com.atelierjlg.fern.ui.theme.Fern

/**
 * Assemble les couches de Fern, de bas en haut :
 * l'accueil, le tiroir (quand il est ouvert), le sélecteur d'applis (mode édition).
 */
@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val picking by vm.picking.collectAsStateWithLifecycle()
    val searchVersion by vm.searchVersion.collectAsStateWithLifecycle()

    // Demande d'autorisations (contacts, agenda) : Android affiche sa propre fenêtre.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.onPermissionsChanged() }

    // Le bouton Retour ferme la couche la plus haute. Sur l'accueil, il ne fait rien
    // (sinon Android fermerait le lanceur).
    BackHandler { vm.onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Fern.colors.nuit),
    ) {
        HomeScreen(vm)

        val webLabel = remember(apps) { vm.webLabel() }
        val searchActions = SearchActions(
            onCopy = vm::copyToClipboard,
            onOpenContact = vm::openContact,
            onCall = vm::callContact,
            onMessage = vm::messageContact,
            onOpenEvent = vm::openEvent,
            onShortcut = vm::startShortcut,
            onWebSearch = vm::webSearch,
            onRequestPermissions = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALENDAR),
                )
            },
        )

        // Tiroir : monte du bas (glisser vers le haut).
        AnimatedVisibility(
            visible = overlay.visible && overlay.mode == OverlayMode.Drawer,
            enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180)),
        ) {
            AppDrawer(
                apps = apps.visible,
                hiddenApps = apps.hidden,
                settings = config.drawer,
                launchCounts = config.launchCounts,
                focusSearch = false,
                versionName = BuildConfig.VERSION_NAME,
                actions = DrawerActions(
                    onLaunch = vm::launch,
                    onAppInfo = vm::openAppInfo,
                    onUninstall = vm::uninstall,
                    onClose = vm::closeOverlay,
                    destinations = vm::freeDestinations,
                    onAddTo = vm::addTo,
                    onSetHidden = vm::setHidden,
                    onRename = vm::renameApp,
                    onSettings = vm::updateDrawer,
                ),
                searchExtras = vm::searchExtras,
                searchVersion = searchVersion,
                searchActions = searchActions,
                webLabel = webLabel,
            )
        }

        // Recherche : descend du haut (glisser vers le bas).
        AnimatedVisibility(
            visible = overlay.visible && overlay.mode == OverlayMode.Search,
            enter = slideInVertically(tween(240)) { -it / 3 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(200)) { -it / 3 } + fadeOut(tween(160)),
        ) {
            SearchPanel(
                apps = apps.visible,
                autoLaunchSingleResult = config.drawer.autoLaunchSingleResult,
                webLabel = webLabel,
                onLaunch = vm::launch,
                onClose = vm::closeOverlay,
                searchExtras = vm::searchExtras,
                searchVersion = searchVersion,
                searchActions = searchActions,
            )
        }

        picking?.let { ref ->
            AppPicker(
                apps = apps.visible,
                current = apps.find(config.slotValue(ref)),
                onPick = { app -> vm.setSlot(ref, app) },
                onDismiss = { vm.pickFor(null) },
            )
        }
    }
}
