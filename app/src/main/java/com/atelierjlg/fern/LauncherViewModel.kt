package com.atelierjlg.fern

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ce qui est ouvert par-dessus l'accueil. */
enum class OverlayMode {
    /** Le tiroir d'applis (glisser vers le haut). */
    Drawer,

    /** Le tiroir avec le clavier déjà ouvert (glisser vers le bas). */
    Search,
}

data class OverlayState(
    val visible: Boolean = false,
    /** On garde le dernier mode même fermé, pour l'animation de fermeture. */
    val mode: OverlayMode = OverlayMode.Drawer,
)

/**
 * Le « cerveau » de l'écran d'accueil : il détient l'état et reçoit les actions.
 * Les écrans (Compose) ne font qu'afficher cet état et appeler ces fonctions.
 *
 * Un ViewModel survit aux rotations et aux recréations de l'écran.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application).also { it.start() }

    val apps: StateFlow<List<AppEntry>> = repository.apps

    private val _overlay = MutableStateFlow(OverlayState())
    val overlay: StateFlow<OverlayState> = _overlay.asStateFlow()

    fun open(mode: OverlayMode) {
        _overlay.value = OverlayState(visible = true, mode = mode)
    }

    fun closeOverlay() {
        _overlay.update { it.copy(visible = false) }
    }

    /** Appui sur le bouton Accueil alors qu'on est déjà sur Fern. */
    fun onHomePressed() = closeOverlay()

    fun launch(app: AppEntry) {
        repository.launch(app)
        closeOverlay()
    }

    fun openAppInfo(app: AppEntry) = repository.openAppInfo(app)

    fun uninstall(app: AppEntry) = repository.uninstall(app)

    override fun onCleared() {
        repository.stop()
    }
}
