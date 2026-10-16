package com.atelierjlg.fern.messages.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atelierjlg.fern.ui.kit.IconButtonRound
import com.atelierjlg.fern.ui.kit.Pill
import com.atelierjlg.fern.ui.kit.rememberPhoto
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcons

/**
 * Une photo en grand, par-dessus la conversation : pincer pour zoomer, toucher pour fermer.
 * En bas : Enregistrer (dans la galerie) et Partager.
 */
@Composable
fun PhotoViewer(uri: Uri, onDismiss: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    val c = Fern.colors
    // Assez grand pour un zoom net, sans charger une photo de 12 Mpx en entier.
    val bitmap = rememberPhoto(uri.toString(), 2400)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + panChange
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap, "Photo",
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                        .transformable(zoom),
                    contentScale = ContentScale.Fit,
                )
            }
            Box(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp)) {
                IconButtonRound(FernIcons.Close, c.creme, onDismiss)
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                Pill("Enregistrer", true, onClick = onSave)
                Pill("Partager", false, onClick = onShare)
            }
            if (bitmap == null) Text("…", style = Fern.type.titreWidget, color = c.creme)
        }
    }
}
