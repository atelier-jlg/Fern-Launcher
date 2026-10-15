package com.atelierjlg.fern.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.atelierjlg.fern.theme.FernSharedTheme
import com.atelierjlg.fern.ui.kit.FernField
import com.atelierjlg.fern.ui.kit.Pill
import com.atelierjlg.fern.ui.kit.SectionLabel
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons

/**
 * L'écran de réglages de Fern Clavier (icône dans le tiroir, ou roue dentée sur le clavier).
 * 1. Mise en route : activer le clavier, puis le choisir.
 * 2. Un champ pour l'essayer.
 * 3. Les options.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { FernSharedTheme { SettingsScreen() } }
    }
}

/** Fern Clavier est-il activé / choisi comme clavier ? */
private object Setup {
    private fun id(context: Context) = ComponentName(context, FernKeyboardService::class.java)

    fun isEnabled(context: Context): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.component == id(context) }
    }

    fun isDefault(context: Context): Boolean {
        val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: return false
        return ComponentName.unflattenFromString(current) == id(context)
    }
}

@Composable
private fun SettingsScreen() {
    val c = Fern.colors
    val context = LocalContext.current
    var settings by remember { mutableStateOf(KeyboardPrefs.load(context)) }
    var check by remember { mutableIntStateOf(0) }
    var trial by remember { mutableStateOf("") }

    // Revenir des réglages d'Android : on revérifie l'état.
    LifecycleResumeEffect(Unit) {
        check++
        onPauseOrDispose { }
    }
    val enabled = remember(check) { Setup.isEnabled(context) }
    val isDefault = remember(check) { Setup.isDefault(context) }

    fun update(new: KeyboardSettings) {
        settings = new
        KeyboardPrefs.save(context, new)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.nuit)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Fern Clavier", style = Fern.type.titreWidget, color = c.creme)
        Text(
            "Version ${BuildConfig.VERSION_NAME} · sans accès à Internet : ce que tu tapes ne sort jamais du téléphone.",
            style = Fern.type.nomApp,
            color = c.lichen,
        )

        SectionLabel("Mise en route")
        Card {
            StepRow(
                number = "1",
                title = "Activer Fern Clavier",
                detail = if (enabled) "C'est fait" else "Dans la liste des claviers d'Android",
                done = enabled,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
            StepRow(
                number = "2",
                title = "Le choisir comme clavier",
                detail = if (isDefault) "C'est ton clavier" else "Touche ici puis choisis « Fern Clavier »",
                done = isDefault,
                onClick = {
                    context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                    check++
                },
            )
        }

        SectionLabel("Essayer")
        FernField(
            value = trial,
            onValueChange = { trial = it },
            placeholder = "Écris quelque chose…",
            singleLine = false,
        )

        SectionLabel("Frappe")
        Card {
            Toggle("Majuscules automatiques", "En début de phrase", settings.autoCaps) { update(settings.copy(autoCaps = it)) }
            Toggle("Point automatique", "Deux espaces → « . »", settings.autoPeriod) { update(settings.copy(autoPeriod = it)) }
            Toggle("Rangée de chiffres", "Sinon : appui long sur la rangée du haut", settings.numberRow) { update(settings.copy(numberRow = it)) }
            Toggle("Curseur sur la barre d'espace", "Glisser sur l'espace déplace le curseur", settings.cursorOnSpace) {
                update(settings.copy(cursorOnSpace = it))
            }
        }

        SectionLabel("Toucher")
        Card {
            Toggle("Vibration", "Petit « tic » à chaque touche", settings.haptics) { update(settings.copy(haptics = it)) }
            Toggle("Son", "Clic des touches (suit le volume du système)", settings.sound) { update(settings.copy(sound = it)) }
        }

        SectionLabel("Apparence")
        Card {
            Text("Hauteur des touches", style = Fern.type.corps, color = c.creme)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyHeight.entries.forEach { h ->
                    Pill(h.label, selected = settings.keyHeight == h) { update(settings.copy(keyHeight = h)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Toggle("Écume animée", "La vague ondule un instant à l'ouverture", settings.foamAnimation) {
                update(settings.copy(foamAnimation = it))
            }
            Text(
                "Les couleurs suivent le thème de Fern Launcher.",
                style = Fern.type.nomApp,
                color = c.lichen,
            )
        }

        SectionLabel("Bientôt")
        Text(
            "Correction automatique et suggestions, presse-papiers (5 derniers), emoji favoris, " +
                "memes et stickers, dictée Whisper hors ligne.",
            style = Fern.type.nomApp,
            color = c.lichen,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Fern.colors.mousse)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun StepRow(number: String, title: String, detail: String, done: Boolean, onClick: () -> Unit) {
    val c = Fern.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (done) c.pistache else c.lierre),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                FernIcon(FernIcons.Check, c.nuit, size = 20.dp)
            } else {
                Text(number, style = Fern.type.corps, color = c.creme)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Fern.type.corps, color = c.creme)
            Text(detail, style = Fern.type.nomApp, color = c.lichen)
        }
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Fern.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Fern.type.corps, color = c.creme)
            Text(detail, style = Fern.type.nomApp, color = c.lichen)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.pistache, checkedThumbColor = c.nuit, uncheckedTrackColor = c.lierre),
        )
    }
}
