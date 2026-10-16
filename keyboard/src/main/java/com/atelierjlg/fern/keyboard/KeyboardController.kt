package com.atelierjlg.fern.keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.view.View
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
import com.atelierjlg.fern.keyboard.logic.ClipHistory
import com.atelierjlg.fern.keyboard.logic.EmojiIndex
import com.atelierjlg.fern.keyboard.logic.KeyAction
import com.atelierjlg.fern.keyboard.logic.MemeLibrary
import com.atelierjlg.fern.keyboard.logic.Page
import com.atelierjlg.fern.keyboard.logic.Predictor
import com.atelierjlg.fern.keyboard.logic.SpeechText
import com.atelierjlg.fern.keyboard.logic.ShiftMode
import com.atelierjlg.fern.keyboard.logic.Strip
import com.atelierjlg.fern.keyboard.logic.TextRules
import com.atelierjlg.fern.keyboard.logic.Words
import com.atelierjlg.fern.keyboard.voice.Dictation

/** À quoi sert la touche Entrée dans le champ actuel (son picto change). */
enum class EnterKind { NewLine, Send, Search, Go, Next, Previous, Done }

/** Ce qui remplace les touches : rien (les touches), le presse-papiers, les emoji, les memes, la dictée. */
enum class Panel { None, Clipboard, Emoji, Memes, Dictation }

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
    /** Un emoji qui va avec le mot en cours (« chat » → 🐈), proposé au bout de la barre. */
    var stripEmoji by mutableStateOf<String?>(null)
        private set

    // ─── Barre d'outils et panneaux (v0.3) ───
    var panel by mutableStateOf(Panel.None)
        private set
    /** La barre d'outils est ouverte à la place des suggestions. */
    var toolbar by mutableStateOf(false)
        private set
    /** Recherche d'emoji en cours (null = pas de recherche) : les touches écrivent ici. */
    var emojiQuery by mutableStateOf<String?>(null)
        private set
    /** Onglet des emoji : -2 favoris, -1 récents, 0… groupes. */
    var emojiTab by mutableIntStateOf(-1)
        private set
    var clips by mutableStateOf<List<ClipHistory.Entry>>(emptyList())
        private set
    var favorites by mutableStateOf<List<String>>(emptyList())
        private set
    var recents by mutableStateOf<List<String>>(emptyList())
        private set
    /** Le dernier code de vérification reçu par Fern Messages (10 minutes). */
    var smsCode by mutableStateOf<String?>(null)
        private set
    /** Suggestions de Proton Pass (ou d'un autre gestionnaire de mots de passe), déjà dessinées par Android. */
    var inlineViews by mutableStateOf<List<View>>(emptyList())
        private set
    private var usedCode: String? = null

    // ─── Memes et stickers (v0.4) ───
    var memes by mutableStateOf<List<MemeLibrary.Meme>>(emptyList())
        private set
    var memeTags by mutableStateOf<List<String>>(emptyList())
        private set
    /** Filtre par mot-clé (null = tous). */
    var memeFilter by mutableStateOf<String?>(null)
        private set
    /** Petit message dans la barre (« Image copiée… »), effacé à la prochaine touche. */
    var notice by mutableStateOf<String?>(null)
        private set

    /**
     * La barre du haut a quelque chose à montrer (suggestions, code, texte copié, outils…).
     * Sinon, avec l'option « rangée de chiffres », ce sont les chiffres qui prennent sa place.
     */
    val stripBusy: Boolean
        get() = toolbar || inlineViews.isNotEmpty() || !strip.isEmpty ||
            smsCode != null || clip != null || notice != null

    init {
        Memes.observe {
            memes = Memes.library.all
            memeTags = Memes.library.topTags(12)
        }
    }

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
        KeyboardData.ensureLoaded(service, settings.clipCount) { refreshData() }
        Memes.ensureLoaded(service)
        notice = null
        panel = Panel.None
        toolbar = false
        emojiQuery = null
        stripEmoji = null
        refreshCode()

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
        if (panel == Panel.Dictation) dictation.cancel()
        inlineViews = emptyList()
        panel = Panel.None
        emojiQuery = null
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
        notice = null
        // Recherche d'emoji : les touches écrivent dans la recherche, pas dans l'appli.
        emojiQuery?.let { q ->
            emojiQuery = q + TextRules.applyShift(text, shift)
            if (shift == ShiftMode.Once) shift = ShiftMode.Off
            return
        }
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
        emojiQuery?.let { q ->
            emojiQuery = q.dropLast(1)
            return
        }
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
        emojiQuery?.let { q ->
            emojiQuery = "$q "
            return
        }
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
        if (emojiQuery != null) {
            emojiQuery = null
            return
        }
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
        val emojis = if (settings.emojiSuggestions) KeyboardData.emojis else null
        Lexicon.worker.execute {
            val s = runCatching { corrector.strip(word) }.getOrNull() ?: return@execute
            val emoji = runCatching { emojis?.forWord(Words.splitElision(word).second) }.getOrNull()
            main.post {
                if (seq != stripSeq) return@post
                stripEmoji = emoji?.let { tone(it) }
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
        stripEmoji = null
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
        // Historique : tout ce qui n'est pas sensible (même ancien), pour le tiroir du presse-papiers.
        if (!sensitive && !text.isNullOrEmpty() && settings.clipCount > 0) {
            if (KeyboardData.clips.add(text, desc?.timestamp ?: System.currentTimeMillis())) {
                KeyboardData.saveClips(service)
                clips = KeyboardData.clips.entries
            }
        }
    }

    fun pasteClip() {
        val text = clip ?: return
        pasteText(text)
        pastedClip = text
        clip = null
    }

    /** Colle un texte (presse-papiers, code SMS…) à la place du mot en cours. */
    fun pasteText(text: String) {
        finishWord(autocorrect = false)
        ic?.commitText(text, 1)
        lastWasSpace = false
        lastCorrection = null
        strip = Strip.Empty
    }

    // ─── Code SMS (prêté par Fern Messages) ────────────────────────────────

    fun refreshCode() {
        val found = runCatching {
            service.contentResolver.query(CODES_URI, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        smsCode = found?.takeIf { it != usedCode }
    }

    fun pasteCode() {
        val code = smsCode ?: return
        pasteText(code)
        usedCode = code
        smsCode = null
    }

    // ─── Barre d'outils, presse-papiers, emoji ─────────────────────────────

    /** Relit les données (après chargement ou changement). */
    private fun refreshData() {
        clips = KeyboardData.clips.entries
        favorites = KeyboardData.memory.favorites.toList()
        recents = KeyboardData.memory.recents.toList()
        if (emojiTab == -1 && recents.isEmpty()) emojiTab = if (favorites.isNotEmpty()) -2 else 0
    }

    fun toggleToolbar() {
        toolbar = !toolbar
    }

    fun openPanel(target: Panel) {
        finishWord(autocorrect = false)
        strip = Strip.Empty
        toolbar = false
        emojiQuery = null
        panel = if (panel == target) Panel.None else target
        if (panel == Panel.Emoji && emojiTab == -1 && recents.isEmpty()) emojiTab = if (favorites.isNotEmpty()) -2 else 0
    }

    fun closePanel() {
        panel = Panel.None
        emojiQuery = null
    }

    fun pickClip(text: String) {
        closePanel()
        pasteText(text)
    }

    fun togglePinClip(text: String) {
        KeyboardData.clips.togglePin(text)
        KeyboardData.saveClips(service)
        clips = KeyboardData.clips.entries
    }

    fun removeClip(text: String) {
        KeyboardData.clips.remove(text)
        KeyboardData.saveClips(service)
        clips = KeyboardData.clips.entries
    }

    fun clearClips() {
        KeyboardData.clips.clearUnpinned()
        KeyboardData.saveClips(service)
        clips = KeyboardData.clips.entries
        clip = null
    }

    fun selectEmojiTab(tab: Int) {
        emojiTab = tab
    }

    fun startEmojiSearch() {
        emojiQuery = ""
        page = Page.Lettres
        shift = ShiftMode.Off
    }

    /** Écrit un emoji (avec la teinte de peau choisie) et le range dans les récents. */
    fun insertEmoji(emoji: String) {
        finishWord(autocorrect = true, separator = if (composing.isNotEmpty()) " " else "")
        ic?.commitText(emoji, 1)
        lastWasSpace = false
        lastCorrection = null
        strip = Strip.Empty
        stripEmoji = null
        KeyboardData.memory.used(emoji)
        KeyboardData.saveMemory(service)
        recents = KeyboardData.memory.recents.toList()
    }

    /** Teinte de peau des réglages appliquée à un emoji du clavier. */
    fun tone(e: com.atelierjlg.fern.keyboard.logic.Emoji): String = EmojiIndex.withSkinTone(e, settings.skinTone)

    /** Appui long sur un emoji : favori (ou plus favori). */
    fun toggleFavorite(emoji: String): Boolean {
        val now = KeyboardData.memory.toggleFavorite(emoji)
        KeyboardData.saveMemory(service)
        favorites = KeyboardData.memory.favorites.toList()
        return now
    }

    // ─── Memes ─────────────────────────────────────────────────────────────

    fun selectMemeFilter(tag: String?) {
        memeFilter = if (memeFilter == tag) null else tag
    }

    /**
     * Envoie un meme dans l'appli ouverte. Si elle accepte les images venues du clavier (Signal,
     * WhatsApp…), il arrive directement dans la conversation ; sinon on passe par le partage d'Android,
     * vers la même appli (Fern Messages le prend ainsi).
     */
    fun sendMeme(meme: MemeLibrary.Meme) {
        val info = editorInfo ?: return
        val file = Memes.file(service, meme)
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(service, "${service.packageName}.images", file)
        }.getOrNull() ?: return
        finishWord(autocorrect = true)
        val accepted = androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(info)
            .any { ClipDescription.compareMimeTypes(meme.mime, it) }
        val ic = ic
        val sent = accepted && ic != null && androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
            ic,
            info,
            androidx.core.view.inputmethod.InputContentInfoCompat(uri, ClipDescription("meme", arrayOf(meme.mime)), null),
            androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
        if (!sent) shareImage(uri, meme.mime, info.packageName)
        Memes.used(service, meme.id)
        closePanel()
    }

    private fun shareImage(uri: Uri, mime: String, target: String?) {
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        send.clipData = android.content.ClipData.newRawUri("meme", uri)
        val direct = target?.let { Intent(send).setPackage(it) }
        val ok = direct != null && runCatching { service.startActivity(direct) }.isSuccess
        if (!ok) {
            runCatching {
                service.startActivity(
                    Intent.createChooser(send, "Envoyer l'image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }
        notice = "Cette appli ne prend pas les images du clavier : choisis la conversation."
    }

    // ─── Dictée (Whisper, hors ligne) ──────────────────────────────────────

    val dictation = Dictation(service)

    /** Toucher du micro : le panneau de dictée s'ouvre et l'écoute commence. */
    fun startDictation() {
        finishWord(autocorrect = true)
        strip = Strip.Empty
        toolbar = false
        emojiQuery = null
        panel = Panel.Dictation
        dictation.start(settings.autoStopDictation) { text -> insertDictation(text) }
    }

    /** Toucher du gros micro pendant l'écoute : fin de la prise de parole. */
    fun stopDictation() = dictation.stop()

    fun cancelDictation() {
        dictation.cancel()
        closePanel()
    }

    private fun insertDictation(text: String) {
        val ic = ic ?: return
        val before = ic.getTextBeforeCursor(4, 0) ?: ""
        ic.commitText(SpeechText.forInsertion(text, before), 1)
        lastWasSpace = false
        lastCorrection = null
        closePanel()
        val words = text.trimEnd('.', '!', '?', ' ').split(' ')
        previousWord = words.lastOrNull()?.let { Words.splitElision(it).second }
    }

    // ─── Proton Pass & co (suggestions « en ligne ») ──────────────────────

    fun setInline(views: List<View>) {
        inlineViews = views
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
        /** Le dernier code SMS, prêté par Fern Messages (permission de niveau signature). */
        val CODES_URI: Uri = Uri.parse("content://com.atelierjlg.fern.messages.codes/latest")

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
