package com.atelierjlg.fern.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelierjlg.fern.keyboard.EnterKind
import com.atelierjlg.fern.keyboard.KeyboardController
import com.atelierjlg.fern.keyboard.Panel
import com.atelierjlg.fern.keyboard.logic.Key
import com.atelierjlg.fern.keyboard.logic.KeyAction
import com.atelierjlg.fern.keyboard.logic.Layouts
import com.atelierjlg.fern.keyboard.logic.Page
import com.atelierjlg.fern.keyboard.logic.ShiftMode
import com.atelierjlg.fern.keyboard.logic.TextRules
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlin.math.abs
import kotlin.math.roundToInt
import com.atelierjlg.fern.theme.R as ThemeR

/** Appui long avant d'afficher les accents. */
private const val LONG_PRESS_MS = 320L
/** Appui long sur l'espace : choisir un autre clavier. */
private const val LONG_PRESS_SPACE_MS = 600L

/** Bricolage Grotesque un peu étroite et demi-grasse, pour les lettres des touches. */
@OptIn(ExperimentalTextApi::class)
internal val keyFont = FontFamily(
    Font(
        resId = ThemeR.font.bricolage_grotesque,
        weight = FontWeight(600),
        variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.width(85f)),
    ),
)

@OptIn(ExperimentalTextApi::class)
internal val labelFont = FontFamily(
    Font(
        resId = ThemeR.font.bricolage_grotesque,
        weight = FontWeight(700),
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.width(100f)),
    ),
)

// ─── Ce qui flotte au-dessus des touches (aperçu de la lettre, choix des accents) ──

private data class Preview(val label: String, val bounds: Rect, val owner: Any)

@Stable
private class PopupState(
    /** Ce qui sera écrit (en minuscules : la majuscule est appliquée à l'écriture). */
    val raw: List<String>,
    /** Ce qui est affiché. */
    val labels: List<String>,
    val left: Float,
    val top: Float,
    val cell: Float,
) {
    var selected by mutableIntStateOf(0)
    fun indexAt(x: Float): Int = ((x - left) / cell).toInt().coerceIn(0, raw.lastIndex)
}

@Stable
private class Overlay {
    var preview by mutableStateOf<Preview?>(null)
    var popup by mutableStateOf<PopupState?>(null)
    var rootWidth = 0f
}

// ─── Le clavier ────────────────────────────────────────────────────────────

@Composable
fun FernKeyboard(controller: KeyboardController) {
    val c = Fern.colors
    val settings = controller.settings
    val overlay = remember { Overlay() }
    val page = controller.page
    val rows = remember(page, settings.numberRow) { Layouts.rows(page, settings.numberRow && page == Page.Lettres) }
    val keyHeight = settings.keyHeight.dp.dp

    Box(Modifier.fillMaxWidth().onGloballyPositioned { overlay.rootWidth = it.size.width.toFloat() }) {
        Column(Modifier.fillMaxWidth()) {
            Wave(openings = controller.openings, animate = settings.foamAnimation)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.mousse)
                    .navigationBarsPadding()
                    .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            ) {
                if (controller.emojiQuery != null) EmojiSearchBar(controller) else TopStrip(controller)
                // Les panneaux prennent exactement la place des touches (le clavier ne saute pas).
                val keysHeight = keyHeight * 4 + if (settings.numberRow) keyHeight * 0.82f else 0.dp
                val panel = controller.panel
                if (panel == Panel.Clipboard) ClipboardPanel(controller, keysHeight)
                if (panel == Panel.Emoji && controller.emojiQuery == null) EmojiPanel(controller, keysHeight)
                if (panel == Panel.Memes) MemesPanel(controller, keysHeight)
                if (panel == Panel.Dictation) DictationPanel(controller, keysHeight)
                val showKeys = panel == Panel.None || controller.emojiQuery != null
                if (showKeys) rows.forEachIndexed { index, row ->
                    // La rangée de chiffres est un peu plus basse ; le pavé numérique un peu plus haut.
                    val height = when {
                        settings.numberRow && page == Page.Lettres && index == 0 -> keyHeight * 0.82f
                        page == Page.Numerique -> keyHeight * 1.1f
                        else -> keyHeight
                    }
                    Row(Modifier.fillMaxWidth().height(height)) {
                        row.forEach { key ->
                            KeyCell(key, controller, overlay, Modifier.weight(key.width).fillMaxHeight())
                        }
                    }
                }
            }
        }
        OverlayLayer(overlay)
    }
}

/**
 * La barre du haut. À gauche, le bouton de la barre d'outils. Puis, selon le moment :
 * la barre d'outils · les suggestions de Proton Pass · les 3 suggestions de mots ·
 * le code SMS reçu / le dernier texte copié. Les réglages à droite quand il y a de la place.
 */
@Composable
private fun TopStrip(controller: KeyboardController) {
    val c = Fern.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (controller.toolbar) c.lierre else c.mousse)
                .clickable { controller.toggleToolbar() }
                .semantics { contentDescription = "Barre d'outils" },
            contentAlignment = Alignment.Center,
        ) {
            FernIcon(if (controller.toolbar) FernIcons.Close else KeyIcons.Grid, c.lichen, size = 18.dp)
        }
        Spacer(Modifier.width(4.dp))
        val strip = controller.strip
        val showSettings: Boolean
        when {
            controller.toolbar -> {
                ToolbarRow(controller)
                showSettings = true
            }
            controller.inlineViews.isNotEmpty() -> {
                InlineRow(controller)
                showSettings = false
            }
            !strip.isEmpty -> {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    SuggestionSlot(strip.left, literal = strip.leftIsLiteral, highlight = false, bold = false, controller)
                    SuggestionSlot(strip.middle, literal = false, highlight = strip.middleIsCorrection, bold = true, controller)
                    SuggestionSlot(strip.right, literal = false, highlight = false, bold = false, controller)
                    controller.stripEmoji?.let { e ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { controller.insertEmoji(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, style = TextStyle(fontSize = 22.sp)) }
                    }
                }
                showSettings = false
            }
            else -> {
                Box(Modifier.weight(1f)) {
                    val code = controller.smsCode
                    val clip = controller.clip
                    val notice = controller.notice
                    if (notice != null) {
                        Text(
                            notice,
                            color = c.lichen,
                            style = TextStyle(fontFamily = keyFont, fontSize = 13.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp),
                        )
                    } else if (code != null) {
                        CodeChip(code) { controller.pasteCode() }
                    } else if (clip != null) {
                        Row(
                            Modifier
                                .widthIn(max = 260.dp)
                                .clip(CircleShape)
                                .background(c.lierre)
                                .clickable { controller.pasteClip() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FernIcon(FernIcons.Copy, c.lichen, size = 16.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                clip.replace('\n', ' '),
                                color = c.creme,
                                style = TextStyle(fontFamily = keyFont, fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                showSettings = true
            }
        }
        if (showSettings && !controller.toolbar) {
            MicButton(controller)
        } else if (showSettings) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { controller.openSettings() }
                    .semantics { contentDescription = "Réglages du clavier" },
                contentAlignment = Alignment.Center,
            ) {
                FernIcon(FernIcons.Settings, c.lichen, size = 18.dp)
            }
        }
    }
}

/** Une case de suggestion. La correction qui sera appliquée à l'espace est en pistache. */
@Composable
private fun RowScope.SuggestionSlot(word: String?, literal: Boolean, highlight: Boolean, bold: Boolean, controller: KeyboardController) {
    val c = Fern.colors
    Box(
        Modifier
            .weight(1f)
            .height(40.dp)
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .background(if (highlight) c.pistache else c.mousse)
            .then(if (word != null) Modifier.clickable { controller.pick(word) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (word != null) {
            Text(
                if (literal) "« $word »" else word,
                color = when {
                    highlight -> c.nuit
                    bold -> c.creme
                    else -> c.lichen
                },
                style = TextStyle(fontFamily = if (bold) labelFont else keyFont, fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

// ─── Une touche ────────────────────────────────────────────────────────────

@Composable
private fun KeyCell(key: Key, controller: KeyboardController, overlay: Overlay, modifier: Modifier) {
    val c = Fern.colors
    val bounds = remember { mutableStateOf(Rect.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val shift = controller.shift

    val gestures = Modifier.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            controller.feedback(key.action)
            pressed = true
            when (val action = key.action) {
                is KeyAction.Text -> {
                    val shown = TextRules.applyShift(key.label, controller.shift)
                    overlay.preview = Preview(shown, bounds.value, key)
                    val released = if (key.popup.isEmpty()) {
                        awaitRelease(down.id)
                    } else {
                        withTimeoutOrNull(LONG_PRESS_MS) { awaitRelease(down.id) }
                    }
                    when (released) {
                        true -> controller.onKey(action)
                        false -> Unit
                        null -> {
                            // Appui long : la rangée d'accents (ou de signes) s'ouvre au-dessus.
                            overlay.preview = null
                            val popup = createPopup(key, controller.shift, bounds.value, overlay.rootWidth, this)
                            overlay.popup = popup
                            val ok = trackPopup(down.id, bounds.value, popup)
                            overlay.popup = null
                            if (ok) controller.type(popup.raw[popup.selected])
                        }
                    }
                }
                KeyAction.Delete -> {
                    // Effacer, puis répéter tant que le doigt reste posé (de plus en plus vite).
                    controller.delete()
                    var interval = 420L
                    var count = 0
                    while (withTimeoutOrNull(interval) { awaitRelease(down.id) } == null) {
                        controller.delete()
                        count++
                        interval = if (count > 15) 30L else 60L
                    }
                }
                KeyAction.Space -> {
                    val canDrag = controller.settings.cursorOnSpace
                    val intent = withTimeoutOrNull(LONG_PRESS_SPACE_MS) {
                        awaitSpaceIntent(down.id, down.position.x, viewConfiguration.touchSlop, canDrag)
                    }
                    when (intent) {
                        SpaceIntent.Tap -> controller.space()
                        SpaceIntent.Drag -> {
                            // Glisser sur l'espace : le curseur suit le doigt, un caractère tous les 10 dp.
                            dragCursor(down.id, down.position.x, 10.dp.toPx(), controller)
                        }
                        SpaceIntent.Cancel -> Unit
                        null -> {
                            controller.pickKeyboard()
                            awaitRelease(down.id)
                        }
                    }
                }
                else -> if (awaitRelease(down.id)) controller.onKey(action)
            }
            pressed = false
            if (overlay.preview?.owner == key) overlay.preview = null
        }
    }

    Box(
        modifier
            .onGloballyPositioned { bounds.value = it.boundsInRoot() }
            .then(gestures),
        contentAlignment = Alignment.Center,
    ) {
        when (key.action) {
            is KeyAction.Text -> TextKey(key, shift, pressed, special = key.special)
            KeyAction.Shift -> RoundKey(pressed, filled = shift != ShiftMode.Off, locked = shift == ShiftMode.Locked) {
                FernIcon(
                    if (shift == ShiftMode.Locked) KeyIcons.ShiftLocked else KeyIcons.Shift,
                    when (shift) {
                        ShiftMode.Off -> c.lichen
                        ShiftMode.Once -> c.creme
                        ShiftMode.Locked -> c.nuit
                    },
                    size = 20.dp,
                    contentDescription = "Majuscule",
                )
            }
            KeyAction.Delete -> RoundKey(pressed) {
                FernIcon(FernIcons.Backspace, c.lichen, size = 20.dp, contentDescription = "Effacer")
            }
            KeyAction.Space -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clip(CircleShape)
                    .background(if (pressed) c.sousBois else c.lierre),
                contentAlignment = Alignment.Center,
            ) {
                if (controller.page != Page.Numerique) {
                    Text(
                        "FERN",
                        color = c.moussePale,
                        style = TextStyle(fontFamily = labelFont, fontSize = 11.sp, letterSpacing = 1.8.sp),
                    )
                }
            }
            KeyAction.Enter -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clip(CircleShape)
                    .background(if (pressed) c.roseCarmin else c.carmin),
                contentAlignment = Alignment.Center,
            ) {
                val (icon, label) = when (controller.enterKind) {
                    EnterKind.NewLine -> KeyIcons.Return to "Retour à la ligne"
                    EnterKind.Send -> FernIcons.Send to "Envoyer"
                    EnterKind.Search -> FernIcons.Search to "Rechercher"
                    EnterKind.Go, EnterKind.Next -> KeyIcons.ArrowRight to "Suivant"
                    EnterKind.Previous -> FernIcons.Back to "Précédent"
                    EnterKind.Done -> FernIcons.Check to "Terminé"
                }
                FernIcon(icon, c.creme, size = 20.dp, contentDescription = label)
            }
            is KeyAction.GoTo -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clip(CircleShape)
                    .background(if (pressed) c.lierre else c.mousse)
                    .border(1.5.dp, c.sousBois, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(key.label, color = c.lichen, style = TextStyle(fontFamily = labelFont, fontSize = 13.sp))
            }
        }
    }
}

/** Une touche de lettre ou de signe : la lettre seule, posée sur la vague (pas de plaque). */
@Composable
private fun TextKey(key: Key, shift: ShiftMode, pressed: Boolean, special: Boolean) {
    val c = Fern.colors
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) c.lierre else c.mousse),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            TextRules.applyShift(key.label, shift),
            color = if (special) c.lichen else c.creme,
            style = TextStyle(fontFamily = keyFont, fontSize = 24.sp),
        )
        key.hint?.let {
            Text(
                it,
                color = c.moussePale,
                style = TextStyle(fontFamily = keyFont, fontSize = 10.sp),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}

/** Touche ronde cerclée (majuscule, effacer). */
@Composable
private fun RoundKey(pressed: Boolean, filled: Boolean = false, locked: Boolean = false, content: @Composable () -> Unit) {
    val c = Fern.colors
    val bg = when {
        locked -> c.pistache
        filled || pressed -> c.lierre
        else -> c.mousse
    }
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, if (locked) c.pistache else c.sousBois, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Aperçu de la lettre au-dessus du doigt, et rangée d'accents à l'appui long. */
@Composable
private fun OverlayLayer(overlay: Overlay) {
    val c = Fern.colors
    val density = LocalDensity.current
    overlay.preview?.let { p ->
        val w = with(density) { p.bounds.width.toDp() } + 12.dp
        val h = 58.dp
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (p.bounds.center.x - w.toPx() / 2f).roundToInt(),
                        (p.bounds.top - h.toPx() - 2.dp.toPx()).roundToInt(),
                    )
                }
                .size(w, h)
                .clip(RoundedCornerShape(16.dp))
                .background(c.lierre),
            contentAlignment = Alignment.Center,
        ) {
            Text(p.label, color = c.creme, style = TextStyle(fontFamily = keyFont, fontSize = 30.sp))
        }
    }
    overlay.popup?.let { popup ->
        val cell: Dp = with(density) { popup.cell.toDp() }
        Row(
            Modifier
                .offset { IntOffset(popup.left.roundToInt(), popup.top.roundToInt()) }
                .clip(RoundedCornerShape(18.dp))
                .background(c.lierre),
        ) {
            popup.labels.forEachIndexed { i, label ->
                val selected = i == popup.selected
                Box(
                    Modifier
                        .size(cell, 52.dp)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) c.pistache else c.lierre),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (selected) c.nuit else c.creme, style = TextStyle(fontFamily = keyFont, fontSize = 24.sp))
                }
            }
        }
    }
}

// ─── Gestes ────────────────────────────────────────────────────────────────

/** Attend que le doigt se lève (même s'il a glissé hors de la touche). true = levé, false = annulé. */
private suspend fun AwaitPointerEventScope.awaitRelease(id: PointerId): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return false
        if (!change.pressed) return true
    }
}

private fun createPopup(
    key: Key,
    shift: ShiftMode,
    bounds: Rect,
    rootWidth: Float,
    density: androidx.compose.ui.unit.Density,
): PopupState {
    val margin = with(density) { 4.dp.toPx() }
    val cell = maxOf(bounds.width, with(density) { 40.dp.toPx() })
    val total = cell * key.popup.size
    // La première variante (celle choisie par défaut) au-dessus de la touche, sans sortir de l'écran.
    var left = bounds.center.x - cell / 2f
    if (left + total > rootWidth - margin) left = rootWidth - margin - total
    if (left < margin) left = margin
    val top = bounds.top - with(density) { 56.dp.toPx() }
    return PopupState(
        raw = key.popup,
        labels = key.popup.map { TextRules.applyShift(it, shift) },
        left = left,
        top = top,
        cell = cell,
    )
}

/** Suit le doigt dans la rangée d'accents. true = levé sur un choix. */
private suspend fun AwaitPointerEventScope.trackPopup(id: PointerId, keyBounds: Rect, popup: PopupState): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return false
        popup.selected = popup.indexAt(keyBounds.left + change.position.x)
        if (!change.pressed) return true
        change.consume()
    }
}

private enum class SpaceIntent { Tap, Drag, Cancel }

private suspend fun AwaitPointerEventScope.awaitSpaceIntent(
    id: PointerId,
    startX: Float,
    slop: Float,
    canDrag: Boolean,
): SpaceIntent {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return SpaceIntent.Cancel
        if (!change.pressed) return SpaceIntent.Tap
        if (canDrag && abs(change.position.x - startX) > slop) return SpaceIntent.Drag
    }
}

private suspend fun AwaitPointerEventScope.dragCursor(id: PointerId, startX: Float, step: Float, controller: KeyboardController) {
    var anchor = startX
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        val steps = ((change.position.x - anchor) / step).toInt()
        if (steps != 0) {
            repeat(abs(steps)) { controller.moveCursor(if (steps < 0) -1 else 1) }
            anchor += steps * step
        }
        change.consume()
    }
}
