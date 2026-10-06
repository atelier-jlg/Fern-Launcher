package com.atelierjlg.fern.ui.home

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.R
import com.atelierjlg.fern.ui.theme.Fern
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Part de la hauteur d'écran occupée par le bandeau pixel art sur l'accueil. */
private const val BANDEAU_ACCUEIL = 0.29f

/**
 * La page d'accueil. Pour l'instant : fonds, horloge et date.
 * Glisser vers le haut ouvre le tiroir, vers le bas ouvre la recherche.
 */
@Composable
fun HomeScreen(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    // rememberUpdatedState : le détecteur de gestes, créé une seule fois,
    // appelle toujours la version la plus récente de ces fonctions.
    val swipeUp by rememberUpdatedState(onSwipeUp)
    val swipeDown by rememberUpdatedState(onSwipeDown)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(threshold) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    },
                    onDragEnd = {
                        when {
                            total < -threshold -> swipeUp()
                            total > threshold -> swipeDown()
                        }
                    },
                )
            },
    ) {
        val bandeauHeight = maxHeight * BANDEAU_ACCUEIL

        // Couche 1 : fond topographique. Couche 2 : bandeau pixel art détouré par la vague.
        Image(
            painter = painterResource(R.drawable.fond_topo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.bandeau_accueil),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(bandeauHeight))
            Clock()
            Spacer(Modifier.weight(1f))
            Text(
                text = "↑ applis · ↓ chercher".uppercase(),
                style = Fern.type.libelle,
                color = Fern.colors.moussePale,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

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
private fun Clock() {
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
        Box(Modifier.padding(start = 4.dp)) {
            Text(
                text = now.format(dateFormat).uppercase(Locale.FRENCH),
                style = Fern.type.libelle,
                color = colors.lichen,
            )
        }
    }
}
