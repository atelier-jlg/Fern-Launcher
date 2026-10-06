package com.atelierjlg.fern.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.data.AltType
import com.atelierjlg.fern.data.AlternanceSettings
import com.atelierjlg.fern.data.CarnetDay
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernColors
import com.atelierjlg.fern.widgets.Alternance
import com.atelierjlg.fern.widgets.Anki
import java.time.LocalDate

// ─── Alternance ─────────────────────────────────────────────────────────────

/**
 * Alternance : où je suis cette semaine (école ou entreprise), dans combien de jours ça change,
 * et la frise des 8 prochaines semaines.
 */
@Composable
fun AlternanceWidget(settings: AlternanceSettings, compact: Boolean) {
    val colors = Fern.colors
    val today = LocalDate.now()
    val current = Alternance.periodAt(settings.periods, today)
    val next = Alternance.nextChange(settings.periods, today)
    fun nameOf(type: AltType?) = when (type) {
        AltType.Ecole -> settings.schoolName
        AltType.Entreprise -> settings.companyName
        null -> "Pause"
    }
    fun colorOf(type: AltType?) = when (type) {
        AltType.Ecole -> colors.pistache
        AltType.Entreprise -> colors.roseCarmin
        null -> colors.sousBois
    }

    WidgetCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Alternance".uppercase(), style = Fern.type.libelle, color = colors.lichen)
            if (settings.periods.isEmpty()) {
                Text("Indique tes périodes dans Paramètres → Alternance", style = Fern.type.corps, color = colors.moussePale)
                return@Column
            }
            Text(nameOf(current?.type), style = Fern.type.titreWidget, color = colorOf(current?.type))
            if (next != null) {
                val days = next.daysUntil
                Text(
                    (if (days <= 1L) "${nameOf(next.period.type)} demain" else "${nameOf(next.period.type)} dans $days j").uppercase(),
                    style = Fern.type.libelle,
                    color = colors.creme,
                )
            }
            // Frise : une barre par semaine, la semaine en cours entourée.
            val strip = Alternance.weekStrip(settings.periods, today, weeks = if (compact) 6 else 10)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                strip.forEachIndexed { i, type ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .background(colorOf(type), RoundedCornerShape(7.dp))
                            .then(if (i == 0) Modifier.border(1.5.dp, colors.creme, RoundedCornerShape(7.dp)) else Modifier),
                    )
                }
            }
        }
    }
}

// ─── Révisions ──────────────────────────────────────────────────────────────

/**
 * Révisions : les cartes AnkiDroid à revoir aujourd'hui. Le lotus est en bouton tant qu'il en reste,
 * et s'ouvre quand tout est fait.
 */
@Composable
fun RevisionsWidget(
    state: Anki.State,
    onOpenAnki: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val colors = Fern.colors
    val done = state is Anki.State.Due && state.cards == 0
    val openness by animateFloatAsState(if (done) 1f else 0.18f, tween(900), label = "lotus")

    WidgetCard(onClick = onOpenAnki) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(64.dp)) { drawLotus(openness, colors) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Révisions".uppercase(), style = Fern.type.libelle, color = colors.lichen)
                when (state) {
                    Anki.State.NotInstalled -> Text("AnkiDroid n'est pas installé", style = Fern.type.corps, color = colors.moussePale)
                    Anki.State.NoPermission -> PillButton("Autoriser", onClick = onRequestPermission, accent = true)
                    is Anki.State.Due -> if (state.cards == 0) {
                        Text("Tout est fait ✿", style = Fern.type.corps, color = colors.pistache)
                    } else {
                        Text("${state.cards}", style = Fern.type.titreWidget, color = colors.creme)
                        Text(if (state.cards > 1) "CARTES À REVOIR" else "CARTE À REVOIR", style = Fern.type.libelle, color = colors.lichen)
                    }
                }
            }
        }
    }
}

/** Un lotus : 7 pétales qui s'écartent de la verticale selon `openness` (0 fermé, 1 ouvert). */
private fun DrawScope.drawLotus(openness: Float, colors: FernColors) {
    val base = Offset(size.width / 2, size.height * 0.82f)
    val petal = Size(size.width * 0.2f, size.height * 0.55f)
    val spread = 70f * openness
    for (i in -3..3) {
        val angle = i * spread / 3f
        val tint = if (i % 2 == 0) colors.roseCarmin else colors.carmin
        rotate(angle, pivot = base) {
            drawOval(tint, topLeft = Offset(base.x - petal.width / 2, base.y - petal.height), size = petal)
        }
    }
    drawCircle(colors.pistache, radius = size.width * 0.08f * (0.5f + openness / 2), center = Offset(base.x, base.y - petal.height * 0.25f))
    drawLine(colors.lichen, Offset(size.width * 0.15f, base.y + 4f), Offset(size.width * 0.85f, base.y + 4f), strokeWidth = 2.dp.toPx())
}

// ─── Carnet du jour ─────────────────────────────────────────────────────────

/**
 * Carnet du jour : l'humeur (de la graine à la fleur, toucher pour changer),
 * 3 habitudes à cocher, une note (toucher pour écrire, puis l'envoyer vers Obsidian).
 */
@Composable
fun CarnetWidget(
    day: CarnetDay,
    habits: List<String>,
    compact: Boolean,
    onMood: (Int) -> Unit,
    onToggleHabit: (Int) -> Unit,
    onNote: (String) -> Unit,
    onSendToObsidian: (String) -> Unit,
) {
    val colors = Fern.colors
    var editing by remember { mutableStateOf(false) }
    val moodNames = listOf("Graine", "Pousse", "Bourgeon", "Fleur", "Éclose")

    WidgetCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Toucher la plante la fait grandir d'un cran (et revenir à la graine après « Éclose »).
                Canvas(
                    Modifier
                        .size(if (compact) 48.dp else 60.dp)
                        .clickable { onMood(((day.mood ?: -1) + 1) % 5) },
                ) { drawPlant(day.mood, colors) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Carnet du jour".uppercase(), style = Fern.type.libelle, color = colors.lichen)
                    Text(
                        day.mood?.let { moodNames[it] } ?: "Comment ça pousse ?",
                        style = Fern.type.corps,
                        color = if (day.mood == null) colors.moussePale else colors.creme,
                    )
                }
            }
            // Habitudes : pastilles à cocher.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                habits.take(3).forEachIndexed { i, name ->
                    val checked = i in day.habits
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (checked) colors.pistache else colors.lierre, RoundedCornerShape(50))
                            .clickable { onToggleHabit(i) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            (if (checked) "✓ " else "") + name,
                            style = Fern.type.nomApp,
                            color = if (checked) colors.nuit else colors.creme,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                day.note.ifBlank { "Touche pour écrire la note du jour" },
                style = Fern.type.corps,
                color = if (day.note.isBlank()) colors.moussePale else colors.creme,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editing = true },
            )
            if (day.note.isNotBlank()) {
                PillButton("→ Obsidian", onClick = { onSendToObsidian(day.note) })
            }
        }
    }

    if (editing) {
        TextInputDialog(
            title = "Note du jour",
            initial = day.note,
            onConfirm = { onNote(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

/** La plante de l'humeur : 0 graine, 1 pousse, 2 bourgeon, 3 fleur, 4 éclose (null = graine pâle). */
private fun DrawScope.drawPlant(mood: Int?, colors: FernColors) {
    val w = size.width
    val h = size.height
    val ground = h * 0.88f
    val stage = mood ?: 0
    val pale = mood == null
    val stem: Color = if (pale) colors.moussePale else colors.pistache
    drawLine(colors.lichen.copy(alpha = 0.6f), Offset(w * 0.15f, ground), Offset(w * 0.85f, ground), strokeWidth = 2.dp.toPx())
    if (stage == 0) {
        drawOval(if (pale) colors.moussePale else colors.carmin, Offset(w * 0.4f, ground - h * 0.16f), Size(w * 0.2f, h * 0.14f))
        return
    }
    val top = ground - h * (0.25f + 0.13f * stage)
    drawLine(stem, Offset(w / 2, ground), Offset(w / 2, top), strokeWidth = 3.dp.toPx())
    // Deux feuilles.
    drawOval(stem, Offset(w / 2 - w * 0.26f, ground - h * 0.28f), Size(w * 0.26f, h * 0.11f))
    drawOval(stem, Offset(w / 2, ground - h * 0.36f), Size(w * 0.26f, h * 0.11f))
    when (stage) {
        2 -> drawOval(colors.carmin, Offset(w / 2 - w * 0.08f, top - h * 0.16f), Size(w * 0.16f, h * 0.2f))
        3, 4 -> {
            val petals = if (stage == 3) 5 else 8
            val r = w * (if (stage == 3) 0.1f else 0.13f)
            for (i in 0 until petals) {
                val a = Math.toRadians(i * 360.0 / petals)
                val c = Offset(w / 2 + (r * Math.cos(a)).toFloat(), top + (r * Math.sin(a)).toFloat())
                drawCircle(colors.roseCarmin, radius = r * 0.75f, center = c)
            }
            drawCircle(colors.pistache, radius = r * 0.6f, center = Offset(w / 2, top))
        }
    }
    if (stage == 1) drawCircle(stem, radius = 3.dp.toPx(), center = Offset(w / 2, top), style = Stroke(width = 2.dp.toPx()))
}
