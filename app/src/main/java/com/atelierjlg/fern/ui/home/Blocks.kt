package com.atelierjlg.fern.ui.home

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.apps.AppEntry
import com.atelierjlg.fern.apps.AppIndex
import com.atelierjlg.fern.data.PACK_SIZE
import com.atelierjlg.fern.data.SlotRef
import com.atelierjlg.fern.ui.common.AppIcon
import com.atelierjlg.fern.ui.common.EmptySlot
import com.atelierjlg.fern.ui.common.GlyphButton
import com.atelierjlg.fern.ui.theme.Fern
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ce qu'un emplacement d'appli doit savoir faire, en mode normal comme en édition. */
class SlotActions(
    val editing: Boolean,
    val onLaunch: (AppEntry) -> Unit,
    val onPick: (SlotRef) -> Unit,
    val onEnterEdit: () -> Unit,
)

/** Un emplacement : l'appli, ou un « + » en mode édition, ou rien. */
@Composable
fun Slot(
    app: AppEntry?,
    ref: SlotRef,
    iconSize: Dp,
    showLabel: Boolean,
    actions: SlotActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        when {
            app != null -> AppIcon(
                app = app,
                iconSize = iconSize,
                showLabel = showLabel,
                // En édition, toucher une appli permet de la remplacer ou de la retirer.
                onClick = { if (actions.editing) actions.onPick(ref) else actions.onLaunch(app) },
                onLongClick = { if (!actions.editing) actions.onEnterEdit() else actions.onPick(ref) },
                modifier = Modifier.fillMaxWidth(),
            )
            actions.editing -> EmptySlot(iconSize = iconSize, onClick = { actions.onPick(ref) })
        }
    }
}

/** La petite barre ↑ ↓ ✎ ✕ au-dessus d'un bloc en mode édition. */
@Composable
fun BlockToolbar(
    label: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
    onRename: (() -> Unit)? = null,
    onResize: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = Fern.type.libelle,
            color = Fern.colors.lichen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onRename != null) GlyphButton("✎", onClick = onRename)
        if (onResize != null) GlyphButton("↕", onClick = onResize)
        GlyphButton("↑", onClick = onUp)
        GlyphButton("↓", onClick = onDown)
        GlyphButton("✕", onClick = onDelete)
    }
}

/** Un pack : carte arrondie, titre, 4 applis en 2×2. */
@Composable
fun PackCard(
    title: String,
    slots: List<String?>,
    refFor: (Int) -> SlotRef,
    apps: AppIndex,
    actions: SlotActions,
    modifier: Modifier = Modifier,
) {
    val colors = Fern.colors
    Column(
        modifier
            .background(colors.mousse.copy(alpha = 0.92f), RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = Fern.type.libelle,
            color = colors.lichen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 2) {
                    val index = row * 2 + col
                    Slot(
                        app = apps.find(slots.getOrNull(index)),
                        ref = refFor(index),
                        iconSize = 50.dp,
                        showLabel = true,
                        actions = actions,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Une rangée de 4 applis sans carte (favoris). */
@Composable
fun AppRowView(
    slots: List<String?>,
    refFor: (Int) -> SlotRef,
    apps: AppIndex,
    actions: SlotActions,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (index in 0 until PACK_SIZE) {
            Slot(
                app = apps.find(slots.getOrNull(index)),
                ref = refFor(index),
                iconSize = 54.dp,
                showLabel = true,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─── Horloge ────────────────────────────────────────────────────────────────

/** L'heure qui se met à jour à chaque seconde pile. */
@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    return now
}

private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

@Composable
fun ClockView() {
    val now = rememberNow()
    val context = LocalContext.current
    val colors = Fern.colors

    Column(
        // Toucher l'horloge ouvre les alarmes.
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            runCatching {
                context.startActivity(
                    Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    ) {
        Text(
            text = buildAnnotatedString {
                append("%02d:".format(now.hour))
                withStyle(SpanStyle(color = colors.roseCarmin)) {
                    append("%02d".format(now.minute))
                }
            },
            style = Fern.type.horloge,
            color = colors.creme,
        )
        Text(
            text = now.format(dateFormat).uppercase(Locale.FRENCH),
            style = Fern.type.libelle,
            color = colors.lichen,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}
