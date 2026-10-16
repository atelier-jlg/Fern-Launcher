package com.atelierjlg.fern.keyboard.ui

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.atelierjlg.fern.keyboard.KeyboardController
import com.atelierjlg.fern.keyboard.KeyboardData
import com.atelierjlg.fern.keyboard.Panel
import com.atelierjlg.fern.keyboard.logic.ClipHistory
import com.atelierjlg.fern.keyboard.logic.EmojiIndex
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons

// ─── Barre d'outils (à la place des suggestions) ───────────────────────────

/** Presse-papiers, emoji, 3 emoji favoris d'un toucher, réglages. */
@Composable
fun RowScope.ToolbarRow(controller: KeyboardController) {
    val c = Fern.colors
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToolPlate(KeyIcons.Clipboard, "Presse-papiers", c.creme, c.nuit) { controller.openPanel(Panel.Clipboard) }
        ToolPlate(KeyIcons.Smile, "Emoji", c.roseCarmin, c.nuit) { controller.openPanel(Panel.Emoji) }
        ToolPlate(FernIcons.Image, "Memes et stickers", c.pistache, c.nuit) { controller.openPanel(Panel.Memes) }
        ToolPlate(FernIcons.Mic, "Dictée", c.sousBois, c.creme) { controller.startDictation() }
        Spacer(Modifier.weight(1f))
        controller.favorites.take(3).forEach { e ->
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { controller.insertEmoji(e) },
                contentAlignment = Alignment.Center,
            ) { Text(e, style = TextStyle(fontSize = 22.sp)) }
        }
    }
}

/** Une plaque ronde de couleur, comme les icônes de Fern. */
@Composable
private fun ToolPlate(icon: ImageVector, label: String, plate: androidx.compose.ui.graphics.Color, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(plate)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { FernIcon(icon, tint, size = 19.dp) }
}

/** Les suggestions de Proton Pass (vues dessinées par Android, posées telles quelles). */
@Composable
fun RowScope.InlineRow(controller: KeyboardController) {
    Row(
        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        controller.inlineViews.forEach { v ->
            key(v) {
                AndroidView(factory = {
                    (v.parent as? ViewGroup)?.removeView(v)
                    v
                })
            }
        }
    }
}

/** Le code SMS reçu (Fern Messages), à coller d'un toucher. */
@Composable
fun CodeChip(code: String, onClick: () -> Unit) {
    val c = Fern.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(c.pistache)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FernIcon(KeyIcons.Shield, c.nuit, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text("Code $code", color = c.nuit, style = TextStyle(fontFamily = labelFont, fontSize = 15.sp))
    }
}

// ─── Presse-papiers ────────────────────────────────────────────────────────

@Composable
fun ClipboardPanel(controller: KeyboardController, height: Dp) {
    val c = Fern.colors
    val clips = controller.clips
    Column(Modifier.fillMaxWidth().height(height).padding(horizontal = 6.dp)) {
        PanelHeader(
            "Presse-papiers · ${controller.settings.clipCount} derniers",
            onBack = { controller.closePanel() },
            action = if (clips.any { !it.pinned }) FernIcons.Trash to { controller.clearClips() } else null,
        )
        if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Rien de copié pour l'instant.\nLes mots de passe ne sont jamais gardés.",
                    color = c.lichen,
                    style = TextStyle(fontFamily = keyFont, fontSize = 14.sp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(clips, key = { it.text }) { entry -> ClipRow(entry, controller) }
            }
        }
    }
}

@Composable
private fun ClipRow(entry: ClipHistory.Entry, controller: KeyboardController) {
    val c = Fern.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (entry.pinned) c.sousBois else c.lierre)
            .clickable { controller.pickClip(entry.text) }
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.text.replace('\n', ' '),
                color = c.creme,
                style = TextStyle(fontFamily = keyFont, fontSize = 15.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.pinned) "ÉPINGLÉ" else ago(entry.time),
                color = if (entry.pinned) c.pistache else c.moussePale,
                style = TextStyle(fontFamily = labelFont, fontSize = 10.sp, letterSpacing = 1.sp),
            )
        }
        SmallIcon(KeyIcons.Pin, if (entry.pinned) c.pistache else c.moussePale, if (entry.pinned) "Désépingler" else "Épingler") {
            controller.togglePinClip(entry.text)
        }
        SmallIcon(FernIcons.Close, c.moussePale, "Retirer") { controller.removeClip(entry.text) }
    }
}

/** « Il y a 2 min », « Il y a 3 h », « Hier »… */
private fun ago(time: Long): String {
    val minutes = (System.currentTimeMillis() - time) / 60_000
    return when {
        minutes < 1 -> "À l'instant"
        minutes < 60 -> "Il y a $minutes min"
        minutes < 24 * 60 -> "Il y a ${minutes / 60} h"
        minutes < 48 * 60 -> "Hier"
        else -> "Il y a ${minutes / (24 * 60)} jours"
    }
}

// ─── Emoji ─────────────────────────────────────────────────────────────────

@Composable
fun EmojiPanel(controller: KeyboardController, height: Dp) {
    val c = Fern.colors
    val index = KeyboardData.emojis
    val tab = controller.emojiTab
    Column(Modifier.fillMaxWidth().height(height)) {
        // Onglets : recherche, favoris, récents, puis les groupes.
        LazyRow(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { TabIcon(FernIcons.Search, "Chercher", selected = false) { controller.startEmojiSearch() } }
            item { TabIcon(FernIcons.Star, "Favoris", selected = tab == -2) { controller.selectEmojiTab(-2) } }
            item { TabIcon(FernIcons.Clock, "Récents", selected = tab == -1) { controller.selectEmojiTab(-1) } }
            items(EmojiIndex.GROUP_ICONS.size) { g ->
                TabEmoji(EmojiIndex.GROUP_ICONS[g], EmojiIndex.GROUP_NAMES[g], selected = tab == g) { controller.selectEmojiTab(g) }
            }
        }
        val list: List<String> = when {
            tab == -2 -> controller.favorites
            tab == -1 -> controller.recents
            index == null -> emptyList()
            else -> remember(tab, controller.settings.skinTone, index) { index.groups.getOrNull(tab).orEmpty().map { controller.tone(it) } }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (list.isEmpty()) {
                Text(
                    when (tab) {
                        -2 -> "Appui long sur un emoji pour l'ajouter à tes favoris."
                        -1 -> "Tes emoji récents s'afficheront ici."
                        else -> "Chargement…"
                    },
                    color = c.lichen,
                    style = TextStyle(fontFamily = keyFont, fontSize = 14.sp),
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            } else {
                LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                    items(list, key = { it }) { e -> EmojiCell(e, controller) }
                }
            }
        }
        // En bas : retour aux lettres et effacer.
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BackPill { controller.closePanel() }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { controller.delete() }
                    .semantics { contentDescription = "Effacer" },
                contentAlignment = Alignment.Center,
            ) { FernIcon(FernIcons.Backspace, c.lichen, size = 20.dp) }
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, controller: KeyboardController) {
    val c = Fern.colors
    val favorite = emoji in controller.favorites
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (favorite && controller.emojiTab >= 0) c.lierre else c.mousse)
            .pointerInput(emoji) {
                detectTapGestures(
                    onTap = { controller.insertEmoji(emoji) },
                    onLongPress = {
                        controller.toggleFavorite(emoji)
                        controller.feedback(com.atelierjlg.fern.keyboard.logic.KeyAction.Text(emoji))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = TextStyle(fontSize = 26.sp)) }
}

/**
 * Recherche d'emoji : les résultats en haut, la recherche en dessous, puis les touches normales
 * (qui écrivent dans la recherche au lieu de l'appli).
 */
@Composable
fun EmojiSearchBar(controller: KeyboardController) {
    val c = Fern.colors
    val query = controller.emojiQuery.orEmpty()
    val index = KeyboardData.emojis
    val results = remember(query, index, controller.settings.skinTone) {
        index?.search(query, 40)?.map { controller.tone(it) }.orEmpty()
    }
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(results, key = { it }) { e ->
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable { controller.insertEmoji(e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, style = TextStyle(fontSize = 26.sp)) }
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallIcon(FernIcons.Back, c.lichen, "Fermer la recherche") { controller.closePanel() }
            Row(
                Modifier.weight(1f).height(36.dp).clip(CircleShape).background(c.lierre).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FernIcon(FernIcons.Search, c.lichen, size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    query.ifEmpty { "Cherche un emoji (chat, cœur, fête…)" },
                    color = if (query.isEmpty()) c.moussePale else c.creme,
                    style = TextStyle(fontFamily = keyFont, fontSize = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── Memes et stickers ─────────────────────────────────────────────────────

@Composable
fun MemesPanel(controller: KeyboardController, height: Dp) {
    val c = Fern.colors
    val filter = controller.memeFilter
    val shown = remember(controller.memes, filter) {
        if (filter == null) controller.memes else controller.memes.filter { filter in it.tags }
    }
    Column(Modifier.fillMaxWidth().height(height).padding(horizontal = 6.dp)) {
        PanelHeader("Memes et stickers", onBack = { controller.closePanel() }, action = FernIcons.Plus to { controller.openSettings() })
        if (controller.memeTags.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items(controller.memeTags, key = { it }) { tag ->
                    val on = tag == filter
                    Box(
                        Modifier.height(32.dp).clip(CircleShape).background(if (on) c.pistache else c.lierre)
                            .clickable { controller.selectMemeFilter(tag) }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(tag, color = if (on) c.nuit else c.creme, style = TextStyle(fontFamily = keyFont, fontSize = 14.sp)) }
                }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Pas encore de memes.\nAjoute tes images ou tes stickers de Fern dans les réglages (+).",
                    color = c.lichen,
                    style = TextStyle(fontFamily = keyFont, fontSize = 14.sp),
                )
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize().padding(top = 4.dp)) {
                items(shown, key = { it.id }) { meme -> MemeCell(meme, controller) }
            }
        }
    }
}

@Composable
private fun MemeCell(meme: com.atelierjlg.fern.keyboard.logic.MemeLibrary.Meme, controller: KeyboardController) {
    val c = Fern.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, meme.file) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.atelierjlg.fern.keyboard.Memes.thumbnail(context, meme) }.getOrNull() }
    }
    Box(
        Modifier.padding(3.dp).height(84.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.lierre)
            .clickable { controller.sendMeme(meme) },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                it.asImageBitmap(),
                contentDescription = meme.tags.joinToString(", ").ifEmpty { "meme" },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

// ─── Dictée ────────────────────────────────────────────────────────────────

/** Le micro au bout de la barre (quand il y a de la place). */
@Composable
fun MicButton(controller: KeyboardController) {
    val c = Fern.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.pistache).clickable { controller.startDictation() }
            .semantics { contentDescription = "Dicter" },
        contentAlignment = Alignment.Center,
    ) { FernIcon(FernIcons.Mic, c.nuit, size = 18.dp) }
}

/** Le panneau de dictée : l'onde, le gros micro, l'état (écoute, écriture). */
@Composable
fun DictationPanel(controller: KeyboardController, height: Dp) {
    val c = Fern.colors
    val d = controller.dictation
    val state = d.state
    val listening = state == com.atelierjlg.fern.keyboard.voice.Dictation.State.Listening
    // Le micro « respire » seulement pendant l'écoute (sinon, aucune animation ne tourne pour rien).
    val halo = if (listening) {
        val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "halo").animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(900),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "halo",
        )
        t
    } else 1f
    // Tout doit tenir dans la hauteur des touches (176 dp avec les petites) : l'onde prend ce qui reste.
    Column(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "DICTÉE · HORS LIGNE",
                color = c.lichen,
                style = TextStyle(fontFamily = labelFont, fontSize = 11.sp, letterSpacing = 1.4.sp),
                modifier = Modifier.weight(1f),
            )
            val model = controller.settings.whisperModel
            if (model.isNotEmpty()) {
                Box(Modifier.clip(CircleShape).background(c.lierre).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text(model, color = c.lichen, style = TextStyle(fontFamily = keyFont, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
        // L'onde : des barres arrondies qui suivent le volume de la voix.
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            d.levels.forEach { level ->
                Box(
                    Modifier.width(5.dp).fillMaxHeight(0.12f + 0.88f * level.coerceIn(0f, 1f)).clip(RoundedCornerShape(3.dp))
                        .background(if (level > 0.3f) c.pistache else if (level > 0.1f) c.lichen else c.sousBois),
                )
            }
        }
        // Le micro et ce qui se passe, côte à côte.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(68.dp).scale(halo).clip(CircleShape).background(c.lierre),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(if (listening) c.pistache else c.sousBois)
                        .clickable {
                            when (state) {
                                com.atelierjlg.fern.keyboard.voice.Dictation.State.Listening -> controller.stopDictation()
                                com.atelierjlg.fern.keyboard.voice.Dictation.State.Idle -> controller.startDictation()
                                else -> Unit
                            }
                        }
                        .semantics { contentDescription = if (listening) "Terminer" else "Dicter" },
                    contentAlignment = Alignment.Center,
                ) { FernIcon(FernIcons.Mic, if (listening) c.nuit else c.creme, size = 24.dp) }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                d.message ?: when (state) {
                    com.atelierjlg.fern.keyboard.voice.Dictation.State.Loading -> "Je me prépare…"
                    com.atelierjlg.fern.keyboard.voice.Dictation.State.Listening ->
                        if (controller.settings.autoStopDictation) "Je t'écoute… je m'arrête quand tu te tais" else "Je t'écoute… touche le micro pour finir"
                    com.atelierjlg.fern.keyboard.voice.Dictation.State.Transcribing -> "J'écris…"
                    com.atelierjlg.fern.keyboard.voice.Dictation.State.Idle -> "Touche le micro pour parler"
                },
                color = if (d.message != null) c.roseCarmin else c.creme,
                style = TextStyle(fontFamily = keyFont, fontSize = 15.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidePill("Annuler", Modifier.weight(1f)) { controller.cancelDictation() }
            WidePill(if (d.message != null) "Réglages" else "Clavier", Modifier.weight(1f)) {
                if (d.message != null) controller.openSettings()
                controller.cancelDictation()
            }
        }
    }
}

@Composable
private fun WidePill(text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Fern.colors
    Box(
        modifier.height(44.dp).clip(CircleShape).background(c.lierre).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.creme, style = TextStyle(fontFamily = labelFont, fontSize = 14.sp)) }
}

// ─── Petits morceaux communs ───────────────────────────────────────────────

@Composable
private fun PanelHeader(title: String, onBack: () -> Unit, action: Pair<ImageVector, () -> Unit>?) {
    val c = Fern.colors
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        BackPill(onBack)
        Spacer(Modifier.width(10.dp))
        Text(
            title.uppercase(),
            color = c.lichen,
            style = TextStyle(fontFamily = labelFont, fontSize = 11.sp, letterSpacing = 1.4.sp),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        action?.let { (icon, run) -> SmallIcon(icon, c.lichen, if (icon == FernIcons.Plus) "Ajouter" else "Tout effacer", run) }
    }
}

/** Retour aux lettres. */
@Composable
private fun BackPill(onClick: () -> Unit) {
    val c = Fern.colors
    Box(
        Modifier.height(40.dp).widthIn(min = 56.dp).clip(CircleShape).background(c.lierre).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text("ABC", color = c.lichen, style = TextStyle(fontFamily = labelFont, fontSize = 13.sp)) }
}

@Composable
private fun SmallIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { FernIcon(icon, tint, size = 18.dp) }
}

@Composable
private fun TabIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Fern.colors
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (selected) c.lierre else c.mousse).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { FernIcon(icon, if (selected) c.creme else c.lichen, size = 17.dp) }
}

@Composable
private fun TabEmoji(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Fern.colors
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (selected) c.lierre else c.mousse).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = TextStyle(fontSize = 18.sp)) }
}
