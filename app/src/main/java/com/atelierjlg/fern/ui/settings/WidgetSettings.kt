package com.atelierjlg.fern.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.LauncherViewModel
import com.atelierjlg.fern.data.AltPeriod
import com.atelierjlg.fern.data.AltType
import com.atelierjlg.fern.data.AlternanceSettings
import com.atelierjlg.fern.data.CarnetSettings
import com.atelierjlg.fern.data.CatPose
import com.atelierjlg.fern.data.ChatSettings
import com.atelierjlg.fern.data.CoursSettings
import com.atelierjlg.fern.data.PomodoroSettings
import com.atelierjlg.fern.data.ScreenTimeSettings
import com.atelierjlg.fern.widgets.CalendarInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.produceState
import com.atelierjlg.fern.ui.common.GlyphButton
import com.atelierjlg.fern.ui.common.PillButton
import com.atelierjlg.fern.ui.common.TextInputDialog
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.widgets.Alternance
import java.time.LocalDate

@Composable
private fun Row2(title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Fern.colors.mousse, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Fern.type.corps, color = Fern.colors.creme)
            if (subtitle != null) Text(subtitle, style = Fern.type.nomApp, color = Fern.colors.lichen)
        }
        trailing()
    }
}

// ─── Alternance ─────────────────────────────────────────────────────────────

/** Paramètres → Alternance : noms, périodes, et générateur de rythme. */
@Composable
fun AlternanceSection(vm: LauncherViewModel, settings: AlternanceSettings) {
    var editingName by remember { mutableStateOf<AltType?>(null) }
    var adding by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row2("École", settings.schoolName, onClick = { editingName = AltType.Ecole })
        Row2("Entreprise", settings.companyName, onClick = { editingName = AltType.Entreprise })
        Row2("Mission à l'international", settings.missionName, onClick = { editingName = AltType.Mission })
        Text("PÉRIODES", style = Fern.type.libelle, color = Fern.colors.roseCarmin, modifier = Modifier.padding(top = 10.dp))
        if (settings.periods.isEmpty()) {
            Text(
                "Ajoute tes périodes une par une, ou génère ton rythme (ex. 2 semaines d'école, 3 en entreprise).",
                style = Fern.type.nomApp,
                color = Fern.colors.lichen,
            )
        }
        for (period in settings.periods.sortedBy { it.start }) {
            Row2(
                title = settings.nameOf(period.type),
                subtitle = "${Alternance.shortDate(period.start)} → ${Alternance.shortDate(period.end)}",
                onClick = {},
            ) {
                GlyphButton("✕", onClick = { vm.updateAlternance { it.copy(periods = it.periods - period) } })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("+ Période", onClick = { adding = true })
            PillButton("Générer un rythme", onClick = { generating = true })
        }
        // Le calendrier officiel de l'ESB (remplace les périodes existantes).
        PillButton("Charger le calendrier ESB 2026/27", onClick = {
            vm.updateAlternance { it.copy(periods = Alternance.esbIngenieur1) }
        })
        if (settings.periods.isNotEmpty()) {
            PillButton("Tout effacer", onClick = { vm.updateAlternance { it.copy(periods = emptyList()) } })
        }
    }

    editingName?.let { type ->
        TextInputDialog(
            title = when (type) {
                AltType.Ecole -> "Nom de l'école"
                AltType.Entreprise -> "Nom de l'entreprise"
                AltType.Mission -> "Nom de la mission"
            },
            initial = settings.nameOf(type),
            onConfirm = { name ->
                vm.updateAlternance {
                    when (type) {
                        AltType.Ecole -> it.copy(schoolName = name.ifBlank { it.schoolName })
                        AltType.Entreprise -> it.copy(companyName = name.ifBlank { it.companyName })
                        AltType.Mission -> it.copy(missionName = name.ifBlank { it.missionName })
                    }
                }
                editingName = null
            },
            onDismiss = { editingName = null },
        )
    }
    if (adding) {
        PeriodDialog(
            settings = settings,
            onConfirm = { p -> vm.updateAlternance { it.copy(periods = (it.periods + p).sortedBy { x -> x.start }) }; adding = false },
            onDismiss = { adding = false },
        )
    }
    if (generating) {
        RhythmDialog(
            settings = settings,
            onConfirm = { list -> vm.updateAlternance { it.copy(periods = list) }; generating = false },
            onDismiss = { generating = false },
        )
    }
}

@Composable
private fun TypePicker(settings: AlternanceSettings, type: AltType, withMission: Boolean = false, onChange: (AltType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(settings.schoolName, onClick = { onChange(AltType.Ecole) }, accent = type == AltType.Ecole)
        PillButton(settings.companyName, onClick = { onChange(AltType.Entreprise) }, accent = type == AltType.Entreprise)
        if (withMission) {
            PillButton(settings.missionName, onClick = { onChange(AltType.Mission) }, accent = type == AltType.Mission)
        }
    }
}

@Composable
private fun PeriodDialog(settings: AlternanceSettings, onConfirm: (AltPeriod) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(AltType.Ecole) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    val startDate = Alternance.parseDate(start)
    val endDate = Alternance.parseDate(end)
    val valid = startDate != null && endDate != null && !endDate.isBefore(startDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle période") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TypePicker(settings, type, withMission = true) { type = it }
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Début (jj/mm/aaaa)") }, singleLine = true)
                OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Fin incluse (jj/mm/aaaa)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(AltPeriod(type, startDate.toString(), endDate.toString())) }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun RhythmDialog(settings: AlternanceSettings, onConfirm: (List<AltPeriod>) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf(AltType.Ecole) }
    var start by remember { mutableStateOf("") }
    var until by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("2") }
    var company by remember { mutableStateOf("3") }
    val startDate = Alternance.parseDate(start)
    val untilDate = Alternance.parseDate(until)
    val schoolWeeks = school.toIntOrNull()?.takeIf { it in 1..52 }
    val companyWeeks = company.toIntOrNull()?.takeIf { it in 1..52 }
    val valid = startDate != null && untilDate != null && untilDate.isAfter(startDate) && schoolWeeks != null && companyWeeks != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Générer un rythme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("On commence par :")
                TypePicker(settings, first) { first = it }
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("À partir du (jj/mm/aaaa)") }, singleLine = true)
                OutlinedTextField(value = until, onValueChange = { until = it }, label = { Text("Jusqu'au (jj/mm/aaaa)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = school,
                        onValueChange = { school = it },
                        label = { Text("Sem. ${settings.schoolName}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Sem. ${settings.companyName}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Remplace les périodes existantes.", style = Fern.type.nomApp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(Alternance.generate(startDate!!, untilDate!!, schoolWeeks!!, companyWeeks!!, first))
            }) { Text("Générer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ─── Carnet ─────────────────────────────────────────────────────────────────

/** Paramètres → Carnet : les 3 habitudes et la destination Obsidian. */
@Composable
fun CarnetSection(vm: LauncherViewModel, settings: CarnetSettings) {
    var editing by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("HABITUDES", style = Fern.type.libelle, color = Fern.colors.roseCarmin)
        for (i in 0 until 3) {
            Row2("Habitude ${i + 1}", settings.habits.getOrNull(i) ?: "—", onClick = { editing = "habit:$i" })
        }
        Text("OBSIDIAN", style = Fern.type.libelle, color = Fern.colors.roseCarmin, modifier = Modifier.padding(top = 10.dp))
        Row2("Coffre", settings.obsidianVault.ifBlank { "Le dernier ouvert" }, onClick = { editing = "vault" })
        Row2("Dossier des notes du jour", settings.obsidianFolder.ifBlank { "Racine du coffre" }, onClick = { editing = "folder" })
        Text(
            "« → Obsidian » ajoute ta note (avec l'humeur et les habitudes) à la fin de la note du jour, " +
                "par exemple « ${settings.obsidianFolder.ifBlank { "" }}/${LocalDate.now()} ».",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
    }
    editing?.let { field ->
        val initial = when {
            field.startsWith("habit:") -> settings.habits.getOrNull(field.substringAfter(':').toInt()) ?: ""
            field == "vault" -> settings.obsidianVault
            else -> settings.obsidianFolder
        }
        TextInputDialog(
            title = when {
                field.startsWith("habit:") -> "Habitude"
                field == "vault" -> "Nom du coffre (vide = le dernier ouvert)"
                else -> "Dossier (vide = racine)"
            },
            initial = initial,
            onConfirm = { text ->
                vm.updateCarnetSettings { s ->
                    when {
                        field.startsWith("habit:") -> {
                            val i = field.substringAfter(':').toInt()
                            val list = (s.habits + List(3) { "" }).take(3).toMutableList()
                            list[i] = text.trim().ifEmpty { list[i] }
                            s.copy(habits = list)
                        }
                        field == "vault" -> s.copy(obsidianVault = text.trim())
                        else -> s.copy(obsidianFolder = text.trim().trim('/'))
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ─── Cours du jour ──────────────────────────────────────────────────────────

/** Paramètres → Cours du jour : les agendas à lire et les mots qui signalent un examen. */
@Composable
fun CoursSection(vm: LauncherViewModel, settings: CoursSettings) {
    var reload by remember { mutableStateOf(0) }
    val calendars by produceState<List<CalendarInfo>?>(null, reload) { value = vm.calendars() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload++ }
    var editingKeywords by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Le widget lit l'agenda d'Android. Pour Proton Calendar : lien de partage ICS dans ICSx⁵, " +
                "puis choisis ici l'agenda créé par ICSx⁵.",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
        Text("AGENDAS", style = Fern.type.libelle, color = Fern.colors.roseCarmin, modifier = Modifier.padding(top = 10.dp))
        when {
            calendars == null -> Unit
            calendars!!.isEmpty() -> {
                Text("Aucun agenda visible (autorisation manquante ?)", style = Fern.type.nomApp, color = Fern.colors.lichen)
                PillButton("Autoriser l'agenda", onClick = { permission.launch(android.Manifest.permission.READ_CALENDAR) }, accent = true)
            }
            else -> {
                Text(
                    if (settings.calendarIds.isEmpty()) "Aucun coché = tous les agendas." else "Seuls les agendas cochés sont lus.",
                    style = Fern.type.nomApp,
                    color = Fern.colors.lichen,
                )
                for (cal in calendars!!) {
                    val on = cal.id in settings.calendarIds
                    Row2(cal.name, cal.account, onClick = {
                        vm.updateCours { it.copy(calendarIds = if (on) it.calendarIds - cal.id else it.calendarIds + cal.id) }
                    }) {
                        Text(if (on) "✓" else "", style = Fern.type.corps, color = Fern.colors.pistache)
                    }
                }
            }
        }
        Text("EXAMENS", style = Fern.type.libelle, color = Fern.colors.roseCarmin, modifier = Modifier.padding(top = 10.dp))
        Row2("Mots repérés dans les titres", settings.examKeywords.joinToString(", "), onClick = { editingKeywords = true })
    }
    if (editingKeywords) {
        TextInputDialog(
            title = "Mots séparés par des virgules",
            initial = settings.examKeywords.joinToString(", "),
            onConfirm = { text ->
                vm.updateCours { it.copy(examKeywords = text.split(',').map { w -> w.trim() }.filter { w -> w.isNotEmpty() }) }
                editingKeywords = false
            },
            onDismiss = { editingKeywords = false },
        )
    }
}

// ─── Le chat ────────────────────────────────────────────────────────────────

/** Paramètres → Le chat : nom, images jour / nuit, tâches du jour. */
@Composable
fun ChatSection(vm: LauncherViewModel, settings: ChatSettings) {
    var editing by remember { mutableStateOf<String?>(null) }
    var pickingPose by remember { mutableStateOf(CatPose.Jour) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importChatImage(pickingPose, uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row2("Nom", settings.name, onClick = { editing = "name" })
        Text("IMAGES ET ANIMATIONS", style = Fern.type.libelle, color = Fern.colors.roseCarmin, modifier = Modifier.padding(top = 10.dp))
        Text(
            "Sans image, Fern dessine un chat en pixel art. Tu peux mettre tes PNG, ou des animations en GIF / WebP animé " +
                "(fond transparent). Astuce pixel art : exporte en grand (×8, ex. 32 px → 256 px) pour que ça reste net.",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
        for (pose in CatPose.entries) {
            val file = settings.images[pose]
            Row2(
                pose.label,
                when {
                    file != null -> "Ton image"
                    pose == CatPose.Jour || pose == CatPose.Content -> "Pixel art de Fern"
                    else -> "Comme la journée"
                },
                onClick = {
                    pickingPose = pose
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) {
                if (file != null) GlyphButton("✕", onClick = { vm.updateChat { it.copy(images = it.images - pose) } })
            }
        }
        Text("PARTAGE", style = Fern.type.libelle, color = Fern.colors.roseCarmin, modifier = Modifier.padding(top = 10.dp))
        if (!settings.sync.enabled) {
            Text(
                "Partage les cases avec quelqu'un qui a aussi Fern (ta compagne par exemple) : quand l'un coche « Gamelle », " +
                    "la case se coche chez l'autre. Passe par ntfy.sh (libre, sans compte) ; seuls la date et le nom de la tâche circulent.",
                style = Fern.type.nomApp,
                color = Fern.colors.lichen,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Créer un partage", onClick = { vm.startChatSync(null) }, accent = true)
                PillButton("Rejoindre", onClick = { editing = "join" })
            }
        } else {
            Row2("Code du partage", settings.sync.topic + " · toucher pour l'envoyer", onClick = {
                vm.shareText("Code Fern pour le widget Chat (Paramètres → Le chat → Rejoindre) : ${settings.sync.topic}")
            })
            Row2("Serveur", settings.sync.server, onClick = { editing = "server" })
            if (settings.sync.pending.isNotEmpty()) {
                Text("${settings.sync.pending.size} changement(s) en attente de réseau", style = Fern.type.nomApp, color = Fern.colors.lichen)
            }
            PillButton("Arrêter le partage", onClick = { vm.stopChatSync() })
        }
        Text("TÂCHES DU JOUR", style = Fern.type.libelle, color = Fern.colors.roseCarmin, modifier = Modifier.padding(top = 10.dp))
        Text(
            "La 1re tâche, c'est le repas : quand elle est cochée, le chat a un petit cœur et une bouille contente.",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
        for (i in 0 until 3) {
            Row2("Tâche ${i + 1}", settings.chores.getOrNull(i) ?: "—", onClick = { editing = "chore:$i" })
        }
    }
    editing?.let { field ->
        TextInputDialog(
            title = when (field) {
                "name" -> "Nom du chat"
                "join" -> "Code reçu (fern-chat-…)"
                "server" -> "Serveur ntfy (le même des deux côtés)"
                else -> "Tâche (vide = aucune)"
            },
            initial = when (field) {
                "name" -> settings.name
                "join" -> ""
                "server" -> settings.sync.server
                else -> settings.chores.getOrNull(field.substringAfter(':').toInt()) ?: ""
            },
            onConfirm = { text ->
                if (field == "join") {
                    vm.startChatSync(text)
                    editing = null
                    return@TextInputDialog
                }
                vm.updateChat { c ->
                    if (field == "server") {
                        val url = text.trim().trimEnd('/')
                        if (url.startsWith("https://")) c.copy(sync = c.sync.copy(server = url, lastId = "")) else c
                    } else if (field == "name") {
                        c.copy(name = text.trim().ifEmpty { c.name })
                    } else {
                        val i = field.substringAfter(':').toInt()
                        val list = (c.chores + List(3) { "" }).take(3).toMutableList()
                        list[i] = text.trim()
                        c.copy(chores = list.filter { it.isNotEmpty() }, done = emptyMap())
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ─── Pomodoro ───────────────────────────────────────────────────────────────

@Composable
fun PomodoroSection(vm: LauncherViewModel, settings: PomodoroSettings) {
    var editing by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row2("Travail", "${settings.workMinutes} min", onClick = { editing = "work" })
        Row2("Pause", "${settings.breakMinutes} min", onClick = { editing = "break" })
        Row2(
            "Mode Focus pendant le travail",
            if (settings.autoFocus) "Oui · remis comme avant à la pause" else "Non",
            onClick = { vm.updatePomodoroSettings(settings.workMinutes, settings.breakMinutes, !settings.autoFocus) },
        ) {
            Text(if (settings.autoFocus) "OUI" else "NON", style = Fern.type.libelle, color = Fern.colors.pistache)
        }
        Text(
            "Une notification sonne à la fin du travail et de la pause, même si tu es dans une autre appli.",
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
    }
    editing?.let { field ->
        TextInputDialog(
            title = if (field == "work") "Minutes de travail" else "Minutes de pause",
            initial = (if (field == "work") settings.workMinutes else settings.breakMinutes).toString(),
            onConfirm = { text ->
                val n = text.trim().toIntOrNull()
                if (n != null) {
                    if (field == "work") vm.updatePomodoroSettings(n, settings.breakMinutes, settings.autoFocus)
                    else vm.updatePomodoroSettings(settings.workMinutes, n, settings.autoFocus)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ─── Temps d'écran ──────────────────────────────────────────────────────────

@Composable
fun ScreenTimeSection(vm: LauncherViewModel, settings: ScreenTimeSettings, hasFocusApps: Boolean) {
    var editing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (hasFocusApps) {
                "Le widget compte le temps passé aujourd'hui sur les applis du mode Focus (Paramètres → Focus)."
            } else {
                "Le widget compte le temps passé aujourd'hui sur toutes les applis. Ajoute des applis dans Focus pour ne compter qu'elles."
            },
            style = Fern.type.nomApp,
            color = Fern.colors.lichen,
        )
        Row2("Repère quotidien", "${settings.goalMinutes} min · pas une limite, juste un repère", onClick = { editing = true })
        Row2("Accès aux données d'utilisation", "À autoriser une fois pour Fern", onClick = vm::openUsageAccess)
    }
    if (editing) {
        TextInputDialog(
            title = "Repère en minutes",
            initial = settings.goalMinutes.toString(),
            onConfirm = { text ->
                text.trim().toIntOrNull()?.let { n -> vm.updateScreenTime { it.copy(goalMinutes = n.coerceIn(5, 24 * 60)) } }
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}
