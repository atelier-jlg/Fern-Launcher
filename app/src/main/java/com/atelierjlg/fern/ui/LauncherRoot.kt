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
import com.atelierjlg.fern.ui.drawer.AppDrawer
import com.atelierjlg.fern.ui.home.HomeScreen
import com.atelierjlg.fern.ui.theme.Fern

/**
 * Assemble l'accueil et le tiroir (posé par-dessus quand il est ouvert).
 */
@Composable
fun LauncherRoot(viewModel: LauncherViewModel) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()

    // Le bouton Retour ferme le tiroir. Sur l'accueil, il ne fait rien
    // (sinon Android fermerait le lanceur).
    BackHandler { viewModel.closeOverlay() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Fern.colors.nuit),
    ) {
        HomeScreen(
            onSwipeUp = { viewModel.open(OverlayMode.Drawer) },
            onSwipeDown = { viewModel.open(OverlayMode.Search) },
        )

        AnimatedVisibility(
            visible = overlay.visible,
            enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180)),
        ) {
            AppDrawer(
                apps = apps,
                focusSearch = overlay.mode == OverlayMode.Search,
                versionName = BuildConfig.VERSION_NAME,
                onLaunch = viewModel::launch,
                onAppInfo = viewModel::openAppInfo,
                onUninstall = viewModel::uninstall,
                onClose = viewModel::closeOverlay,
            )
        }
    }
}
