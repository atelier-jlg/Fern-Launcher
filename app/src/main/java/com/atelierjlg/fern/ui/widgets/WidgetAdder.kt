package com.atelierjlg.fern.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.LauncherViewModel
import com.atelierjlg.fern.data.ContextBlock
import com.atelierjlg.fern.data.AlternanceBlock
import com.atelierjlg.fern.data.CarnetBlock
import com.atelierjlg.fern.data.MaisonBlock
import com.atelierjlg.fern.data.MaisonKind
import com.atelierjlg.fern.data.RevisionsBlock
import com.atelierjlg.fern.data.HomeBlock
import com.atelierjlg.fern.data.MusicBlock
import com.atelierjlg.fern.data.SkyBlock
import com.atelierjlg.fern.data.newId
import com.atelierjlg.fern.ui.theme.Fern

/** Un ajout de widget Android en cours (entre les étapes « autoriser » et « configurer »). */
private data class Pending(val pageId: String, val id: Int, val info: AppWidgetProviderInfo)

/**
 * La boîte « Ajouter un widget » : les widgets maison, puis tous les widgets Android.
 * Gère les étapes imposées par Android (autorisation, puis configuration du widget).
 */
@Composable
fun WidgetAdder(vm: LauncherViewModel, pageId: String, onDone: () -> Unit) {
    val host = vm.widgetHost
    var showAndroidList by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val density = LocalDensity.current

    fun finish(p: Pending) {
        // Hauteur de départ : celle demandée par le widget, entre 120 et 320 dp.
        val heightDp = with(density) { p.info.minHeight.toDp().value.toInt() }.coerceIn(120, 320)
        vm.addAppWidget(p.pageId, p.id, host.label(p.info), heightDp)
        pending = null
        onDone()
    }

    fun cancel(p: Pending) {
        host.delete(p.id)
        pending = null
        onDone()
    }

    val configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending?.let { p -> if (result.resultCode == Activity.RESULT_OK) finish(p) else cancel(p) }
    }

    fun configureOrFinish(p: Pending) {
        val configure = p.info.configure
        if (configure == null) {
            finish(p)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(configure)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, p.id)
        try {
            configureLauncher.launch(intent)
        } catch (e: Exception) {
            // Écran de configuration inaccessible : on ajoute le widget tel quel.
            finish(p)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending?.let { p -> if (result.resultCode == Activity.RESULT_OK) configureOrFinish(p) else cancel(p) }
    }

    fun start(info: AppWidgetProviderInfo) {
        val p = Pending(pageId, host.allocateId(), info)
        pending = p
        if (host.bindIfAllowed(p.id, info)) {
            configureOrFinish(p)
        } else {
            // Android demande à Jules d'autoriser Fern à afficher les widgets de cette appli.
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, p.id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            )
        }
    }

    if (pending != null) return

    if (!showAndroidList) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Ajouter un widget") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((label, block) in listOf<Pair<String, () -> HomeBlock>>(
                        "Ciel · soleil et lune" to { SkyBlock(newId()) },
                        "Musique · ce qui joue" to { MusicBlock(newId()) },
                        "Contexte · moment, agenda, note" to { ContextBlock(newId()) },
                        "Alternance · école ou entreprise" to { AlternanceBlock(newId()) },
                        "Révisions · cartes AnkiDroid" to { RevisionsBlock(newId()) },
                        "Carnet du jour · humeur, habitudes, note" to { CarnetBlock(newId()) },
                        "Cours du jour · prochain cours, examens" to { MaisonBlock(newId(), MaisonKind.Cours) },
                        "Le chat · il dort, s'étire, veille" to { MaisonBlock(newId(), MaisonKind.Chat) },
                        "Météo · en pixel art" to { MaisonBlock(newId(), MaisonKind.Meteo) },
                        "Plante · pousse avec ton Carnet" to { MaisonBlock(newId(), MaisonKind.Plante) },
                        "Pomodoro · 25 min, mode Focus" to { MaisonBlock(newId(), MaisonKind.Pomodoro) },
                        "Temps d'écran · une jauge douce" to { MaisonBlock(newId(), MaisonKind.TempsEcran) },
                    )) {
                        TextButton(onClick = { vm.addBlock(pageId, block()); onDone() }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(onClick = { showAndroidList = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Widget d'une autre appli…", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Annuler") } },
        )
    } else {
        val providers = remember { host.providers() }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Widgets des applis") },
            text = {
                LazyColumn {
                    items(providers) { info ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { start(info) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(host.label(info), style = Fern.type.corps)
                            Text(info.provider.packageName, style = Fern.type.nomApp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Annuler") } },
        )
    }
}
