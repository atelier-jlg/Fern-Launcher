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
import com.atelierjlg.fern.contact.data.RecentsRepo
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
}
