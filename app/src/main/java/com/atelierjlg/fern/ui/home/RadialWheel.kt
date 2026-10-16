package com.atelierjlg.fern.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.apps.AppIndex
import com.atelierjlg.fern.data.RADIAL_SIZE
import com.atelierjlg.fern.data.SlotRef
import com.atelierjlg.fern.ui.common.AppIcon
import com.atelierjlg.fern.ui.common.EmptySlot
import com.atelierjlg.fern.ui.theme.Fern
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * La roue d'applis : jusqu'à 8 applis en cercle autour du doigt.
 * Toucher une appli l'ouvre ; un « + » permet d'en choisir une ; appui long pour remplacer.
 * Toucher ailleurs referme la roue.
 */
@Composable
fun RadialWheel(
    center: Offset,
    slots: List<String?>,
    apps: AppIndex,
    onLaunch: (com.atelierjlg.fern.apps.AppEntry) -> Unit,
    onPick: (SlotRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Fern.colors.nuit.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        val radius = with(density) { 104.dp.toPx() }
        val itemSize = with(density) { 56.dp.toPx() }
        val margin = radius + itemSize
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        // On garde toute la roue à l'écran, même si le doigt est près d'un bord.
        val c = if (center.isSpecified) {
            Offset(center.x.coerceIn(margin, width - margin), center.y.coerceIn(margin, height - margin))
        } else {
            Offset(width / 2, height / 2)
        }

        // Le centre : un petit rond pour repère.
        Box(
            Modifier
                .offset { IntOffset((c.x - 10.dp.toPx()).roundToInt(), (c.y - 10.dp.toPx()).roundToInt()) }
                .size(20.dp)
                .background(Fern.colors.roseCarmin, CircleShape),
        )

        for (i in 0 until RADIAL_SIZE) {
            val angle = -PI / 2 + i * 2 * PI / RADIAL_SIZE
            val x = c.x + radius * cos(angle).toFloat() - itemSize / 2
            val y = c.y + radius * sin(angle).toFloat() - itemSize / 2
            val app = apps.find(slots.getOrNull(i))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(56.dp),
            ) {
                if (app != null) {
                    AppIcon(
                        app = app,
                        iconSize = 52.dp,
                        showLabel = false,
                        onClick = { onLaunch(app) },
                        onLongClick = { onPick(SlotRef.Radial(i)) },
                    )
                } else {
                    EmptySlot(iconSize = 48.dp, onClick = { onPick(SlotRef.Radial(i)) })
                }
            }
        }

        if (slots.all { it == null }) {
            Text(
                "Touche un + pour remplir ta roue",
                style = Fern.type.libelle,
                color = Fern.colors.creme,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-120).dp),
            )
        }
    }
}
