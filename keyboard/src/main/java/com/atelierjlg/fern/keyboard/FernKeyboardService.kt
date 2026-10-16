package com.atelierjlg.fern.keyboard

import android.content.ClipboardManager
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.graphics.toArgb
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
    private val codeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = controller.refreshCode()
    }

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        controller = KeyboardController(this)
        getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipListener)
        // Un nouveau code SMS arrive dans Fern Messages pendant que le clavier est ouvert.
        runCatching { contentResolver.registerContentObserver(KeyboardController.CODES_URI, false, codeObserver) }
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

    // ─── Suggestions des gestionnaires de mots de passe (Android 11+) ───
    // Proton Pass propose ses identifiants directement dans la barre du clavier. Le clavier ne voit
    // jamais le mot de passe : Android dessine la suggestion et remplit le champ lui-même.

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest {
        val d = resources.displayMetrics.density
        fun px(dp: Int) = (dp * d).toInt()
        val colors = com.atelierjlg.fern.theme.FernThemeReader.cached(this).colors
        val style = InlineSuggestionUi.newStyleBuilder()
            .setChipStyle(
                ViewStyle.Builder()
                    .setBackground(Icon.createWithResource(this, R.drawable.inline_chip))
                    .setPadding(px(12), 0, px(12), 0)
                    .build(),
            )
            .setTitleStyle(TextViewStyle.Builder().setTextColor(colors.creme.toArgb()).setTextSize(15f).build())
            .setSubtitleStyle(TextViewStyle.Builder().setTextColor(colors.lichen.toArgb()).setTextSize(12f).build())
            .setStartIconStyle(ImageViewStyle.Builder().setLayoutMargin(0, 0, px(8), 0).build())
            .setSingleIconChipStyle(
                ViewStyle.Builder()
                    .setBackground(Icon.createWithResource(this, R.drawable.inline_chip))
                    .setPadding(px(10), 0, px(10), 0)
                    .build(),
            )
            .build()
        val styles = UiVersions.newStylesBuilder().addStyle(style).build()
        val spec = InlinePresentationSpec.Builder(Size(px(64), px(40)), Size(px(280), px(40)))
            .setStyle(styles)
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(6).build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val list = response.inlineSuggestions
        if (list.isEmpty()) {
            controller.setInline(emptyList())
            return true
        }
        val views = arrayOfNulls<View>(list.size)
        var remaining = list.size
        list.forEachIndexed { i, suggestion ->
            val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            suggestion.inflate(this, size, mainExecutor) { view ->
                views[i] = view
                remaining--
                if (remaining == 0) controller.setInline(views.filterNotNull())
            }
        }
        return true
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(codeObserver) }
        getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener)
        if (::controller.isInitialized) controller.destroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
