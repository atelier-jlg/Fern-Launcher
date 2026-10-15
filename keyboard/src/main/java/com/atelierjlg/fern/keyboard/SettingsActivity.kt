package com.atelierjlg.fern.keyboard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.atelierjlg.fern.keyboard.logic.MemeLibrary
import com.atelierjlg.fern.keyboard.voice.Dictation
import com.atelierjlg.fern.keyboard.voice.ModelImport
import com.atelierjlg.fern.theme.FernSharedTheme
import com.atelierjlg.fern.ui.kit.FernField
import com.atelierjlg.fern.ui.kit.Pill
import com.atelierjlg.fern.ui.kit.SectionLabel
import com.atelierjlg.fern.ui.theme.Fern
import com.atelierjlg.fern.ui.theme.FernIcon
import com.atelierjlg.fern.ui.theme.FernIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    var learned by remember { mutableIntStateOf(0) }
    LaunchedEffect(check) { learned = withContext(Dispatchers.IO) { Lexicon.learnedCount(context) } }
    var confirmForget by remember { mutableStateOf(false) }

    // Dictée : micro et modèle Whisper.
    var importing by remember { mutableStateOf<String?>(null) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { check++ }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = "0 Mo"
            ModelImport.copy(context, uri, progress = { mb -> importing = "$mb Mo" }) { name ->
                importing = null
                if (name != null) {
                    Dictation.unloadModel()
                    update(settings.copy(whisperModel = name))
                }
                check++
            }
        }
    }

    // Memes : la liste suit les changements (import, mots-clés, suppression).
    var memeList by remember { mutableStateOf(Memes.library.all) }
    var memeMessage by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<MemeLibrary.Meme?>(null) }
    DisposableEffect(Unit) {
        Memes.ensureLoaded(context)
        val stop = Memes.observe { memeList = Memes.library.all }
        memeList = Memes.library.all
        onDispose { stop() }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) Memes.importUris(context, uris) { n -> memeMessage = "$n image${if (n > 1) "s" else ""} ajoutée${if (n > 1) "s" else ""}" }
    }
    editing?.let { meme ->
        var tags by remember(meme.id) { mutableStateOf(meme.tags.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Mots-clés") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Séparés par des virgules : chat, lundi, mdr…", style = Fern.type.nomApp, color = c.lichen)
                    FernField(value = tags, onValueChange = { tags = it }, placeholder = "chat, dodo")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Memes.setTags(context, meme.id, tags)
                    editing = null
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = {
                    Memes.delete(context, meme.id)
                    editing = null
                }) { Text("Supprimer") }
            },
        )
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update(settings.copy(contactNames = granted))
        Lexicon.reloadContacts(context, granted)
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Oublier les mots appris ?") },
            text = { Text("Le clavier repartira de zéro (le dictionnaire, lui, reste).") },
            confirmButton = {
                TextButton(onClick = {
                    Lexicon.forgetAll(context)
                    learned = 0
                    confirmForget = false
                }) { Text("Oublier") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Annuler") } },
        )
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

        SectionLabel("Correction")
        Card {
            Toggle("Correction automatique", "Corrige à l'espace, seulement quand c'est sûr", settings.autoCorrect) {
                update(settings.copy(autoCorrect = it))
            }
            Toggle("Suggestions", "Trois mots au-dessus des touches", settings.suggestions) { update(settings.copy(suggestions = it)) }
            Toggle("Apprendre mes mots", "⌫ juste après une correction = ton mot est retenu", settings.learnWords) {
                update(settings.copy(learnWords = it))
            }
            Toggle("Noms de mes contacts", "Pour ne jamais « corriger » un prénom", settings.contactNames) { on ->
                if (on && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    askContacts.launch(Manifest.permission.READ_CONTACTS)
                } else {
                    update(settings.copy(contactNames = on))
                    Lexicon.reloadContacts(context, on)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { confirmForget = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Oublier les mots appris", style = Fern.type.corps, color = c.creme)
                    Text("$learned mot${if (learned > 1) "s" else ""} retenu${if (learned > 1) "s" else ""}", style = Fern.type.nomApp, color = c.lichen)
                }
                FernIcon(FernIcons.Trash, c.lichen, size = 20.dp)
            }
        }

        SectionLabel("Presse-papiers et emoji")
        Card {
            Text("Textes copiés gardés", style = Fern.type.corps, color = c.creme)
            Text("Les copies de mots de passe ne sont jamais gardées", style = Fern.type.nomApp, color = c.lichen)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Aucun", 3 to "3", 5 to "5", 10 to "10").forEach { (n, label) ->
                    Pill(label, selected = settings.clipCount == n) { update(settings.copy(clipCount = n)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Teinte des emoji", style = Fern.type.corps, color = c.creme)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("👋", "👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿").forEachIndexed { i, hand ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (settings.skinTone == i) c.pistache else c.lierre)
                            .clickable { update(settings.copy(skinTone = i)) },
                        contentAlignment = Alignment.Center,
                    ) { Text(hand, style = Fern.type.corps) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Toggle("Emoji suggérés", "« chat » → 🐈 au bout de la barre", settings.emojiSuggestions) {
                update(settings.copy(emojiSuggestions = it))
            }
            Text(
                "Appui long sur un emoji pour l'ajouter à tes favoris (3 favoris dans la barre d'outils).",
                style = Fern.type.nomApp,
                color = c.lichen,
            )
        }

        SectionLabel("Dictée (Whisper, hors ligne)")
        Card {
            val micOk = remember(check) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
            StepRow(
                number = "1",
                title = "Autoriser le micro",
                detail = if (micOk) "C'est fait" else "Le son reste dans le téléphone",
                done = micOk,
                onClick = { askMic.launch(Manifest.permission.RECORD_AUDIO) },
            )
            val model = Dictation.modelFile(context)
            val hasModel = remember(check, importing) { model.exists() }
            StepRow(
                number = "2",
                title = if (hasModel) "Modèle : ${settings.whisperModel.ifEmpty { "importé" }}" else "Importer un modèle Whisper",
                detail = when {
                    importing != null -> "Copie… ${importing}"
                    hasModel -> "${model.length() / (1024 * 1024)} Mo · toucher pour en changer"
                    else -> "Un fichier .bin téléchargé une fois (voir ci-dessous)"
                },
                done = hasModel && importing == null,
                onClick = { if (importing == null) pickModel.launch(arrayOf("*/*")) },
            )
            Text(
                "Télécharge un modèle depuis huggingface.co/ggerganov/whisper.cpp (dossier « Files ») :\n" +
                    "· ggml-small-q5_1.bin (≈ 190 Mo) : rapide, bon français — conseillé pour commencer ;\n" +
                    "· ggml-medium-q5_0.bin (≈ 540 Mo) : meilleur, mais plus lent.\n" +
                    "Puis importe-le ici. Le clavier, lui, n'a jamais accès à Internet.",
                style = Fern.type.nomApp,
                color = c.lichen,
            )
            Toggle("Arrêt automatique", "La dictée s'arrête quand tu te tais", settings.autoStopDictation) {
                update(settings.copy(autoStopDictation = it))
            }
            if (hasModel && importing == null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        model.delete()
                        Dictation.unloadModel()
                        update(settings.copy(whisperModel = ""))
                        check++
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Supprimer le modèle", style = Fern.type.corps, color = c.creme, modifier = Modifier.weight(1f))
                    FernIcon(FernIcons.Trash, c.lichen, size = 20.dp)
                }
            }
        }

        SectionLabel("Memes et stickers")
        Card {
            Text(
                "Tes images, envoyées d'un toucher depuis la barre d'outils du clavier. Ajoute des mots-clés pour les retrouver.",
                style = Fern.type.nomApp,
                color = c.lichen,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Ajouter des images", selected = false) {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Pill("Stickers de Fern", selected = false) {
                    Memes.importFromFern(context) { n ->
                        memeMessage = when {
                            n < 0 -> "Fern Launcher n'a pas répondu (installé, même version de clé ?)"
                            n == 0 -> "Aucun nouveau sticker dans Fern"
                            else -> "$n sticker${if (n > 1) "s" else ""} ajouté${if (n > 1) "s" else ""}"
                        }
                    }
                }
            }
            memeMessage?.let { Text(it, style = Fern.type.nomApp, color = c.pistache) }
            memeList.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { meme -> MemeThumb(meme, Modifier.weight(1f)) { editing = meme } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

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
            "Tout ce qui était prévu est là. Dis-moi ce qui manque !",
            style = Fern.type.nomApp,
            color = c.lichen,
        )
        SectionLabel("Crédits")
        Text(
            "Dictionnaire : fréquences des sous-titres OpenSubtitles (FrequencyWords, CC BY-SA 4.0) et " +
                "liste de mots an-array-of-french-words (MIT). Police Bricolage Grotesque (OFL).",
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

/** Miniature d'un meme dans les réglages (toucher = mots-clés / supprimer). */
@Composable
private fun MemeThumb(meme: MemeLibrary.Meme, modifier: Modifier, onClick: () -> Unit) {
    val c = Fern.colors
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, meme.file) {
        value = withContext(Dispatchers.IO) { runCatching { Memes.thumbnail(context, meme) }.getOrNull() }
    }
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.lierre).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            meme.tags.joinToString(", ").ifEmpty { "+ mots-clés" },
            style = Fern.type.nomApp,
            color = if (meme.tags.isEmpty()) c.moussePale else c.lichen,
            maxLines = 1,
        )
    }
}
