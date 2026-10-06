package com.atelierjlg.fern.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atelierjlg.fern.BuildConfig
import com.atelierjlg.fern.LauncherViewModel
import com.atelierjlg.fern.data.DrawerSort
import com.atelierjlg.fern.data.DrawerStyle
import com.atelierjlg.fern.data.GestureAction
import com.atelierjlg.fern.data.GestureBinding
import com.atelierjlg.fern.data.GestureSettings
import com.atelierjlg.fern.data.NamedTheme
import com.atelierjlg.fern.data.RADIAL_SIZE
import com.atelierjlg.fern.data.SlotRef
import com.atelierjlg.fern.data.ThemePresets
import com.atelierjlg.fern.system.FernAccessibilityService
import com.atelierjlg.fern.ui.common.AppIcon
import com.atelierjlg.fern.ui.common.AppPicker
import com.atelierjlg.fern.ui.common.EmptySlot
import com.atelierjlg.fern.ui.common.GlyphButton
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern

/** Les rubriques des Paramètres. */
private enum class Section(val title: String) {
    Theme("Thème"),
    Spaces("Spaces"),
    Focus("Focus"),
    Gestes("Gestes"),
    Recherche("Recherche"),
    Tiroir("Tiroir"),
    Lieu("Lieu (ciel)"),
    Sauvegarde("Sauvegarde"),
    APropos("À propos"),
}

/** Moteurs de recherche proposés (%s = texte cherché). */
private val SEARCH_ENGINES = listOf(
    "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
    "Startpage" to "https://www.startpage.com/do/search?q=%s",
    "Qwant" to "https://www.qwant.com/?q=%s",
    "Ecosia" to "https://www.ecosia.org/search?q=%s",
    "Brave Search" to "https://search.brave.com/search?q=%s",
    "Google" to "https://www.google.com/search?q=%s",
)

/**
 * L'onglet Paramètres : plein écran, une liste de rubriques.
 * On l'ouvre depuis le mode édition ou depuis le tiroir.
 */
@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf<Section?>(null) }

    // Retour : revient au menu, puis ferme.
    BackHandler { if (section != null) section = null else vm.closeSettings() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fern.colors.nuit)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            GlyphButton("←", onClick = { if (section != null) section = null else vm.closeSettings() })
            Spacer(Modifier.width(12.dp))
            Text(
                text = (section?.title ?: "Paramètres").uppercase(),
                style = Fern.type.titreWidget,
                color = Fern.colors.creme,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            when (section) {
                null -> items(Section.entries.toList()) { s ->
                    SettingRow(title = s.title, onClick = { section = s })
                }
                Section.Theme -> item { ThemeSection(vm, config.theme, config.savedThemes) }
                Section.Spaces -> item { SpacesSection(vm, config) }
                Section.Focus -> item { FocusSection(vm, config) }
                Section.Gestes -> item { GesturesSection(vm, config.gestures) }
                Section.Recherche -> searchSection(vm, config.search.extended, config.search.webSearchUrl)
                Section.Tiroir -> drawerSection(vm, config.drawer)
                Section.Lieu -> item { PlaceSection(vm, config.place) }
                Section.Sauvegarde -> item { BackupSection(vm) }
                Section.APropos -> item { AboutSection() }
            }
        }
    }
}

// ─── Briques communes ───────────────────────────────────────────────────────

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Fern.colors.mousse, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Fern.type.corps, color = Fern.colors.creme)
            if (subtitle != null) Text(subtitle, style = Fern.type.nomApp, color = Fern.colors.lichen)
        }
        trailing()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title = title, subtitle = subtitle, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Fern.colors.nuit,
                checkedTrackColor = Fern.colors.pistache,
                uncheckedThumbColor = Fern.colors.lichen,
                uncheckedTrackColor = Fern.colors.lierre,
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = Fern.type.libelle,
        color = Fern.colors.roseCarmin,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp),
    )
}

/** Une boîte de choix dans une liste. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { option ->
                    TextButton(onClick = { onPick(option) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label(option), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

@Composable
private fun Swatch(hex: String, size: Int = 26) {
    Box(
        Modifier
            .size(size.dp)
            .background(parseColor(hex), CircleShape)
            .border(1.dp, Fern.colors.sousBois, CircleShape),
    )
}

// ─── Thème ──────────────────────────────────────────────────────────────────

@Composable
private fun ThemeSection(vm: LauncherViewModel, theme: NamedTheme, saved: List<NamedTheme>) {
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var naming by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportTheme(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importTheme(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Thème actif · ${theme.name}")
        for ((label, key, hex) in theme.colors.entries()) {
            SettingRow(title = label, subtitle = hex, onClick = { editing = key to hex }) { Swatch(hex) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            PillButton("Enregistrer", onClick = { naming = true }, accent = true)
            PillButton("Exporter", onClick = { exportLauncher.launch("theme-fern-${theme.name}.json") })
            PillButton("Importer", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
        }
        Text(
            "Exporter crée un petit fichier à envoyer à un proche : il l'importe dans son Fern.",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )

        SectionLabel("Thèmes prêts")
        for (preset in ThemePresets.all) {
            ThemeRow(preset, onApply = { vm.applyTheme(preset) })
        }
        if (saved.isNotEmpty()) {
            SectionLabel("Mes thèmes")
            for (t in saved) {
                ThemeRow(t, onApply = { vm.applyTheme(t) }, onDelete = { vm.deleteSavedTheme(t.name) })
            }
        }
    }

    editing?.let { (key, hex) ->
        ColorDialog(
            initial = hex,
            onConfirm = { vm.setThemeColor(key, it); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (naming) {
        TextInputDialog(
            title = "Nom du thème",
            initial = theme.name,
            onConfirm = { vm.saveTheme(it); naming = false },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun ThemeRow(theme: NamedTheme, onApply: () -> Unit, onDelete: (() -> Unit)? = null) {
    SettingRow(title = theme.name, onClick = onApply) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(theme.colors.nuit, theme.colors.mousse, theme.colors.creme, theme.colors.roseCarmin, theme.colors.pistache)
                .forEach { Swatch(it, size = 22) }
        }
        if (onDelete != null) {
            Spacer(Modifier.width(8.dp))
            GlyphButton("✕", onClick = onDelete)
        }
    }
}

// ─── Gestes ─────────────────────────────────────────────────────────────────

@Composable
private fun GesturesSection(vm: LauncherViewModel, gestures: GestureSettings) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf<String?>(null) }
    var pickingAppFor by remember { mutableStateOf<String?>(null) }

    fun bindingOf(name: String) = when (name) {
        "up" -> gestures.swipeUp
        "down" -> gestures.swipeDown
        "double" -> gestures.doubleTap
        else -> gestures.longPress
    }

    fun set(name: String, binding: GestureBinding) = vm.updateGestures {
        when (name) {
            "up" -> it.copy(swipeUp = binding)
            "down" -> it.copy(swipeDown = binding)
            "double" -> it.copy(doubleTap = binding)
            else -> it.copy(longPress = binding)
        }
    }

    fun describe(binding: GestureBinding): String =
        if (binding.action == GestureAction.Appli) {
            "Ouvrir " + (apps.find(binding.appKey)?.label ?: "une appli")
        } else {
            binding.action.label
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((name, title) in listOf("up" to "Glisser vers le haut", "down" to "Glisser vers le bas", "double" to "Double appui", "long" to "Appui long")) {
            SettingRow(title = title, subtitle = describe(bindingOf(name)), onClick = { choosing = name })
        }

        SectionLabel("Roue d'applis")
        Text(
            "Choisis « Roue d'applis » pour un geste (par exemple l'appui long), puis remplis-la ici.",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until RADIAL_SIZE) {
                val app = apps.find(gestures.radialApps.getOrNull(i))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (app != null) {
                        AppIcon(app = app, iconSize = 36.dp, showLabel = false, onClick = { vm.pickFor(SlotRef.Radial(i)) })
                    } else {
                        EmptySlot(iconSize = 36.dp, onClick = { vm.pickFor(SlotRef.Radial(i)) })
                    }
                }
            }
        }

        SectionLabel("Verrouillage")
        val enabled = FernAccessibilityService.instance != null
        SettingRow(
            title = if (enabled) "Verrouillage activé ✓" else "Activer le verrouillage",
            subtitle = "Accessibilité → Fern Launcher. Fern ne lit rien à l'écran.",
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
    }

    choosing?.let { name ->
        ChoiceDialog(
            title = "Action",
            options = GestureAction.entries.toList(),
            label = { it.label },
            onPick = { action ->
                choosing = null
                if (action == GestureAction.Appli) pickingAppFor = name else set(name, GestureBinding(action))
            },
            onDismiss = { choosing = null },
        )
    }
    pickingAppFor?.let { name ->
        AppPicker(
            apps = apps.visible,
            current = null,
            onPick = { app ->
                if (app != null) set(name, GestureBinding(GestureAction.Appli, app.key))
                pickingAppFor = null
            },
            onDismiss = { pickingAppFor = null },
        )
    }
}

// ─── Recherche ──────────────────────────────────────────────────────────────

private fun LazyListScope.searchSection(vm: LauncherViewModel, extended: Boolean, url: String) {
    item {
        ToggleRow(
            title = "Recherche étendue",
            subtitle = "Contacts, agenda, raccourcis d'applis et calculatrice, en plus des applis.",
            checked = extended,
            onChange = { on -> vm.updateSearch { it.copy(extended = on) } },
        )
    }
    item { SectionLabel("Moteur de recherche web") }
    items(SEARCH_ENGINES) { (name, engineUrl) ->
        SettingRow(title = name, onClick = { vm.updateSearch { it.copy(webSearchUrl = engineUrl) } }) {
            if (engineUrl == url) Text("✓", style = Fern.type.corps, color = Fern.colors.pistache)
        }
    }
    item {
        Text(
            "La recherche s'ouvre dans Firefox s'il est installé, comme un lien ordinaire " +
                "(ton réglage de navigation privée s'applique).",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ─── Tiroir ─────────────────────────────────────────────────────────────────

private fun LazyListScope.drawerSection(vm: LauncherViewModel, drawer: com.atelierjlg.fern.data.DrawerSettings) {
    item {
        SettingRow(
            title = "Affichage",
            subtitle = if (drawer.style == DrawerStyle.Grille) "Grille · ${drawer.columns} colonnes" else "Liste",
            onClick = {
                vm.updateDrawer {
                    when {
                        it.style == DrawerStyle.Liste -> it.copy(style = DrawerStyle.Grille, columns = 4)
                        it.columns < 5 -> it.copy(columns = 5)
                        else -> it.copy(style = DrawerStyle.Liste)
                    }
                }
            },
        )
    }
    item {
        SettingRow(
            title = "Tri",
            subtitle = if (drawer.sort == DrawerSort.Alphabetique) "Alphabétique (A–Z)" else "Les plus utilisées d'abord",
            onClick = {
                vm.updateDrawer {
                    it.copy(sort = if (it.sort == DrawerSort.Alphabetique) DrawerSort.Frequence else DrawerSort.Alphabetique)
                }
            },
        )
    }
    item {
        ToggleRow(
            title = "Ouverture directe",
            subtitle = "Quand la recherche ne trouve qu'une appli, elle s'ouvre toute seule.",
            checked = drawer.autoLaunchSingleResult,
            onChange = { on -> vm.updateDrawer { it.copy(autoLaunchSingleResult = on) } },
        )
    }
}

// ─── Lieu ───────────────────────────────────────────────────────────────────

@Composable
private fun PlaceSection(vm: LauncherViewModel, place: com.atelierjlg.fern.data.PlaceSettings) {
    var editing by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Sert au widget Ciel (lever et coucher du soleil). Pas de GPS : tu indiques ta ville une fois.",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
        SettingRow(title = "Ville", subtitle = place.name, onClick = { editing = "name" })
        SettingRow(title = "Latitude", subtitle = place.latitude.toString(), onClick = { editing = "lat" })
        SettingRow(title = "Longitude", subtitle = place.longitude.toString(), onClick = { editing = "lon" })
    }
    editing?.let { field ->
        TextInputDialog(
            title = when (field) {
                "name" -> "Ville"
                "lat" -> "Latitude (ex. 47.2184)"
                else -> "Longitude (ex. -1.5536)"
            },
            initial = when (field) {
                "name" -> place.name
                "lat" -> place.latitude.toString()
                else -> place.longitude.toString()
            },
            onConfirm = { text ->
                val number = text.replace(',', '.').trim().toDoubleOrNull()
                vm.updatePlace {
                    when (field) {
                        "name" -> it.copy(name = text.trim().ifEmpty { it.name })
                        "lat" -> if (number != null && number in -90.0..90.0) it.copy(latitude = number) else it
                        else -> if (number != null && number in -180.0..180.0) it.copy(longitude = number) else it
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ─── Sauvegarde ─────────────────────────────────────────────────────────────

@Composable
private fun BackupSection(vm: LauncherViewModel) {
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Toute ta configuration (pages, packs, dock, thème, gestes…) dans un seul fichier. " +
                "À garder sur ton Drive avant une grosse mise à jour.",
            style = Fern.type.corps,
            color = Fern.colors.lichen,
        )
        SettingRow(title = "Exporter une sauvegarde", onClick = { exportLauncher.launch("fern-sauvegarde.json") })
        SettingRow(
            title = "Restaurer une sauvegarde",
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
    }
    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Restaurer ?") },
            text = { Text("Ta configuration actuelle sera remplacée par celle du fichier.") },
            confirmButton = { TextButton(onClick = { vm.importBackup(uri); confirmImport = null }) { Text("Restaurer") } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Annuler") } },
        )
    }
}

// ─── À propos ───────────────────────────────────────────────────────────────

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow(title = "Fern Launcher", subtitle = "Version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})", onClick = {})
        SettingRow(
            title = "Écran d'accueil par défaut",
            subtitle = "Choisir Fern (ou revenir à un autre lanceur)",
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
        SettingRow(
            title = "Code source",
            subtitle = "github.com/atelier-jlg/Fern-Launcher",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/atelier-jlg/Fern-Launcher"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Police Bricolage Grotesque (SIL OFL 1.1).",
            style = Fern.type.nomApp,
            color = Fern.colors.moussePale,
        )
    }
}
