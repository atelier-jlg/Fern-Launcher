package com.atelierjlg.fern.keyboard

import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.atelierjlg.fern.keyboard.logic.WaveShape
import com.atelierjlg.fern.keyboard.ui.FernKeyboard
import com.atelierjlg.fern.theme.FernSharedTheme

/**
 * Le service de clavier qu'Android appelle quand un champ de texte a besoin d'un clavier.
 *
 * Compose a besoin d'un « cycle de vie » (comme une Activity : créé, visible, caché…). Un service
 * de clavier n'en a pas : on lui en fabrique un (LifecycleRegistry), qu'on fait avancer quand le
 * clavier s'affiche ou se cache.
 */
class FernKeyboardService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private lateinit var controller: KeyboardController
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { controller.refreshClip() }

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        controller = KeyboardController(this)
        getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onCreateInputView(): View {
        // La fenêtre du clavier : transparente (pour voir l'appli derrière la vague),
        // et dessinée jusque sous la barre de navigation.
        window.window?.let { w ->
            w.setFormat(PixelFormat.TRANSLUCENT)
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(w, false)
            @Suppress("DEPRECATION")
            w.navigationBarColor = Color.TRANSPARENT
            w.decorView.setViewTreeLifecycleOwner(this)
            w.decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FernKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@FernKeyboardService)
            setContent {
                FernSharedTheme { FernKeyboard(controller) }
            }
        }
    }

    // Pas de clavier « plein écran » en paysage : on garde l'appli visible au-dessus.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.start(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.finish()
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onSelectionChanged(newSelStart, newSelEnd, candidatesEnd)
    }

    /**
     * Ce que l'appli du dessous doit laisser libre. On annonce un haut un peu plus bas que la
     * vraie fenêtre : l'appli se prolonge sous le creux de la vague, et un toucher dans la partie
     * transparente arrive bien à l'appli.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!isFullscreenMode) {
            val shift = (WaveShape.BASE_DP * resources.displayMetrics.density).toInt()
            outInsets.contentTopInsets += shift
            outInsets.visibleTopInsets += shift
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        }
    }

    override fun onDestroy() {
        getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
