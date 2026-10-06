package com.atelierjlg.fern.ui.widgets

import android.content.Intent
import android.provider.Settings
import android.appwidget.AppWidgetHostView
import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atelierjlg.fern.data.PlaceSettings
import com.atelierjlg.fern.search.EventResult
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.widgets.Astro
import com.atelierjlg.fern.widgets.NowPlaying
import com.atelierjlg.fern.widgets.WidgetHost
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** La carte commune à tous les widgets maison. */
@Composable
fun WidgetCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Fern.colors.mousse.copy(alpha = 0.92f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) { content() }
}

/** L'heure courante, rafraîchie chaque minute. */
@Composable
private fun rememberMinute(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = LocalDateTime.now()
        }
    }
    return now
}

private val hm = DateTimeFormatter.ofPattern("H:mm")

// ─── Ciel ───────────────────────────────────────────────────────────────────

/**
 * Ciel : le jour, l'arc du soleil entre son lever et son coucher ;
 * la nuit, la phase de la lune (toucher ouvre Stellarium s'il est installé).
 */
@Composable
fun SkyWidget(place: PlaceSettings, compact: Boolean = false) {
    val now = rememberMinute()
    val context = LocalContext.current
    val colors = Fern.colors
    val zone = ZoneId.systemDefault()
    val sun = remember(now.toLocalDate(), place) {
        Astro.sunTimes(LocalDate.now(), place.latitude, place.longitude, zone)
    }
    val sunrise = sun.sunrise
    val sunset = sun.sunset
    val time = now.toLocalTime()
    val isDay = sunrise != null && sunset != null && time.isAfter(sunrise) && time.isBefore(sunset)

    val openStellarium: (() -> Unit)? = if (isDay) {
        null
    } else {
        {
            val pm = context.packageManager
            listOf("com.noctuasoftware.stellarium_free", "com.noctuasoftware.stellarium_plus", "org.stellarium.stellarium")
                .firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
                ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }

    if (compact) {
        SkyCompact(isDay, sunrise, sunset, time, place, openStellarium)
        return
    }

    WidgetCard(onClick = openStellarium) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(60.dp)) {
            if (isDay && sunrise != null && sunset != null) {
                val total = (sunset.toSecondOfDay() - sunrise.toSecondOfDay()).toFloat()
                val progress = ((time.toSecondOfDay() - sunrise.toSecondOfDay()) / total).coerceIn(0f, 1f)
                Canvas(Modifier.width(120.dp).fillMaxHeight()) {
                    val r = size.width / 2 - 6.dp.toPx()
                    val center = Offset(size.width / 2, size.height - 4.dp.toPx())
                    drawArc(
                        color = colors.lichen.copy(alpha = 0.5f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(2 * r, 2 * r),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    val angle = PI * (1 - progress)
                    val sunPos = Offset(center.x + r * cos(angle).toFloat(), center.y - r * sin(angle).toFloat())
                    drawCircle(colors.pistache, radius = 7.dp.toPx(), center = sunPos)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Ciel · ${place.name}".uppercase(), style = Fern.type.libelle, color = colors.lichen)
                    Text(
                        "↑ ${sunrise.format(hm)}  ↓ ${sunset.format(hm)}",
                        style = Fern.type.titreWidget,
                        color = colors.creme,
                    )
                }
            } else {
                val phase = Astro.moonPhase(System.currentTimeMillis())
                val illumination = Astro.moonIllumination(phase)
                Canvas(Modifier.size(52.dp)) {
                    val r = size.minDimension / 2
                    // Disque sombre, puis la partie éclairée dessinée par une ombre décalée.
                    drawCircle(colors.creme, radius = r)
                    val shift = (2 * r * (1 - illumination)).toFloat() * (if (phase < 0.5) -1 else 1)
                    drawCircle(colors.mousse, radius = r, center = Offset(center.x + shift, center.y))
                    drawCircle(colors.lichen, radius = r, style = Stroke(width = 1.dp.toPx()))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Ciel de nuit".uppercase(), style = Fern.type.libelle, color = colors.lichen)
                    Text(Astro.moonPhaseName(phase), style = Fern.type.titreWidget, color = colors.creme)
                    Text(
                        "${(illumination * 100).toInt()} % éclairée" + (sunrise?.let { " · lever ${it.format(hm)}" } ?: ""),
                        style = Fern.type.nomApp,
                        color = colors.lichen,
                    )
                }
            }
        }
    }
}

// ─── Musique ────────────────────────────────────────────────────────────────

/** Transforme une image en deux tons : les ombres en `dark`, les lumières en `light` (effet estampe). */
private fun duotone(dark: Color, light: Color): ColorFilter {
    fun row(d: Float, l: Float) = floatArrayOf((l - d) * 0.299f, (l - d) * 0.587f, (l - d) * 0.114f, 0f, d * 255f)
    return ColorFilter.colorMatrix(
        ColorMatrix(
            row(dark.red, light.red) +
                row(dark.green, light.green) +
                row(dark.blue, light.blue) +
                floatArrayOf(0f, 0f, 0f, 1f, 0f),
        ),
    )
}

@Composable
fun MusicWidget(
    compact: Boolean = false,
    nowPlaying: NowPlaying?,
    hasAccess: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val colors = Fern.colors
    if (compact && hasAccess && nowPlaying != null) {
        MusicCompact(nowPlaying, onPlayPause, onNext, onPrevious, onOpen)
        return
    }

    WidgetCard(onClick = nowPlaying?.let { { onOpen(it.packageName) } }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(60.dp)) {
            when {
                !hasAccess -> {
                    Column(Modifier.weight(1f)) {
                        Text("Musique".uppercase(), style = Fern.type.libelle, color = colors.lichen)
                        Text("Autorise Fern à voir ce qui joue", style = Fern.type.corps, color = colors.creme)
                    }
                    PillButton("Autoriser", accent = true, onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    })
                }
                nowPlaying == null -> {
                    Text("Musique".uppercase(), style = Fern.type.libelle, color = colors.lichen)
                    Spacer(Modifier.width(12.dp))
                    Text("Rien en lecture", style = Fern.type.corps, color = colors.moussePale)
                }
                else -> {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.carmin),
                    ) {
                        nowPlaying.art?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = duotone(colors.carmin, colors.roseCarmin),
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nowPlaying.title, style = Fern.type.corps, color = colors.creme, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            nowPlaying.artist.uppercase(),
                            style = Fern.type.libelle,
                            color = colors.lichen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ControlButton("⏮", onPrevious)
                        ControlButton(if (nowPlaying.playing) "⏸" else "▶", onPlayPause)
                        ControlButton("⏭", onNext)
                    }
                }
            }
        }
    }
}

/** Ciel en demi-largeur : le dessin en grand, les heures en dessous. */
@Composable
private fun SkyCompact(
    isDay: Boolean,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    time: LocalTime,
    place: PlaceSettings,
    onClick: (() -> Unit)?,
) {
    val colors = Fern.colors
    WidgetCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text((if (isDay) "Ciel · ${place.name}" else "Ciel de nuit").uppercase(), style = Fern.type.libelle, color = colors.lichen, maxLines = 1)
            if (isDay && sunrise != null && sunset != null) {
                val total = (sunset.toSecondOfDay() - sunrise.toSecondOfDay()).toFloat()
                val progress = ((time.toSecondOfDay() - sunrise.toSecondOfDay()) / total).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                    val r = minOf(size.width / 2, size.height) - 8.dp.toPx()
                    val c = Offset(size.width / 2, size.height - 2.dp.toPx())
                    drawArc(
                        color = colors.lichen.copy(alpha = 0.5f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(2 * r, 2 * r),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    val angle = PI * (1 - progress)
                    drawCircle(colors.pistache, radius = 7.dp.toPx(), center = Offset(c.x + r * cos(angle).toFloat(), c.y - r * sin(angle).toFloat()))
                }
                Text("↑ ${sunrise.format(hm)}  ↓ ${sunset.format(hm)}", style = Fern.type.corps, color = colors.creme)
            } else {
                val phase = Astro.moonPhase(System.currentTimeMillis())
                val illumination = Astro.moonIllumination(phase)
                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                    val r = size.height / 2
                    val c = Offset(size.width / 2, size.height / 2)
                    drawCircle(colors.creme, radius = r, center = c)
                    val shift = (2 * r * (1 - illumination)).toFloat() * (if (phase < 0.5) -1 else 1)
                    drawCircle(colors.mousse, radius = r, center = Offset(c.x + shift, c.y))
                    drawCircle(colors.lichen, radius = r, center = c, style = Stroke(width = 1.dp.toPx()))
                }
                Text(Astro.moonPhaseName(phase), style = Fern.type.corps, color = colors.creme, maxLines = 1)
            }
        }
    }
}

/** Musique en demi-largeur : la pochette en grand, le titre, les boutons. */
@Composable
private fun MusicCompact(
    nowPlaying: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = Fern.colors
    WidgetCard(onClick = { onOpen(nowPlaying.packageName) }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.carmin),
            ) {
                nowPlaying.art?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = duotone(colors.carmin, colors.roseCarmin),
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                    )
                }
            }
            Text(nowPlaying.title, style = Fern.type.corps, color = colors.creme, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                ControlButton("⏮", onPrevious)
                ControlButton(if (nowPlaying.playing) "⏸" else "▶", onPlayPause)
                ControlButton("⏭", onNext)
            }
        }
    }
}

@Composable
private fun ControlButton(glyph: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Fern.colors.lierre)
            .clickable(onClick = onClick),
    ) {
        Text(glyph, style = Fern.type.corps, color = Fern.colors.creme)
    }
}

// ─── Contexte ───────────────────────────────────────────────────────────────

/**
 * Contexte : un mot selon le moment de la journée, le prochain événement (agenda),
 * le coucher du soleil le soir, et une note du jour (toucher pour l'écrire).
 */
@Composable
fun ContextWidget(
    note: String,
    nextEvent: EventResult?,
    place: PlaceSettings,
    onNoteChange: (String) -> Unit,
) {
    val now = rememberMinute()
    val colors = Fern.colors
    var editing by remember { mutableStateOf(false) }
    val hour = now.hour
    val (moment, greeting) = when (hour) {
        in 5..11 -> "Matin" to "Bonjour ☀"
        in 12..17 -> "Journée" to "Bel après-midi"
        in 18..22 -> "Soir" to "Bonsoir"
        else -> "Nuit" to "Douce nuit"
    }
    val sunset: LocalTime? = remember(now.toLocalDate(), place) {
        Astro.sunTimes(LocalDate.now(), place.latitude, place.longitude, ZoneId.systemDefault()).sunset
    }

    WidgetCard(onClick = { editing = true }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(moment.uppercase(), style = Fern.type.libelle, color = colors.roseCarmin)
            Text(greeting, style = Fern.type.titreWidget, color = colors.creme)
            if (nextEvent != null) {
                Text(
                    "${nextEvent.whenLabel.uppercase()} · ${nextEvent.title}",
                    style = Fern.type.corps,
                    color = colors.lichen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hour in 16..21 && sunset != null) {
                Text("Coucher du soleil à ${sunset.format(hm)}", style = Fern.type.corps, color = colors.lichen)
            }
            Text(
                note.ifBlank { "Touche pour écrire la note du jour" },
                style = Fern.type.corps,
                color = if (note.isBlank()) colors.moussePale else colors.creme,
            )
        }
    }

    if (editing) {
        TextInputDialog(
            title = "Note du jour",
            initial = note,
            onConfirm = { onNoteChange(it); editing = false },
            onDismiss = { editing = false },
        )
    }
}

// ─── Widget Android ─────────────────────────────────────────────────────────

/** Un widget d'une autre appli, affiché tel quel dans une carte arrondie. */
@Composable
fun AppWidgetView(host: WidgetHost, appWidgetId: Int, heightDp: Int) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        val widthDp = maxWidth.value.toInt()
        AndroidView(
            factory = { ctx ->
                host.createView(ctx, appWidgetId, widthDp, heightDp)
                    ?: TextView(ctx).apply { text = "Widget indisponible" }
            },
            // Quand la taille change (⤢ Taille), on prévient le widget pour qu'il s'adapte.
            update = { view -> (view as? AppWidgetHostView)?.let { host.resize(it, widthDp, heightDp) } },
            modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        )
    }
}
