package com.atelierjlg.fern.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atelierjlg.fern.keyboard.logic.KeyAction
import com.atelierjlg.fern.keyboard.logic.Page
import com.atelierjlg.fern.keyboard.logic.ShiftMode
import com.atelierjlg.fern.keyboard.logic.TextRules

/** À quoi sert la touche Entrée dans le champ actuel (son picto change). */
enum class EnterKind { NewLine, Send, Search, Go, Next, Previous, Done }

/**
 * Le « cerveau » du clavier : reçoit les touches et écrit dans le champ de texte de l'appli ouverte.
 * L'interface (KeyboardView) lit son état (page, majuscule…) et lui envoie les touches.
 *
 * Analogie web : c'est le JavaScript ; KeyboardView est le HTML/CSS.
 * L'« InputConnection » est le tuyau vers le champ de texte de l'autre appli.
 */
class KeyboardController(private val service: InputMethodService) {
    var page by mutableStateOf(Page.Lettres)
        private set
    var shift by mutableStateOf(ShiftMode.Off)
        private set
    var enterKind by mutableStateOf(EnterKind.NewLine)
        private set
    /** Texte copié il y a peu, proposé dans la barre du haut (null = rien à proposer). */
    var clip by mutableStateOf<String?>(null)
        private set
    var settings by mutableStateOf(KeyboardSettings())
        private set
    /** Augmente à chaque ouverture du clavier : relance l'animation de l'écume. */
    var openings by mutableIntStateOf(0)
        private set

    /** Comment gérer la majuscule auto dans ce champ. */
    private enum class Caps { None, Sentences, System }

    private var editorInfo: EditorInfo? = null
    private var caps = Caps.None
    private var autoShift = false
    private var lastShiftTap = 0L
    private var lastWasSpace = false
    private var pastedClip: String? = null

    private val ic get() = service.currentInputConnection

    // ─── Ouverture du clavier sur un champ ─────────────────────────────────────

    fun start(info: EditorInfo, restarting: Boolean) {
        editorInfo = info
        settings = KeyboardPrefs.load(service)
        val type = info.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        page = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Page.Numerique
            else -> Page.Lettres
        }
        caps = when {
            cls != InputType.TYPE_CLASS_TEXT -> Caps.None
            variation in noCapsVariations -> Caps.None
            type and (InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0 -> Caps.System
            type and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 -> Caps.Sentences
            variation in messageVariations -> Caps.Sentences
            // Un champ d'une ligne sans consigne (une recherche, souvent) : pas de majuscule imposée.
            else -> Caps.None
        }
        enterKind = resolveEnter(info)
        shift = ShiftMode.Off
        autoShift = false
        lastWasSpace = false
        refreshClip()
        updateAutoCaps()
        if (!restarting) openings++
    }

    private fun resolveEnter(info: EditorInfo): EnterKind {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterKind.NewLine
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> EnterKind.Send
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.Search
            EditorInfo.IME_ACTION_GO -> EnterKind.Go
            EditorInfo.IME_ACTION_NEXT -> EnterKind.Next
            EditorInfo.IME_ACTION_PREVIOUS -> EnterKind.Previous
            EditorInfo.IME_ACTION_DONE -> EnterKind.Done
            else -> EnterKind.NewLine
        }
    }

    // ─── Les touches ───────────────────────────────────────────────────────────

    fun onKey(action: KeyAction) {
        when (action) {
            is KeyAction.Text -> type(action.text)
            KeyAction.Delete -> delete()
            KeyAction.Space -> space()
            KeyAction.Enter -> enter()
            KeyAction.Shift -> shiftTap()
            is KeyAction.GoTo -> goTo(action.page)
        }
    }

    /** Écrit un texte (lettre, accent choisi à l'appui long, signe…). */
    fun type(text: String) {
        val ic = ic ?: return
        ic.commitText(TextRules.applyShift(text, shift), 1)
        lastWasSpace = false
        if (shift == ShiftMode.Once) {
            shift = ShiftMode.Off
            autoShift = false
        }
    }

    fun delete() {
        val ic = ic ?: return
        lastWasSpace = false
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(32, 0)
        if (before.isNullOrEmpty()) {
            // Champ vide : on envoie quand même la touche (certaines applis s'en servent).
            service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        // Efface un « caractère visible » entier : un emoji composé (👩‍👩‍👧, drapeaux…) compte pour un.
        val it = android.icu.text.BreakIterator.getCharacterInstance()
        it.setText(before.toString())
        val start = it.preceding(before.length).coerceAtLeast(0)
        ic.deleteSurroundingText(before.length - start, 0)
    }

    fun space() {
        val ic = ic ?: return
        val before = ic.getTextBeforeCursor(3, 0) ?: ""
        if (settings.autoPeriod && caps == Caps.Sentences && TextRules.doubleSpaceToPeriod(before, lastWasSpace)) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            lastWasSpace = false
        } else {
            ic.commitText(" ", 1)
            lastWasSpace = true
        }
        // Après un espace, on revient aux lettres (comme les autres claviers).
        if (page == Page.Symboles || page == Page.Symboles2) page = Page.Lettres
    }

    fun enter() {
        lastWasSpace = false
        val info = editorInfo
        if (enterKind == EnterKind.NewLine || info == null) {
            service.sendKeyChar('\n')
        } else {
            ic?.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        }
    }

    fun shiftTap() {
        val now = SystemClock.uptimeMillis()
        shift = TextRules.onShiftTap(shift, now - lastShiftTap)
        lastShiftTap = now
        autoShift = false
    }

    fun goTo(target: Page) {
        page = target
        if (target == Page.Lettres) updateAutoCaps()
    }

    /** Glisser sur la barre d'espace : déplace le curseur d'un caractère. */
    fun moveCursor(direction: Int) {
        lastWasSpace = false
        service.sendDownUpKeyEvents(if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    /** Appelé par Android quand le curseur bouge (après une frappe, ou un toucher dans le texte). */
    fun onSelectionChanged() = updateAutoCaps()

    private fun updateAutoCaps() {
        if (shift == ShiftMode.Locked) return
        val want = when {
            !settings.autoCaps || page != Page.Lettres -> false
            caps == Caps.Sentences -> TextRules.shouldCapitalize(ic?.getTextBeforeCursor(4, 0) ?: return)
            caps == Caps.System -> (ic?.getCursorCapsMode(editorInfo?.inputType ?: 0) ?: 0) != 0
            else -> false
        }
        if (want && shift == ShiftMode.Off) {
            shift = ShiftMode.Once
            autoShift = true
        } else if (!want && shift == ShiftMode.Once && autoShift) {
            shift = ShiftMode.Off
            autoShift = false
        }
    }

    // ─── Presse-papiers (dernier copié, proposé quelques minutes) ──────────────

    fun refreshClip() {
        val cm = service.getSystemService(ClipboardManager::class.java) ?: return
        val data = runCatching { cm.primaryClip }.getOrNull()
        val desc = data?.description
        // Les gestionnaires de mots de passe (Proton Pass…) marquent leurs copies comme sensibles :
        // le clavier ne les propose jamais.
        val sensitive = Build.VERSION.SDK_INT >= 33 &&
            desc?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        val recent = desc != null && System.currentTimeMillis() - desc.timestamp < CLIP_RECENT_MS
        val text = data?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            ?.let { runCatching { it.coerceToText(service) }.getOrNull() }
            ?.toString()?.trim()
        clip = if (!sensitive && recent && !text.isNullOrEmpty() && text != pastedClip) text else null
    }

    fun pasteClip() {
        val text = clip ?: return
        ic?.commitText(text, 1)
        pastedClip = text
        clip = null
        lastWasSpace = false
    }

    // ─── Retours (vibration, son) et petits services ──────────────────────────

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            service.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            service.getSystemService(Vibrator::class.java)
        }
    }
    private val audio: AudioManager? by lazy { service.getSystemService(AudioManager::class.java) }

    /** Petit « tic » au toucher d'une touche. */
    fun feedback(action: KeyAction) {
        if (settings.haptics) {
            runCatching { vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)) }
        }
        if (settings.sound) {
            val effect = when (action) {
                KeyAction.Delete -> AudioManager.FX_KEYPRESS_DELETE
                KeyAction.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyAction.Enter -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(effect, -1f)
        }
    }

    /** Appui long sur la barre d'espace : choisir un autre clavier. */
    fun pickKeyboard() {
        service.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    fun openSettings() {
        runCatching {
            service.startActivity(Intent(service, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        /** Un texte copié reste proposé 3 minutes. */
        private const val CLIP_RECENT_MS = 3 * 60_000L

        private val noCapsVariations = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
        )
        private val messageVariations = setOf(
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
        )
    }
}
