package com.atelierjlg.fern.contact

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atelierjlg.fern.contact.data.CallEntry
import com.atelierjlg.fern.contact.data.ContactDetail
import com.atelierjlg.fern.contact.data.ContactForm
import com.atelierjlg.fern.contact.data.ContactSummary
import com.atelierjlg.fern.contact.data.ContactsRepo
import com.atelierjlg.fern.contact.data.BlockedRepo
import com.atelierjlg.fern.contact.data.Birthdays
import com.atelierjlg.fern.contact.data.ContactPrefs
import com.atelierjlg.fern.contact.data.RecentsRepo
import com.atelierjlg.fern.contact.data.VoicemailEntry
import com.atelierjlg.fern.contact.data.VoicemailPlayer
import com.atelierjlg.fern.contact.data.VoicemailRepo
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Les écrans de l'appli (comme des pages HTML, mais dans une seule activité). */
sealed interface Screen {
    data object Tabs : Screen
    data class Detail(val id: Long) : Screen
    /** id = null : nouveau contact, éventuellement avec un numéro déjà rempli. */
    data class Edit(val id: Long?, val number: String = "") : Screen
    data object Settings : Screen
}

enum class Tab(val label: String) { Favoris("Favoris"), Recents("Récents"), Contacts("Contacts"), Clavier("Clavier") }

/** Ce qui manque pour que l'appli marche à fond. */
data class SetupState(
    val isDefaultDialer: Boolean = true,
    val missingPermissions: List<String> = emptyList(),
    val canFullScreen: Boolean = true,
) {
    val complete get() = isDefaultDialer && missingPermissions.isEmpty() && canFullScreen
}

/**
 * Le « cerveau » de Fern Contact : l'état de l'appli et toutes les actions.
 * Les écrans observent ces StateFlow (comme des variables réactives) et appellent ces fonctions.
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val contactsRepo = ContactsRepo(application)
    private val recentsRepo = RecentsRepo(application)
    private val prefs = application.getSharedPreferences("fern-contact", Context.MODE_PRIVATE)

    private val _contacts = MutableStateFlow<List<ContactSummary>>(emptyList())
    val contacts: StateFlow<List<ContactSummary>> = _contacts.asStateFlow()

    private val _recents = MutableStateFlow<List<CallEntry>>(emptyList())
    val recents: StateFlow<List<CallEntry>> = _recents.asStateFlow()

    private val voicemailRepo = VoicemailRepo(application)
    private val blockedRepo = BlockedRepo(application)

    private val _voicemails = MutableStateFlow<List<VoicemailEntry>>(emptyList())
    val voicemails: StateFlow<List<VoicemailEntry>> = _voicemails.asStateFlow()

    /** Le message vocal en cours de lecture. */
    private val _playing = MutableStateFlow<Long?>(null)
    val playing: StateFlow<Long?> = _playing.asStateFlow()

    private val _blocked = MutableStateFlow<List<BlockedRepo.Blocked>>(emptyList())
    val blocked: StateFlow<List<BlockedRepo.Blocked>> = _blocked.asStateFlow()

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Tabs))
    val screen: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _tab = MutableStateFlow(runCatching { Tab.valueOf(prefs.getString("tab", null) ?: "") }.getOrDefault(Tab.Recents))
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    /** Numéro tapé dans le clavier (gardé quand on change d'onglet). */
    val dialed = MutableStateFlow("")

    private var reloadJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    private var observing = false

    /** Être prévenu quand les contacts ou le journal changent (possible seulement une fois les accès donnés). */
    private fun observe() {
        if (observing) return
        observing = runCatching {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        }.isSuccess
        if (!observing) runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    override fun onCleared() {
        if (observing) context.contentResolver.unregisterContentObserver(observer)
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    fun open(screen: Screen) {
        _stack.value = _stack.value + screen
    }

    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.value = _stack.value.dropLast(1)
        return true
    }

    fun selectTab(tab: Tab) {
        _tab.value = tab
        _stack.value = listOf(Screen.Tabs)
        prefs.edit().putString("tab", tab.name).apply()
        if (tab == Tab.Recents) markMissedSeen()
    }

    // ─── Chargement ──────────────────────────────────────────────────────────

    /** Relit contacts et journal (regroupé : plusieurs changements d'affilée = une seule lecture). */
    fun reload() {
        refreshSetup()
        observe()
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(150)
            if (has(Manifest.permission.READ_CONTACTS)) {
                _contacts.value = withContext(Dispatchers.IO) { runCatching { contactsRepo.all() }.getOrDefault(_contacts.value) }
            }
            if (has(Manifest.permission.READ_CALL_LOG)) {
                _recents.value = withContext(Dispatchers.IO) { runCatching { recentsRepo.load() }.getOrDefault(_recents.value) }
            }
            _voicemails.value = withContext(Dispatchers.IO) { voicemailRepo.all() }
        }
    }

    suspend fun detail(id: Long): ContactDetail? = withContext(Dispatchers.IO) { runCatching { contactsRepo.detail(id) }.getOrNull() }

    // ─── Réglages de départ ──────────────────────────────────────────────────

    val neededPermissions: List<String>
        get() = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }

    private fun has(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun refreshSetup() {
        val role = context.getSystemService(RoleManager::class.java)
        val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }
        _setup.value = SetupState(
            isDefaultDialer = role?.isRoleHeld(RoleManager.ROLE_DIALER) == true,
            missingPermissions = neededPermissions.filterNot(::has),
            canFullScreen = fullScreen,
        )
    }

    fun dialerRoleIntent(): Intent? =
        context.getSystemService(RoleManager::class.java)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_DIALER) }
            ?.createRequestRoleIntent(RoleManager.ROLE_DIALER)

    fun fullScreenSettingsIntent(): Intent? = if (Build.VERSION.SDK_INT >= 34) {
        Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
    } else {
        null
    }

    fun appSettingsIntent() =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    // ─── Appeler, écrire ─────────────────────────────────────────────────────

    /** Appelle tout de suite (ou ouvre le clavier du système si l'appel n'est pas autorisé). */
    fun call(number: String) {
        if (number.isBlank()) return
        val uri = Uri.fromParts("tel", number, null)
        if (has(Manifest.permission.CALL_PHONE)) {
            runCatching { context.getSystemService(TelecomManager::class.java).placeCall(uri, null) }
                .onFailure { toast("Appel impossible : ${it.message}") }
        } else {
            start(Intent(Intent.ACTION_DIAL, uri))
        }
    }

    /** Appelle la messagerie vocale (appui long sur 1). */
    fun callVoicemail() {
        if (has(Manifest.permission.CALL_PHONE)) {
            runCatching {
                context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("voicemail", "", null), null)
            }.onFailure { toast("Messagerie introuvable") }
        }
    }

    /** Écrire un SMS : Fern Messages s'il est installé, sinon l'appli SMS par défaut. */
    fun message(number: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
        val fern = Intent(intent).setPackage("com.atelierjlg.fern.messages")
        if (context.packageManager.resolveActivity(fern, 0) != null) start(fern) else start(intent)
    }

    fun email(address: String) = start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))

    private fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast("Aucune appli pour ça") }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // ─── Contacts ────────────────────────────────────────────────────────────

    fun setStarred(id: Long, starred: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { contactsRepo.setStarred(id, starred) }
        reload()
    }

    /** Enregistre et renvoie l'identifiant du contact (null en cas d'échec). */
    suspend fun save(original: ContactDetail?, form: ContactForm): Long? = withContext(Dispatchers.IO) {
        runCatching { contactsRepo.save(original, form) }
            .onFailure { withContext(Dispatchers.Main) { toast("Enregistrement impossible : ${it.message}") } }
            .getOrNull()
    }

    suspend fun setPhoto(detail: ContactDetail, uri: Uri?) = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = uri?.let { readPhoto(it) }
            contactsRepo.setPhoto(detail.rawContactId, bytes)
        }.onFailure { withContext(Dispatchers.Main) { toast("Photo impossible : ${it.message}") } }
    }

    /** Photo choisie → JPEG de 720 px au plus (Android la redimensionne de toute façon). */
    private fun readPhoto(uri: Uri): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = 720f / maxOf(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
            it.toByteArray()
        }
    }

    fun delete(detail: ContactDetail) = viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { contactsRepo.delete(detail) } }
        back()
        reload()
    }

    // ─── Journal d'appels ────────────────────────────────────────────────────

    fun deleteCalls(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { recentsRepo.delete(ids) }.onFailure { withContext(Dispatchers.Main) { toast("Suppression impossible") } }
        reload()
    }

    fun markMissedSeen() = viewModelScope.launch(Dispatchers.IO) { recentsRepo.markMissedSeen() }

    // ─── Numéros bloqués (liste d'Android, partagée avec les SMS) ───────────

    fun canBlock(): Boolean = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    suspend fun isBlocked(number: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)
    }

    suspend fun setBlocked(numbers: List<String>, blocked: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            numbers.forEach { n ->
                if (blocked) {
                    context.contentResolver.insert(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                        android.content.ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, n) },
                    )
                } else {
                    BlockedNumberContract.unblock(context, n)
                }
            }
        }.onFailure { withContext(Dispatchers.Main) { toast("Blocage impossible : Fern Contact doit être l'appli Téléphone") } }
    }

    // ─── Messagerie vocale ───────────────────────────────────────────────────

    fun playVoicemail(v: VoicemailEntry) {
        if (_playing.value == v.id) {
            VoicemailPlayer.stop()
            _playing.value = null
            return
        }
        val ok = VoicemailPlayer.play(context, v.id) { _playing.value = null }
        _playing.value = if (ok) v.id else null
        if (!ok) toast("Lecture impossible")
        if (!v.isRead) viewModelScope.launch(Dispatchers.IO) { voicemailRepo.markRead(v.id); reload() }
    }

    fun deleteVoicemail(v: VoicemailEntry) = viewModelScope.launch(Dispatchers.IO) {
        if (_playing.value == v.id) VoicemailPlayer.stop()
        voicemailRepo.delete(v.id)
        reload()
    }

    // ─── Réglages ────────────────────────────────────────────────────────────

    fun loadBlocked() = viewModelScope.launch(Dispatchers.IO) { _blocked.value = blockedRepo.all() }

    fun unblock(number: String) = viewModelScope.launch(Dispatchers.IO) {
        blockedRepo.unblock(number)
        _blocked.value = blockedRepo.all()
    }

    var blockTelemarketing: Boolean
        get() = ContactPrefs.blockTelemarketing(context)
        set(value) = ContactPrefs.setBlockTelemarketing(context, value)

    var birthdayReminders: Boolean
        get() = ContactPrefs.birthdayReminders(context)
        set(value) {
            ContactPrefs.setBirthdayReminders(context, value)
            Birthdays.schedule(context)
        }

    var quickReplies: List<String>
        get() = ContactPrefs.quickReplies(context)
        set(value) = ContactPrefs.setQuickReplies(context, value)

    fun scheduleBirthdays() = viewModelScope.launch(Dispatchers.IO) { runCatching { Birthdays.schedule(context) } }

    /** Sonnerie d'un contact (null = celle du téléphone). */
    fun setRingtone(id: Long, uri: Uri?) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            context.contentResolver.update(
                android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
                android.content.ContentValues().apply { put(ContactsContract.Contacts.CUSTOM_RINGTONE, uri?.toString()) }, null, null,
            )
        }.onFailure { withContext(Dispatchers.Main) { toast("Sonnerie non enregistrée") } }
        reload()
    }

    fun ringtoneTitle(uri: String?): String {
        if (uri == null) return "Celle du téléphone"
        return runCatching { android.media.RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull() ?: "Personnalisée"
    }

    /** Tous les contacts dans un seul fichier .vcf (pour les garder ou les passer à un autre téléphone). */
    fun exportContacts(target: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val keys = mutableListOf<String>()
            context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null)
                ?.use { c -> while (c.moveToNext()) c.getString(0)?.let(keys::add) }
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(keys.joinToString(":")))
            context.contentResolver.openInputStream(uri)?.use { input ->
                context.contentResolver.openOutputStream(target)?.use { input.copyTo(it) }
            }
            keys.size
        }.onSuccess { n -> withContext(Dispatchers.Main) { toast("$n contacts exportés") } }
            .onFailure { withContext(Dispatchers.Main) { toast("Export impossible : ${it.message}") } }
    }

    /** Importer un .vcf : Android (appli Contacts) s'en charge et demande où les ranger. */
    fun importContacts(file: Uri) =
        start(Intent(Intent.ACTION_VIEW).setDataAndType(file, "text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))

    fun versionName(): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
}
