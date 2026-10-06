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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import com.atelierjlg.fern.data.Family
import com.atelierjlg.fern.data.topByLaunches
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.atelierjlg.fern.ui.common.GearButton
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
    onToggleHalf: (() -> Unit)? = null,
    /** Réglages propres à l'élément (ex. durées du Pomodoro) : bouton engrenage. */
    onSettings: (() -> Unit)? = null,
    /** Élément étroit (un quart de largeur) : un seul bouton « ⋯ » qui ouvre un menu. */
    compact: Boolean = false,
) {
    if (compact) {
        var open by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlyphButton("⋯", onClick = { open = true }, size = 28.dp)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                val items = listOfNotNull(
                    "Avant" to onUp,
                    "Après" to onDown,
                    onRename?.let { "Renommer" to it },
                    onToggleHalf?.let { "Largeur" to it },
                    onSettings?.let { "Réglages" to it },
                    onResize?.let { (if (onToggleHalf == null) "Taille" else "Hauteur") to it },
                    "Supprimer" to onDelete,
                )
                for ((text, action) in items) {
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() })
                }
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = Fern.type.libelle,
            color = Fern.colors.lichen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSettings != null) Box(Modifier.padding(2.dp)) { GearButton(onClick = onSettings, size = 28.dp) }
        if (onRename != null) GlyphButton("✎", onClick = onRename, size = 28.dp)
        if (onResize != null) GlyphButton(if (onToggleHalf == null) "⤢" else "↕", onClick = onResize, size = 28.dp)
        if (onToggleHalf != null) GlyphButton("⇔", onClick = onToggleHalf, size = 28.dp)
        GlyphButton("↑", onClick = onUp, size = 28.dp)
        GlyphButton("↓", onClick = onDown, size = 28.dp)
        GlyphButton("✕", onClick = onDelete, size = 28.dp)
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

/** L'horloge ; `weatherLine` (ex. « 14° · PLUIE ») s'ajoute après la date si on l'a. */
@Composable
fun ClockView(weatherLine: String? = null) {
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
            text = now.format(dateFormat).uppercase(Locale.FRENCH) + (weatherLine?.let { " · $it" } ?: ""),
            style = Fern.type.libelle,
            color = colors.lichen,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Un pack « famille » : les 4 applis de la famille les plus lancées, dans une carte comme un pack.
 * - Toucher une appli : elle s'ouvre.
 * - Toucher la carte (ailleurs que sur une appli) : toute la famille s'affiche.
 * En mode édition, les applis ne se choisissent pas à la main : c'est la famille qui décide.
 */
@Composable
fun FamilyPackCard(
    family: Family,
    apps: List<AppEntry>,
    launchCounts: Map<String, Int>,
    actions: SlotActions,
    modifier: Modifier = Modifier,
) {
    val colors = Fern.colors
    var showAll by remember { mutableStateOf(false) }
    val top = remember(apps, launchCounts) {
        val byKey = apps.associateBy { it.key }
        topByLaunches(apps.map { it.key }, launchCounts).mapNotNull { byKey[it] }
    }
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(colors.mousse.copy(alpha = 0.92f))
            .clickable(enabled = !actions.editing) { showAll = true }
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)) {
            // Une pastille de la couleur de la famille (celle des plaques d'icônes).
            Box(Modifier.size(8.dp).background(colors.plate(family), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = family.label.uppercase(),
                style = Fern.type.libelle,
                color = colors.lichen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (apps.size > PACK_SIZE) {
                Text("+${apps.size - PACK_SIZE}", style = Fern.type.libelle, color = colors.roseCarmin)
            }
        }
        if (apps.isEmpty()) {
            Text("Aucune appli dans cette famille", style = Fern.type.nomApp, color = colors.moussePale, modifier = Modifier.padding(6.dp))
        }
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 2) {
                    val app = top.getOrNull(row * 2 + col)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (app != null) {
                            AppIcon(
                                app = app,
                                iconSize = 50.dp,
                                showLabel = true,
                                onClick = { if (!actions.editing) actions.onLaunch(app) },
                                onLongClick = { if (!actions.editing) actions.onEnterEdit() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAll) {
        FamilySheet(family, apps, onLaunch = { showAll = false; actions.onLaunch(it) }, onDismiss = { showAll = false })
    }
}

/** Toute la famille, en grille de 4, par ordre alphabétique. */
@Composable
private fun FamilySheet(family: Family, apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val colors = Fern.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.nuit,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(colors.plate(family), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(family.label.uppercase(), style = Fern.type.libelle, color = colors.creme)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in apps.chunked(4)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 4) {
                            val app = row.getOrNull(i)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                if (app != null) {
                                    AppIcon(app = app, iconSize = 48.dp, showLabel = true, onClick = { onLaunch(app) }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
