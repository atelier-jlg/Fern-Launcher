package com.atelierjlg.fern.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atelierjlg.fern.BuildConfig
import com.atelierjlg.fern.LauncherViewModel
import com.atelierjlg.fern.OverlayMode
import com.atelierjlg.fern.data.slotValue
import com.atelierjlg.fern.ui.common.AppPicker
import com.atelierjlg.fern.ui.drawer.AppDrawer
import com.atelierjlg.fern.ui.drawer.DrawerActions
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

    // Le bouton Retour ferme la couche la plus haute. Sur l'accueil, il ne fait rien
    // (sinon Android fermerait le lanceur).
    BackHandler { vm.onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Fern.colors.nuit),
    ) {
        HomeScreen(vm)

        AnimatedVisibility(
            visible = overlay.visible,
            enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180)),
        ) {
            AppDrawer(
                apps = apps.visible,
                hiddenApps = apps.hidden,
                settings = config.drawer,
                launchCounts = config.launchCounts,
                focusSearch = overlay.mode == OverlayMode.Search,
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
