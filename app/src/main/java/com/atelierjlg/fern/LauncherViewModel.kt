package com.atelierjlg.fern

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.AppIndex
import com.atelierjlg.fern.apps.AppRepository
import com.atelierjlg.fern.data.ConfigStore
import com.atelierjlg.fern.data.DefaultLayout
import com.atelierjlg.fern.data.DrawerSettings
import com.atelierjlg.fern.data.HomeBlock
import com.atelierjlg.fern.data.LauncherConfig
import com.atelierjlg.fern.data.SlotRef
import com.atelierjlg.fern.data.addBlock
import com.atelierjlg.fern.data.addPage
import com.atelierjlg.fern.data.countLaunch
import com.atelierjlg.fern.data.freeDestinations
import com.atelierjlg.fern.data.moveBlock
import com.atelierjlg.fern.data.movePage
import com.atelierjlg.fern.data.removeBlock
import com.atelierjlg.fern.data.removePage
import com.atelierjlg.fern.data.renameApp
import com.atelierjlg.fern.data.renamePack
import com.atelierjlg.fern.data.renamePage
import com.atelierjlg.fern.data.setHidden
import com.atelierjlg.fern.data.setSlot
import com.atelierjlg.fern.data.updateDrawer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Le « cerveau » de Fern : il détient l'état et reçoit les actions.
 * Les écrans (Compose) ne font qu'afficher cet état et appeler ces fonctions.
 *
 * Un ViewModel survit aux rotations et aux recréations de l'écran.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application).also { it.start() }
    private val store = ConfigStore(application)

    val config: StateFlow<LauncherConfig> = store.config

    /** Les applis telles qu'affichées (renommées, masquées à part…). */
    val apps: StateFlow<AppIndex> = combine(repository.apps, store.config) { apps, config ->
        AppIndex.build(apps, config)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppIndex.Empty)

    private val _overlay = MutableStateFlow(OverlayState())
    val overlay: StateFlow<OverlayState> = _overlay.asStateFlow()

    /** Mode édition de l'accueil (appui long). */
    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    /** Augmente à chaque appui sur Accueil (l'accueil revient alors à la première page). */
    private val _homeTick = MutableStateFlow(0)
    val homeTick: StateFlow<Int> = _homeTick.asStateFlow()

    /** Emplacement en cours de remplissage : le sélecteur d'applis est ouvert. */
    private val _picking = MutableStateFlow<SlotRef?>(null)
    val picking: StateFlow<SlotRef?> = _picking.asStateFlow()

    init {
        // Premier lancement : on pose la disposition de départ dès que la liste des applis est connue.
        viewModelScope.launch {
            val installed = repository.apps.first { it.isNotEmpty() }
            if (!store.config.value.seeded) {
                store.update { DefaultLayout.seed(it, installed, application) }
            }
        }
    }

    // ─── Tiroir / recherche ─────────────────────────────────────────────────

    fun open(mode: OverlayMode) {
        _overlay.value = OverlayState(visible = true, mode = mode)
    }

    fun closeOverlay() {
        _overlay.update { it.copy(visible = false) }
    }

    /** Appui sur le bouton Accueil alors qu'on est déjà sur Fern. */
    fun onHomePressed() {
        if (!_overlay.value.visible && !_editing.value) _homeTick.update { it + 1 }
        closeOverlay()
        _picking.value = null
        _editing.value = false
    }

    /** Bouton Retour : on ferme la couche la plus haute. */
    fun onBack() {
        when {
            _picking.value != null -> _picking.value = null
            _overlay.value.visible -> closeOverlay()
            _editing.value -> _editing.value = false
        }
    }

    // ─── Applis ─────────────────────────────────────────────────────────────

    fun launch(app: AppEntry) {
        repository.launch(app)
        store.update { it.countLaunch(app.key) }
        closeOverlay()
    }

    fun openAppInfo(app: AppEntry) = repository.openAppInfo(app)

    fun uninstall(app: AppEntry) = repository.uninstall(app)

    fun setHidden(app: AppEntry, hidden: Boolean) = store.update { it.setHidden(app.key, hidden) }

    fun updateDrawer(transform: (DrawerSettings) -> DrawerSettings) = store.update { it.updateDrawer(transform) }

    fun renameApp(app: AppEntry, name: String) = store.update { it.renameApp(app.key, name) }

    // ─── Mode édition ───────────────────────────────────────────────────────

    fun setEditing(on: Boolean) {
        _editing.value = on
        if (on) closeOverlay()
    }

    fun pickFor(ref: SlotRef?) {
        _picking.value = ref
    }

    fun setSlot(ref: SlotRef, app: AppEntry?) {
        store.update { it.setSlot(ref, app?.key) }
        _picking.value = null
    }

    /** Range une appli à un endroit précis (« Ajouter à… » depuis le tiroir). */
    fun addTo(ref: SlotRef, app: AppEntry) = store.update { it.setSlot(ref, app.key) }

    fun freeDestinations() = config.value.freeDestinations()

    fun addBlock(pageId: String, block: HomeBlock) = store.update { it.addBlock(pageId, block) }
    fun removeBlock(pageId: String, blockId: String) = store.update { it.removeBlock(pageId, blockId) }
    fun moveBlock(pageId: String, blockId: String, delta: Int) = store.update { it.moveBlock(pageId, blockId, delta) }
    fun renamePack(pageId: String, blockId: String, title: String) = store.update { it.renamePack(pageId, blockId, title) }

    fun addPage(title: String) = store.update { it.addPage(title) }
    fun removePage(pageId: String) = store.update { it.removePage(pageId) }
    fun renamePage(pageId: String, title: String) = store.update { it.renamePage(pageId, title) }
    fun movePage(pageId: String, delta: Int) = store.update { it.movePage(pageId, delta) }

    /** L'appli passe en arrière-plan : on écrit la config tout de suite. */
    fun flush() = store.flush()

    override fun onCleared() {
        repository.stop()
        store.flush()
    }
}
