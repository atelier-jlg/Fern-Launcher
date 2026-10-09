package com.atelierjlg.fern.contact.ui

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atelierjlg.fern.contact.ContactViewModel
import com.atelierjlg.fern.contact.Screen
import com.atelierjlg.fern.contact.data.ContactDetail
import com.atelierjlg.fern.contact.data.ContactForm
import com.atelierjlg.fern.contact.data.EmailTypes
import com.atelierjlg.fern.contact.data.Labeled
import com.atelierjlg.fern.contact.data.PhoneTypes
import com.atelierjlg.fern.contact.data.birthdayInput
import com.atelierjlg.fern.contact.data.emailLabel
import com.atelierjlg.fern.contact.data.parseBirthdayInput
import com.atelierjlg.fern.contact.data.phoneLabel
import com.atelierjlg.fern.contact.data.toForm
import com.atelierjlg.fern.ui.kit.*
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.launch

/** « Modifier » ou « Nouveau contact ». Seuls les champs modifiés sont réécrits (voir planEdit). */
@Composable
fun EditScreen(vm: ContactViewModel, id: Long?, prefillNumber: String, me: Boolean = false) {
    val c = Fern.colors
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<ContactDetail?>(null) }
    var form by remember { mutableStateOf<ContactForm?>(if (id == null) ContactForm(phones = listOf(Labeled(value = prefillNumber))) else null) }
    var birthdayText by remember { mutableStateOf("") }
    var newPhoto by remember { mutableStateOf<Uri?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) {
            val d = vm.detail(id)
            original = d
            form = d?.toForm()
            birthdayText = d?.birthday?.value?.let(::birthdayInput).orEmpty()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            newPhoto = uri
            removePhoto = false
        }
    }

    val f = form ?: return
    val birthdayParsed = parseBirthdayInput(birthdayText)

    fun save() {
        if (saving || birthdayParsed == null || f.isEmpty) return
        saving = true
        scope.launch {
            val savedId = vm.save(original, f.copy(birthday = birthdayParsed), me)
            if (savedId != null) {
                val detail = vm.detail(savedId)
                if (detail != null && (newPhoto != null || removePhoto)) vm.setPhoto(detail, newPhoto)
                vm.back()
                if (original == null) vm.open(Screen.Detail(savedId))
                vm.reload()
            }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = { vm.back() }) {
            Pill(if (saving) "…" else "Enregistrer", selected = birthdayParsed != null && !f.isEmpty) { save() }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Photo
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val shownPhoto = when {
                    newPhoto != null -> newPhoto.toString()
                    removePhoto -> null
                    else -> original?.photoUri
                }
                Box(
                    Modifier.clip(CircleShape).clickable {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) { Avatar("${f.given} ${f.family}", shownPhoto, 104.dp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Pill("Photo", selected = false) {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (shownPhoto != null) Pill("Retirer", selected = false) {
                        newPhoto = null
                        removePhoto = true
                    }
                }
            }

            SectionLabel("Nom")
            FernField(f.given, { form = f.copy(given = it) }, "Prénom", Modifier.fillMaxWidth())
            FernField(f.family, { form = f.copy(family = it) }, "Nom", Modifier.fillMaxWidth())
            FernField(f.nickname, { form = f.copy(nickname = it) }, "Surnom", Modifier.fillMaxWidth())
            FernField(f.company, { form = f.copy(company = it) }, "Société", Modifier.fillMaxWidth())

            SectionLabel("Numéros")
            f.phones.forEachIndexed { i, p ->
                LabeledEditor(
                    p, KeyboardType.Phone, "Numéro", phoneLabel(p), PhoneTypes,
                    customType = Phone.TYPE_CUSTOM,
                    onChange = { new -> form = f.copy(phones = f.phones.toMutableList().also { it[i] = new }) },
                    onRemove = { form = f.copy(phones = f.phones.filterIndexed { j, _ -> j != i }) },
                )
            }
            Pill("+ Numéro", selected = false) { form = f.copy(phones = f.phones + Labeled()) }

            SectionLabel("E-mails")
            f.emails.forEachIndexed { i, e ->
                LabeledEditor(
                    e, KeyboardType.Email, "E-mail", emailLabel(e), EmailTypes,
                    customType = Email.TYPE_CUSTOM,
                    onChange = { new -> form = f.copy(emails = f.emails.toMutableList().also { it[i] = new }) },
                    onRemove = { form = f.copy(emails = f.emails.filterIndexed { j, _ -> j != i }) },
                )
            }
            Pill("+ E-mail", selected = false) { form = f.copy(emails = f.emails + Labeled(type = Email.TYPE_HOME)) }

            SectionLabel("Anniversaire")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FernField(
                    birthdayText, { birthdayText = it }, "14/05/1999, 14 mai, 19990514…", Modifier.weight(1f),
                    keyboardType = KeyboardType.Text, error = birthdayParsed == null,
                )
                IconButtonRound(FernIcons.Gift, c.pistache) { pickingDate = true }
            }
            when {
                birthdayParsed == null ->
                    Text("Date illisible : écris par exemple 14/05, 14/05/1999 ou 14 mai, ou utilise le calendrier.", style = Fern.type.nomApp, color = c.roseCarmin)
                birthdayParsed.isNotEmpty() ->
                    Text(com.atelierjlg.fern.contact.data.formatBirthday(birthdayParsed), style = Fern.type.nomApp, color = c.lichen)
            }

            SectionLabel("Note")
            FernField(f.note, { form = f.copy(note = it) }, "Une note", Modifier.fillMaxWidth(), singleLine = false)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pickingDate) {
        BirthdayPicker(
            initial = birthdayParsed,
            onDismiss = { pickingDate = false },
            onPick = { raw ->
                birthdayText = birthdayInput(raw)
                pickingDate = false
            },
        )
    }
}

/** Une valeur + son type (toucher le type passe au suivant : Mobile → Domicile → Travail → Autre). */
@Composable
private fun LabeledEditor(
    value: Labeled,
    keyboard: KeyboardType,
    placeholder: String,
    label: String,
    types: List<Pair<Int, String>>,
    customType: Int,
    onChange: (Labeled) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(label, selected = false) {
            // Cycle des types ; un libellé personnalisé d'origine reste dans le cycle.
            val cycle = types.map { it.first }.let { if (value.type == customType) listOf(customType) + it else it }
            val next = cycle[(cycle.indexOf(value.type) + 1) % cycle.size]
            onChange(value.copy(type = next, label = if (next == customType) value.label else null))
        }
        FernField(value.value, { onChange(value.copy(value = it)) }, placeholder, Modifier.weight(1f), keyboardType = keyboard)
        IconButtonRound(FernIcons.Close, Fern.colors.lichen, onClick = onRemove)
    }
}

/**
 * Le calendrier pour choisir un anniversaire. « Sans l'année » : pour quelqu'un dont on ne connaît
 * que le jour (Android l'enregistre alors sans année).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayPicker(initial: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Fern.colors
    val start = remember(initial) {
        val m = Regex("^(\\d{4}|-)-?(\\d{2})-(\\d{2})").find(initial.orEmpty())
        val y = m?.groupValues?.get(1)?.toIntOrNull() ?: 2000
        val date = m?.let { runCatching { java.time.LocalDate.of(y, it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull() }
            ?: java.time.LocalDate.of(2000, 1, 1)
        date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    var noYear by remember { mutableStateOf(initial?.startsWith("--") == true) }
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = start,
        yearRange = 1900..java.time.LocalDate.now().year,
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton onDismiss()
                val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                onPick(if (noYear) "--%02d-%02d".format(d.monthValue, d.dayOfMonth) else d.toString())
            }) { Text("Choisir", color = c.pistache) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Annuler", color = c.lichen) } },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.mousse),
    ) {
        Column {
            androidx.compose.material3.DatePicker(
                state,
                title = null,
                colors = androidx.compose.material3.DatePickerDefaults.colors(
                    containerColor = c.mousse,
                    selectedDayContainerColor = c.pistache,
                    selectedDayContentColor = c.nuit,
                    todayDateBorderColor = c.pistache,
                    todayContentColor = c.pistache,
                    dayContentColor = c.creme,
                    weekdayContentColor = c.lichen,
                    headlineContentColor = c.creme,
                    navigationContentColor = c.creme,
                    yearContentColor = c.creme,
                    currentYearContentColor = c.pistache,
                    selectedYearContainerColor = c.pistache,
                    selectedYearContentColor = c.nuit,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sans l'année", style = Fern.type.corps, color = c.creme, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(
                    checked = noYear, onCheckedChange = { noYear = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.pistache, checkedThumbColor = c.nuit),
                )
            }
        }
    }
}
