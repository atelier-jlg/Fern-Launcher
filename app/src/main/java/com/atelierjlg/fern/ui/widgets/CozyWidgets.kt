package com.atelierjlg.fern.ui.widgets

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.data.AltType
import com.atelierjlg.fern.data.AlternanceSettings
import com.atelierjlg.fern.data.CatPose
import com.atelierjlg.fern.data.ChatSettings
import com.atelierjlg.fern.data.PomodoroPhase
import com.atelierjlg.fern.data.PomodoroSettings
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.widgets.Alternance
import com.atelierjlg.fern.widgets.CoursState
import com.atelierjlg.fern.widgets.Plant
import com.atelierjlg.fern.widgets.ScreenTime
import com.atelierjlg.fern.widgets.ScreenTimeState
import com.atelierjlg.fern.widgets.Weather
import com.atelierjlg.fern.widgets.WeatherCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 * Les widgets maison de la v0.14 : Cours du jour, Le chat, Météo, Plante, Pomodoro, Temps d'écran.
 * Tous utilisent la même carte (WidgetCard) et les couleurs du thème.
 */

private val hm = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.toTime(): LocalTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()

/** Une valeur qui alterne 0, 1, 0, 1… toutes les `periodMs` millisecondes (pour animer les sprites). */
@Composable
private fun rememberFrame(periodMs: Long, frames: Int = 2): Int {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(periodMs, frames) {
        while (true) {
            delay(periodMs)
            frame = (frame + 1) % frames
        }
    }
    return frame
}

// ─── Météo ──────────────────────────────────────────────────────────────────

/** Météo en pixel art : le temps qu'il fait, la température, min / max, et demain. */
@Composable
fun MeteoWidget(weather: Weather?, placeName: String, compact: Boolean, onRefresh: () -> Unit) {
    val colors = Fern.colors
    val frame = rememberFrame(650)
    val palette = mapOf(
        'y' to colors.creme, 'm' to colors.creme, 'c' to colors.lichen, 'w' to colors.creme,
        'r' to colors.vague, 'n' to colors.creme, 'l' to colors.roseCarmin, 'f' to colors.lichen,
    )
    WidgetCard(onClick = onRefresh) {
        if (weather == null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MÉTÉO", style = Fern.type.libelle, color = colors.lichen)
                Text("Pas encore de météo · touche pour réessayer", style = Fern.type.corps, color = colors.moussePale)
            }
            return@WidgetCard
        }
        val sprite = WeatherSprites.sprite(weather.kind, weather.isDay, frame)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelSprite(sprite, palette, Modifier.size(if (compact) 52.dp else 72.dp))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!compact) Text("MÉTÉO · ${placeName.uppercase()}", style = Fern.type.libelle, color = colors.lichen)
                Text("${weather.temperature}°", style = Fern.type.titreWidget, color = colors.creme)
                Text(
                    "${weather.kind.label} · ${weather.min}° / ${weather.max}°",
                    style = if (compact) Fern.type.nomApp else Fern.type.corps,
                    color = colors.lichen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact && weather.tomorrowCode != null) {
                    Text(
                        "DEMAIN · ${WeatherCodes.kindOf(weather.tomorrowCode).label.uppercase()}" +
                            (weather.tomorrowMax?.let { " · $it°" } ?: ""),
                        style = Fern.type.libelle,
                        color = colors.roseCarmin,
                    )
                }
            }
        }
    }
}

// ─── Le chat ────────────────────────────────────────────────────────────────

private enum class CatMoment { Nuit, Matin, Jour }

private fun catMoment(hour: Int) = when (hour) {
    in 7..9 -> CatMoment.Matin
    in 10..21 -> CatMoment.Jour
    else -> CatMoment.Nuit
}

/**
 * Charge une image ou une animation de Jules (PNG, GIF, WebP animé) en Drawable Android.
 * Une animation (AnimatedImageDrawable) est lancée tout de suite. Pas de lissage : le pixel art reste net.
 */
private fun loadCatDrawable(file: File): Drawable? = runCatching {
    val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
        // Les très grandes images sont réduites (mémoire).
        val biggest = maxOf(info.size.width, info.size.height)
        if (biggest > 800) decoder.setTargetSampleSize(biggest / 800 + 1)
    }
    drawable.isFilterBitmap = false
    if (drawable is AnimatedImageDrawable) drawable.start()
    drawable
}.getOrNull()

/** Affiche le Drawable (image ou animation) dans une vue Android classique. */
@Composable
private fun CatImage(file: File, modifier: Modifier) {
    val drawable by produceState<Drawable?>(null, file) {
        value = withContext(Dispatchers.IO) { loadCatDrawable(file) }
    }
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view -> if (view.drawable !== drawable) view.setImageDrawable(drawable) },
        modifier = modifier,
    )
}

/**
 * Le chat : il dort la nuit, s'étire le matin, veille le reste du temps (il cligne des yeux,
 * remue la queue). Quand la 1re tâche (le repas) est cochée : petit cœur et bouille contente.
 * Si Jules a mis ses propres images ou animations, elles remplacent le pixel art.
 */
@Composable
fun ChatWidget(
    settings: ChatSettings,
    compact: Boolean,
    imageFile: (String) -> File,
    onToggleChore: (Int) -> Unit,
) {
    val colors = Fern.colors
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = LocalTime.now().hour
        }
    }
    val moment = catMoment(hour)
    val today = LocalDate.now().toString()
    val done = settings.done[today].orEmpty()
    val fed = settings.chores.isNotEmpty() && 0 in done
    // Quelle image de Jules montrer (la nuit et le matin reprennent celle de la journée).
    val custom = when {
        fed && moment != CatMoment.Nuit && settings.images[CatPose.Content] != null -> settings.images[CatPose.Content]
        moment == CatMoment.Nuit -> settings.images[CatPose.Nuit] ?: settings.images[CatPose.Jour]
        moment == CatMoment.Matin -> settings.images[CatPose.Matin] ?: settings.images[CatPose.Jour]
        else -> settings.images[CatPose.Jour]
    }
    val customHappy = fed && settings.images[CatPose.Content] != null && moment != CatMoment.Nuit

    // Pas de carte ni de texte : le chat est posé directement sur le fond, comme un sticker.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.size(if (compact) 120.dp else 150.dp)) {
            if (custom != null) {
                // Image de Jules : une respiration lente (elle gonfle un tout petit peu).
                val breath by rememberInfiniteTransition(label = "souffle").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.04f,
                    animationSpec = infiniteRepeatable(tween(if (moment == CatMoment.Nuit) 2400 else 1400), RepeatMode.Reverse),
                    label = "souffle",
                )
                CatImage(imageFile(custom), Modifier.fillMaxWidth().fillMaxHeight().scale(breath))
            } else {
                val sprite = when {
                    moment == CatMoment.Nuit -> CatSprites.sleep
                    // Le matin : étirement, puis assis, puis étirement…
                    moment == CatMoment.Matin && rememberFrame(1800) == 0 -> CatSprites.stretch
                    // Nourri : yeux rieurs, la queue qui remue vite.
                    fed -> if (rememberFrame(450) == 0) CatSprites.happyA else CatSprites.happyB
                    // Sinon : un clignement de temps en temps (1 image sur 6).
                    else -> if (rememberFrame(700, frames = 6) == 5) CatSprites.sitB else CatSprites.sitA
                }
                PixelSprite(sprite, CatSprites.palette, Modifier.fillMaxWidth().fillMaxHeight(), outline = colors.lichen.copy(alpha = 0.55f))
            }
            if (fed && !customHappy) {
                // Le petit cœur à côté de la tête, qui bat doucement.
                val beat by rememberInfiniteTransition(label = "coeur").animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                    label = "coeur",
                )
                PixelSprite(
                    CatSprites.heart,
                    CatSprites.heartPalette,
                    Modifier
                        .align(if (moment == CatMoment.Nuit) Alignment.CenterEnd else Alignment.TopEnd)
                        .size(if (compact) 22.dp else 28.dp)
                        .scale(beat),
                )
            }
            if (moment == CatMoment.Nuit) {
                // Des « z » qui montent doucement.
                val rise by rememberInfiniteTransition(label = "z").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(2600)),
                    label = "z",
                )
                Text(
                    "z",
                    style = Fern.type.corps,
                    color = colors.lichen.copy(alpha = 1f - rise),
                    modifier = Modifier.align(Alignment.TopEnd).offset(y = (-14 * rise).dp),
                )
            }
        }
        if (settings.chores.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                settings.chores.take(if (compact) 2 else 3).forEachIndexed { i, chore ->
                    PillButton(
                        text = if (i in done) "✓ $chore" else chore,
                        accent = i in done,
                        onClick = { onToggleChore(i) },
                    )
                }
            }
        }
    }
}

// ─── Fougères (Plante et Pomodoro) ──────────────────────────────────────────

/**
 * Une fronde de fougère, du pied `base` vers la pointe.
 * - `bendDeg` : courbure douce sur toute la longueur.
 * - `curlDeg` : enroulement de la pointe (crosse) ; 0 = fronde déroulée.
 * - `leafUntil` : jusqu'où (0 à 1) les folioles sont sorties.
 */
private fun DrawScope.drawFrond(
    base: Offset,
    startDeg: Float,
    length: Float,
    bendDeg: Float,
    curlDeg: Float,
    leafUntil: Float,
    stem: Color,
    leaf: Color,
    width: Float,
) {
    val steps = 48
    val ds = length / steps
    var pos = base
    val points = ArrayList<Pair<Offset, Float>>(steps + 1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val angle = startDeg + bendDeg * t + curlDeg * t.pow(4)
        points += pos to angle
        val rad = Math.toRadians(angle.toDouble())
        pos = Offset(pos.x + ds * cos(rad).toFloat(), pos.y + ds * sin(rad).toFloat())
    }
    val path = Path().apply {
        moveTo(points[0].first.x, points[0].first.y)
        points.drop(1).forEach { lineTo(it.first.x, it.first.y) }
    }
    drawPath(path, stem, style = Stroke(width = width, cap = StrokeCap.Round))
    // Les folioles : de petits traits de part et d'autre, plus courts vers la pointe.
    for (i in 4 until steps - 2 step 3) {
        val t = i / steps.toFloat()
        if (t > leafUntil) break
        val (p, angle) = points[i]
        val leafLen = length * 0.2f * (1f - t).pow(0.7f) + width
        for (side in listOf(-1, 1)) {
            val a = Math.toRadians((angle + side * 62f).toDouble())
            drawLine(
                leaf,
                p,
                Offset(p.x + leafLen * cos(a).toFloat(), p.y + leafLen * sin(a).toFloat()),
                strokeWidth = width * 0.9f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * La plante qui pousse avec le Carnet : 1 à 7 frondes selon les habitudes cochées sur 7 jours.
 * Deux jours sans rien : elle pâlit et penche un peu (elle a soif).
 */
@Composable
fun PlanteWidget(state: Plant.State, compact: Boolean) {
    val colors = Fern.colors
    val sway by rememberInfiniteTransition(label = "vent").animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
        label = "vent",
    )
    val green = if (state.thirsty) lerp(colors.pistache, colors.lichen, 0.6f) else colors.pistache
    val stem = if (state.thirsty) colors.lichen else colors.pistache

    WidgetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(if (compact) 72.dp else 92.dp)) {
                val potTop = size.height * 0.74f
                val base = Offset(size.width / 2, potTop)
                val n = state.fronds
                for (i in 0 until n) {
                    // Les frondes s'ouvrent en éventail, de -60° à +60° autour de la verticale.
                    val spread = if (n == 1) 0f else -60f + 120f * i / (n - 1)
                    val droop = if (state.thirsty) 18f else 0f
                    drawFrond(
                        base = base,
                        startDeg = -90f + spread + sway,
                        length = size.height * (0.58f - 0.12f * kotlin.math.abs(spread) / 60f),
                        bendDeg = (if (spread < 0) -1f else 1f) * (24f + droop),
                        curlDeg = 0f,
                        leafUntil = 1f,
                        stem = stem,
                        leaf = green,
                        width = 2.dp.toPx(),
                    )
                }
                // Le pot, en carmin.
                val potW = size.width * 0.42f
                val potPath = Path().apply {
                    moveTo(base.x - potW / 2, potTop)
                    lineTo(base.x + potW / 2, potTop)
                    lineTo(base.x + potW * 0.36f, size.height)
                    lineTo(base.x - potW * 0.36f, size.height)
                    close()
                }
                drawPath(potPath, colors.carmin)
                drawRect(colors.roseCarmin, Offset(base.x - potW / 2 - 2f, potTop - 3.dp.toPx()), androidx.compose.ui.geometry.Size(potW + 4f, 4.dp.toPx()))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("PLANTE", style = Fern.type.libelle, color = colors.lichen)
                Text(
                    "${state.activeDays} jour${if (state.activeDays > 1) "s" else ""} sur 7",
                    style = if (compact) Fern.type.corps else Fern.type.titreWidget,
                    color = colors.creme,
                )
                Text(
                    if (state.thirsty) "Elle a soif · coche une habitude" else "Elle pousse avec ton Carnet",
                    style = Fern.type.nomApp,
                    color = if (state.thirsty) colors.roseCarmin else colors.lichen,
                )
            }
        }
    }
}

// ─── Pomodoro ───────────────────────────────────────────────────────────────

/**
 * Pomodoro : une crosse de fougère qui se déroule pendant le travail.
 * Le mode Focus s'active tout seul (réglable), puis revient comme avant à la pause.
 */
@Composable
fun PomodoroWidget(settings: PomodoroSettings, compact: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val colors = Fern.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = settings.phase != PomodoroPhase.Arret
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val total = (settings.endsAt - settings.startedAt).coerceAtLeast(1)
    val progress = when (settings.phase) {
        PomodoroPhase.Arret -> 0f
        PomodoroPhase.Travail -> ((now - settings.startedAt).toFloat() / total).coerceIn(0f, 1f)
        PomodoroPhase.Pause -> 1f
    }
    val remaining = ((settings.endsAt - now) / 1000).coerceAtLeast(0)

    WidgetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(if (compact) 64.dp else 84.dp)) {
                drawFrond(
                    base = Offset(size.width * 0.4f, size.height * 0.98f),
                    startDeg = -80f,
                    length = size.height * 1.05f,
                    bendDeg = 25f,
                    // Au début, la pointe est enroulée sur deux tours ; elle se déroule avec le temps.
                    curlDeg = 700f * (1f - progress),
                    leafUntil = 0.15f + 0.85f * progress,
                    stem = if (settings.phase == PomodoroPhase.Pause) colors.pistache else colors.lichen,
                    leaf = colors.pistache,
                    width = 2.5.dp.toPx(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when (settings.phase) {
                        PomodoroPhase.Arret -> "POMODORO"
                        PomodoroPhase.Travail -> "CONCENTRATION"
                        PomodoroPhase.Pause -> "PAUSE"
                    },
                    style = Fern.type.libelle,
                    color = if (settings.phase == PomodoroPhase.Pause) colors.pistache else colors.lichen,
                )
                Text(
                    if (running) "%02d:%02d".format(remaining / 60, remaining % 60) else "${settings.workMinutes} min",
                    style = Fern.type.titreWidget,
                    color = colors.creme,
                )
                if (settings.phase == PomodoroPhase.Travail && settings.autoFocus && !compact) {
                    Text("Mode Focus actif", style = Fern.type.nomApp, color = colors.lichen)
                }
                if (running) {
                    PillButton("Arrêter", onClick = onStop)
                } else {
                    PillButton("Lancer", onClick = onStart, accent = true)
                }
            }
        }
    }
}

// ─── Cours du jour ──────────────────────────────────────────────────────────

/**
 * Cours du jour : le cours en cours ou le prochain (agenda), et le prochain examen.
 * Les semaines en entreprise (widget Alternance), il le dit au lieu d'afficher « rien ».
 */
@Composable
fun CoursWidget(
    state: CoursState,
    alternance: AlternanceSettings,
    compact: Boolean,
    onAllow: () -> Unit,
    onOpenEvent: (Long) -> Unit,
) {
    val colors = Fern.colors
    val now = System.currentTimeMillis()
    val next = state.next
    WidgetCard(onClick = next?.let { { onOpenEvent(it.eventId) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!state.hasPermission) {
                Text("COURS DU JOUR", style = Fern.type.libelle, color = colors.lichen)
                Text("Fern a besoin de lire ton agenda", style = Fern.type.corps, color = colors.moussePale)
                PillButton("Autoriser l'agenda", onClick = onAllow, accent = true)
                return@Column
            }
            if (next != null) {
                val startDate = Instant.ofEpochMilli(next.begin).atZone(ZoneId.systemDefault()).toLocalDate()
                val label = when {
                    next.begin <= now -> "EN COURS"
                    startDate == LocalDate.now() -> "COURS DU JOUR"
                    else -> "DEMAIN"
                }
                Text(label, style = Fern.type.libelle, color = colors.roseCarmin)
                Text(
                    next.title,
                    style = Fern.type.corps,
                    color = colors.creme,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${next.begin.toTime().format(hm)} → ${next.end.toTime().format(hm)}" +
                        (if (next.location.isNotBlank()) " · ${next.location}" else ""),
                    style = Fern.type.nomApp,
                    color = colors.lichen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                val today = LocalDate.now()
                val current = Alternance.periodAt(alternance.periods, today)
                Text("COURS DU JOUR", style = Fern.type.libelle, color = colors.lichen)
                if (current != null && current.type != AltType.Ecole) {
                    Text("Semaine ${alternance.nameOf(current.type)}", style = Fern.type.corps, color = colors.creme)
                    Alternance.nextChange(alternance.periods, today)?.let { change ->
                        Text(
                            "${alternance.nameOf(change.period.type)} dans ${change.daysUntil} j",
                            style = Fern.type.nomApp,
                            color = colors.lichen,
                        )
                    }
                } else {
                    Text("Rien de prévu ✿", style = Fern.type.corps, color = colors.creme)
                }
            }
            state.exam?.let { exam ->
                val days = ChronoUnit.DAYS.between(
                    LocalDate.now(),
                    Instant.ofEpochMilli(exam.begin).atZone(ZoneId.systemDefault()).toLocalDate(),
                )
                Text(
                    (if (days <= 0) "EXAMEN AUJOURD'HUI" else if (days == 1L) "EXAMEN DEMAIN" else "EXAMEN DANS $days J") +
                        " · ${exam.title}",
                    style = Fern.type.libelle,
                    color = colors.pistache,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── Temps d'écran doux ─────────────────────────────────────────────────────

/**
 * Temps d'écran doux : le temps passé aujourd'hui, une jauge par rapport à un repère,
 * sans rouge ni alerte. Juste pour savoir.
 */
@Composable
fun TempsEcranWidget(state: ScreenTimeState, goalMinutes: Int, compact: Boolean, onAllow: () -> Unit) {
    val colors = Fern.colors
    WidgetCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (state.focusOnly) "TEMPS D'ÉCRAN · APPLIS FOCUS" else "TEMPS D'ÉCRAN",
                style = Fern.type.libelle,
                color = colors.lichen,
                maxLines = 1,
            )
            if (!state.hasAccess) {
                Text("À autoriser une fois dans les réglages d'Android", style = Fern.type.nomApp, color = colors.moussePale)
                PillButton("Autoriser", onClick = onAllow, accent = true)
                return@Column
            }
            Text(ScreenTime.format(state.totalMs), style = Fern.type.titreWidget, color = colors.creme)
            val goalMs = goalMinutes.coerceAtLeast(1) * 60_000L
            val ratio = (state.totalMs.toFloat() / goalMs).coerceIn(0f, 1f)
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(colors.lierre, RoundedCornerShape(6.dp)),
            ) {
                Box(
                    Modifier
                        .width(maxWidth * ratio)
                        .height(12.dp)
                        .background(if (ratio >= 1f) colors.lichen else colors.pistache, RoundedCornerShape(6.dp)),
                )
            }
            if (state.totalMs > goalMs) {
                Text("Au-delà de ton repère (${ScreenTime.format(goalMs)}) · pas grave", style = Fern.type.nomApp, color = colors.lichen)
            }
            if (!compact) {
                for ((name, ms) in state.top) {
                    Row {
                        Text(name, style = Fern.type.nomApp, color = colors.creme, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ScreenTime.format(ms), style = Fern.type.nomApp, color = colors.lichen)
                    }
                }
            }
        }
    }
}
