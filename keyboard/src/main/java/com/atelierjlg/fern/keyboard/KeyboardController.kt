package com.atelierjlg.fern.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.atelierjlg.fern.keyboard.logic.Predictor
import com.atelierjlg.fern.keyboard.logic.ShiftMode
import com.atelierjlg.fern.keyboard.logic.Strip
import com.atelierjlg.fern.keyboard.logic.TextRules
import com.atelierjlg.fern.keyboard.logic.Words

/** À quoi sert la touche Entrée dans le champ actuel (son picto change). */
enum class EnterKind { NewLine, Send, Search, Go, Next, Previous, Done }

/**
 * Le « cerveau » du clavier : reçoit les touches et écrit dans le champ de texte de l'appli ouverte.
 * L'interface (FernKeyboard) lit son état (page, majuscule, suggestions…) et lui envoie les touches.
 *
 * Analogie web : c'est le JavaScript ; FernKeyboard est le HTML/CSS.
 * L'« InputConnection » est le tuyau vers le champ de texte de l'autre appli.
 *
 * Le mot en cours de frappe est « en composition » (souligné dans le champ) : tant qu'on n'a pas tapé
 * espace ou ponctuation, on peut le remplacer (correction, suggestion choisie).
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
    /** Les 3 suggestions affichées (mot en cours, ou mot suivant). */
    var strip by mutableStateOf(Strip.Empty)
        private set

    /** Comment gérer la majuscule auto dans ce champ. */
    private enum class Caps { None, Sentences, System }

    /** Une correction qu'on peut annuler avec ⌫ juste après. */
    private data class Correction(val original: String, val corrected: String, val separator: String)

    private var editorInfo: EditorInfo? = null
    private var caps = Caps.None
    private var autoShift = false
    private var lastShiftTap = 0L
    private var lastWasSpace = false
    private var pastedClip: String? = null

    // Correction et suggestions
    private val composing = StringBuilder()
    private var composeOn = false
    private var correctOn = false
    private var suggestOn = false
    private var learnOn = false
    private var lastCorrection: Correction? = null
    /** Mot que Jules vient de rétablir (⌫) : on ne le recorrige pas. */
    private var keepAsIs: String? = null
    private var previousWord: String? = null
    private var beforePreviousWord: String? = null
    private var stripSeq = 0
    private var computedFor: String? = null
    private var computed: Strip = Strip.Empty
    private val main = Handler(Looper.getMainLooper())

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
        // Correction : seulement dans du vrai texte (jamais mots de passe, adresses, e-mails).
        val textField = cls == InputType.TYPE_CLASS_TEXT && variation !in noCapsVariations
        val noSuggestions = type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        correctOn = textField && !noSuggestions && settings.autoCorrect
        suggestOn = textField && !noSuggestions && settings.suggestions
        composeOn = correctOn || suggestOn
        // Apprentissage : jamais dans un champ « incognito » (navigation privée…).
        learnOn = composeOn && settings.learnWords &&
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        if (composeOn) Lexicon.ensureLoaded(service, settings.contactNames)

        enterKind = resolveEnter(info)
        shift = ShiftMode.Off
        autoShift = false
        lastWasSpace = false
        composing.clear()
        lastCorrection = null
        keepAsIs = null
        strip = Strip.Empty
        readPreviousWords()
        refreshClip()
        updateAutoCaps()
        if (!restarting) openings++
    }

    /** Le clavier se ferme : on laisse le mot en cours tel quel et on range ce qui a été appris. */
    fun finish() {
        if (composing.isNotEmpty()) {
            ic?.finishComposingText()
            composing.clear()
        }
        strip = Strip.Empty
        Lexicon.saveUser(service)
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
        val out = TextRules.applyShift(text, shift)
        lastWasSpace = false
        lastCorrection = null
        val c = out.singleOrNull()
        val startsWord = c != null && c.isLetterOrDigit()
        val continuesWord = c != null && composing.isNotEmpty() && Words.isWordChar(c)
        if (composeOn && (startsWord || continuesWord) && canCompose(ic)) {
            if (composing.isEmpty()) resumeWord(ic)
            composing.append(out)
            ic.setComposingText(composing, 1)
            refreshStrip()
        } else {
            // Ponctuation de fin de mot : on corrige le mot avant de la poser.
            val punctuation = c != null && c in WORD_END
            finishWord(autocorrect = punctuation, separator = out)
            if (punctuation) refreshPredictions() else strip = Strip.Empty
        }
        if (shift == ShiftMode.Once) {
            shift = ShiftMode.Off
            autoShift = false
        }
    }

    /** Pas de composition au milieu d'un mot (le texte après le curseur est une lettre). */
    private fun canCompose(ic: android.view.inputmethod.InputConnection): Boolean {
        if (composing.isNotEmpty()) return true
        val after = ic.getTextAfterCursor(1, 0)
        return after.isNullOrEmpty() || !Words.isWordChar(after[0])
    }

    /** On tape juste après un mot (sans espace) : on le reprend pour pouvoir le corriger en entier. */
    private fun resumeWord(ic: android.view.inputmethod.InputConnection) {
        val before = ic.getTextBeforeCursor(MAX_WORD, 0) ?: return
        val tail = Words.trailingWord(before)
        if (tail.isEmpty() || tail.length >= MAX_WORD) return
        ic.deleteSurroundingText(tail.length, 0)
        composing.append(tail)
    }

    /**
     * Termine le mot en cours : applique la correction (si demandée et sûre), puis pose le séparateur.
     */
    private fun finishWord(autocorrect: Boolean, separator: String = "") {
        val ic = ic ?: return
        if (composing.isEmpty()) {
            if (separator.isNotEmpty()) ic.commitText(separator, 1)
            return
        }
        val typed = composing.toString()
        composing.clear()
        val fixed = if (autocorrect && correctOn && typed != keepAsIs) autoCorrectionFor(typed) else null
        val final = fixed ?: typed
        ic.beginBatchEdit()
        ic.commitText(final, 1)
        if (separator.isNotEmpty()) ic.commitText(separator, 1)
        ic.endBatchEdit()
        lastCorrection = if (fixed != null && fixed != typed) Correction(typed, fixed, separator) else null
        keepAsIs = null
        remember(final, keptAsTyped = fixed == null)
    }

    /** La correction pour ce mot : celle déjà calculée pour la barre, sinon on la calcule maintenant. */
    private fun autoCorrectionFor(typed: String): String? {
        if (computedFor == typed) return computed.middle.takeIf { computed.middleIsCorrection }
        val corrector = Lexicon.corrector() ?: return null
        return runCatching { corrector.autoCorrect(typed) }.getOrNull()
    }

    /** Retient le mot (apprentissage) et met à jour le contexte pour le mot suivant. */
    private fun remember(word: String, keptAsTyped: Boolean) {
        val last = word.substringAfterLast(' ')
        val (elision, rest) = Words.splitElision(last)
        val previous = previousWord
        if (learnOn) {
            val corrector = Lexicon.corrector()
            Lexicon.edit { user ->
                if (keptAsTyped && rest.isNotEmpty() && corrector != null && !corrector.isKnown(last)) user.learn(rest)
                if (previous != null && rest.isNotEmpty()) user.learnPair(previous, rest)
            }
        }
        beforePreviousWord = if (elision.isNotEmpty()) elision.lowercase() else previous
        previousWord = rest.ifEmpty { null }
    }

    fun delete() {
        val ic = ic ?: return
        lastWasSpace = false
        // 1. ⌫ juste après une correction : on remet le mot tapé (et on le retient).
        lastCorrection?.let { c ->
            lastCorrection = null
            val expected = c.corrected + c.separator
            if (ic.getTextBeforeCursor(expected.length, 0)?.toString() == expected) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(expected.length, 0)
                composing.clear()
                composing.append(c.original)
                ic.setComposingText(composing, 1)
                ic.endBatchEdit()
                keepAsIs = c.original
                if (learnOn) Lexicon.edit { it.learn(Words.splitElision(c.original).second, confirmed = true) }
                refreshStrip()
                return
            }
        }
        // 2. Un mot en cours : on retire sa dernière lettre.
        if (composing.isNotEmpty()) {
            val it = android.icu.text.BreakIterator.getCharacterInstance()
            it.setText(composing.toString())
            val start = it.preceding(composing.length).coerceAtLeast(0)
            composing.setLength(start)
            if (composing.isEmpty()) {
                ic.commitText("", 1)
                strip = Strip.Empty
            } else {
                ic.setComposingText(composing, 1)
                refreshStrip()
            }
            return
        }
        strip = Strip.Empty
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
        if (composing.isNotEmpty()) {
            finishWord(autocorrect = true, separator = " ")
            lastWasSpace = true
        } else {
            val before = ic.getTextBeforeCursor(3, 0) ?: ""
            if (settings.autoPeriod && caps == Caps.Sentences && TextRules.doubleSpaceToPeriod(before, lastWasSpace)) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastWasSpace = false
                lastCorrection = null
                previousWord = null
                strip = Strip.Empty
                return
            }
            ic.commitText(" ", 1)
            lastWasSpace = true
            lastCorrection = null
        }
        // Après un espace, on revient aux lettres (comme les autres claviers).
        if (page == Page.Symboles || page == Page.Symboles2) page = Page.Lettres
        refreshPredictions()
    }

    fun enter() {
        lastWasSpace = false
        finishWord(autocorrect = true)
        lastCorrection = null
        strip = Strip.Empty
        val info = editorInfo
        if (enterKind == EnterKind.NewLine || info == null) {
            service.sendKeyChar('\n')
            previousWord = null
        } else {
            ic?.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        }
    }

    /** Toucher d'une suggestion : elle remplace le mot en cours (ou s'ajoute), suivie d'un espace. */
    fun pick(word: String) {
        val ic = ic ?: return
        val literal = composing.isNotEmpty() && word == composing.toString()
        ic.beginBatchEdit()
        ic.commitText(word, 1)
        ic.commitText(" ", 1)
        ic.endBatchEdit()
        composing.clear()
        lastCorrection = null
        keepAsIs = null
        lastWasSpace = true
        // Choisir le mot tel que tapé = « ce mot existe » : il est retenu tout de suite.
        if (literal && learnOn) Lexicon.edit { it.learn(Words.splitElision(word).second, confirmed = true) }
        remember(word, keptAsTyped = false)
        if (shift == ShiftMode.Once) {
            shift = ShiftMode.Off
            autoShift = false
        }
        refreshPredictions()
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
        if (composing.isNotEmpty()) {
            ic?.finishComposingText()
            composing.clear()
            strip = Strip.Empty
        }
        lastWasSpace = false
        lastCorrection = null
        service.sendDownUpKeyEvents(if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    /** Appelé par Android quand le curseur bouge (après une frappe, ou un toucher dans le texte). */
    fun onSelectionChanged(newSelStart: Int, newSelEnd: Int, candidatesEnd: Int) {
        // Jules a touché ailleurs dans le texte : on laisse le mot en cours tel quel.
        if (composing.isNotEmpty() && candidatesEnd >= 0 && (newSelStart != candidatesEnd || newSelEnd != newSelStart)) {
            ic?.finishComposingText()
            composing.clear()
            lastCorrection = null
            strip = Strip.Empty
        }
        updateAutoCaps()
    }

    // ─── Suggestions ──────────────────────────────────────────────────────────

    /** Recalcule (en arrière-plan) les suggestions pour le mot en cours. */
    private fun refreshStrip() {
        val word = composing.toString()
        val seq = ++stripSeq
        if (word.isEmpty()) {
            strip = Strip.Empty
            return
        }
        val corrector = Lexicon.corrector() ?: return
        Lexicon.worker.execute {
            val s = runCatching { corrector.strip(word) }.getOrNull() ?: return@execute
            main.post {
                if (seq != stripSeq) return@post
                computedFor = word
                computed = if (word == keepAsIs) s.withoutCorrection() else s
                strip = when {
                    !suggestOn -> Strip.Empty
                    !correctOn -> computed.withoutCorrection()
                    else -> computed
                }
            }
        }
    }

    /** Après un espace : les mots qui suivent souvent. */
    private fun refreshPredictions() {
        val seq = ++stripSeq
        val previous = previousWord
        val before = beforePreviousWord
        if (!suggestOn || previous == null) {
            strip = Strip.Empty
            return
        }
        Lexicon.worker.execute {
            val next = runCatching { Predictor.next(before, previous, Lexicon.user) }.getOrDefault(emptyList())
            main.post {
                if (seq != stripSeq) return@post
                strip = if (next.isEmpty()) Strip.Empty else Strip(left = next.getOrNull(1), middle = next[0], right = next.getOrNull(2))
            }
        }
    }

    /** Relit les deux derniers mots avant le curseur (pour proposer le mot suivant dès l'ouverture). */
    private fun readPreviousWords() {
        previousWord = null
        beforePreviousWord = null
        if (!composeOn) return
        val before = ic?.getTextBeforeCursor(60, 0)?.toString() ?: return
        if (!before.endsWith(" ")) return
        val words = before.trimEnd().split(' ').filter { it.isNotEmpty() }
        val last = words.lastOrNull()?.takeIf { w -> w.all { Words.isWordChar(it) } } ?: return
        val (elision, rest) = Words.splitElision(last)
        previousWord = rest
        beforePreviousWord = elision.ifEmpty { words.getOrNull(words.size - 2) }?.lowercase()
    }

    private fun updateAutoCaps() {
        if (shift == ShiftMode.Locked) return
        val want = when {
            !settings.autoCaps || page != Page.Lettres || composing.isNotEmpty() -> false
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
        finishWord(autocorrect = false)
        ic?.commitText(text, 1)
        pastedClip = text
        clip = null
        lastWasSpace = false
        lastCorrection = null
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
        private const val MAX_WORD = 40

        /** Ponctuations après lesquelles on corrige le mot qui précède. */
        private const val WORD_END = ".,;:!?…)»\""

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

/** La même barre, sans correction automatique : le mot tapé au milieu, la correction proposée à gauche. */
fun Strip.withoutCorrection(): Strip =
    if (!middleIsCorrection) this else Strip(left = middle, middle = left, right = right)
