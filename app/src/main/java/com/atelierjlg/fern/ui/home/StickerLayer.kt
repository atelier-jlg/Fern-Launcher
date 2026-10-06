package com.atelierjlg.fern.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.data.Sticker
import com.atelierjlg.fern.ui.common.GlyphButton
import com.atelierjlg.fern.ui.theme.Fern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Les stickers d'une page, posés librement (sans grille), par-dessus les packs et widgets.
 *
 * En mode édition : un doigt pour déplacer, deux doigts pour agrandir / tourner, ✕ pour retirer.
 * Hors mode édition, ils ne captent aucun toucher (les applis dessous restent accessibles).
 */
@Composable
fun StickerLayer(
    stickers: List<Sticker>,
    editing: Boolean,
    stickerFile: (String) -> File,
    onChange: (Sticker) -> Unit,
    onDelete: (Sticker) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        for (sticker in stickers) {
            // key : chaque sticker garde son propre état même si la liste change.
            androidx.compose.runtime.key(sticker.id) {
                StickerView(
                    sticker = sticker,
                    editing = editing,
                    file = stickerFile(sticker.file),
                    pageWidth = widthPx,
                    pageHeight = heightPx,
                    onChange = onChange,
                    onDelete = { onDelete(sticker) },
                )
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun StickerView(
    sticker: Sticker,
    editing: Boolean,
    file: File,
    pageWidth: Float,
    pageHeight: Float,
    onChange: (Sticker) -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    // État local pendant le geste (fluide), enregistré 300 ms après la fin du mouvement.
    var current by remember(sticker.id) { mutableStateOf(sticker) }
    val save by rememberUpdatedState(onChange)
    LaunchedEffect(sticker.id) {
        snapshotFlow { current }.drop(1).debounce(300).collect { save(it) }
    }

    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { loadSticker(file) }
    }

    val sizePx = with(density) { current.sizeDp.dp.toPx() }
    val left = current.x * pageWidth - sizePx / 2
    val top = current.y * pageHeight - sizePx / 2

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(current.sizeDp.dp)
            .then(
                if (editing) {
                    Modifier
                        .border(1.dp, Fern.colors.roseCarmin, RoundedCornerShape(12.dp))
                        .pointerInput(sticker.id, pageWidth, pageHeight) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                val c = current
                                current = c.copy(
                                    x = (c.x + pan.x / pageWidth).coerceIn(0f, 1f),
                                    y = (c.y + pan.y / pageHeight).coerceIn(0f, 1f),
                                    sizeDp = (c.sizeDp * zoom).coerceIn(48f, 360f),
                                    rotation = (c.rotation + rotation) % 360f,
                                )
                            }
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(current.rotation),
            )
        }
        if (editing) {
            Box(Modifier.align(Alignment.TopEnd)) {
                GlyphButton("✕", onClick = onDelete, size = 28.dp)
            }
        }
    }
}

/** Charge l'image en la réduisant (≤ 800 px) pour ne pas gaspiller la mémoire. */
private fun loadSticker(file: File): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 800) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
