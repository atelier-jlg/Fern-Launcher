package com.atelierjlg.fern.messages

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.messages.data.Conversation
import com.atelierjlg.fern.messages.data.MessagesPrefs
import com.atelierjlg.fern.messages.data.MessagesRepo
import com.atelierjlg.fern.messages.data.Msg
import com.atelierjlg.fern.messages.data.NotifyMode
import com.atelierjlg.fern.messages.data.PrefsStore
import com.atelierjlg.fern.messages.mms.MmsTransport
import com.atelierjlg.fern.messages.mms.MmsTransport.Attachment
import com.atelierjlg.fern.messages.sms.MessageNotifier
import com.atelierjlg.fern.messages.sms.ScheduledSend
import com.atelierjlg.fern.messages.sms.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Inbox : Screen
    data object Archived : Screen
    data object Search : Screen
    data object Scheduled : Screen
    data class Thread(val threadId: Long) : Screen
    /** Nouveau message, éventuellement pré-rempli (lien « smsto: », partage depuis une autre appli). */
    data class Compose(val number: String = "", val body: String = "", val image: String? = null, val vcard: String? = null) : Screen
}

data class SetupState(
    val isDefaultSms: Boolean = true,
    val missingPermissions: List<String> = emptyList(),
) {
    val complete get() = isDefaultSms && missingPermissions.isEmpty()
}

/** Un contact proposé quand on écrit un nouveau message. */
data class Recipient(val name: String, val number: String, val photoUri: String?)

/** Le « cerveau » de Fern Messages : état de l'appli et actions. */
class MessagesViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val repo = MessagesRepo(application)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _prefs = MutableStateFlow(PrefsStore.read(application))
    val prefs: StateFlow<MessagesPrefs> = _prefs.asStateFlow()

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Inbox))
    val screen: StateFlow<List<Screen>> = _stack.asStateFlow()

    /** MMS annoncés mais pas encore téléchargés. */
    private val _pendingMms = MutableStateFlow(0)
    val pendingMms: StateFlow<Int> = _pendingMms.asStateFlow()
    private var lastMmsRetry = 0L

    /** Incrémenté à chaque changement dans la base des SMS : les écrans ouverts se relisent. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private var reloadJob: Job? = null
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    override fun onCleared() {
        if (observing) context.contentResolver.unregisterContentObserver(observer)
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    fun open(screen: Screen) {
        _stack.value = _stack.value + screen
    }

    /** Remplace l'écran du dessus (ex. : après l'envoi d'un nouveau message → la conversation). */
    fun replaceTop(screen: Screen) {
        _stack.value = _stack.value.dropLast(1) + screen
    }

    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.value = _stack.value.dropLast(1)
        return true
    }

    fun openThreadFromOutside(threadId: Long) {
        _stack.value = listOf(Screen.Inbox, Screen.Thread(threadId))
    }

    // ─── Chargement ──────────────────────────────────────────────────────────

    fun reload() {
        refreshSetup()
        if (!observing && has(Manifest.permission.READ_SMS)) {
            observing = runCatching {
                // Toute la base SMS + MMS : un message reçu, envoyé ou supprimé, et la liste suit.
                context.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
            }.isSuccess
        }
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(120)
            _prefs.value = withContext(Dispatchers.IO) { PrefsStore.read(context) }
            _pendingMms.value = withContext(Dispatchers.IO) { MmsTransport.pendingCount(context) }
            if (has(Manifest.permission.READ_SMS)) {
                _conversations.value = withContext(Dispatchers.IO) { runCatching { repo.conversations() }.getOrDefault(_conversations.value) }
            }
            _version.value++
        }
    }

    /** Les contacts ont pu changer (nom ajouté…) : on oublie ceux gardés en mémoire. */
    fun forgetContacts() = repo.forgetContacts()

    suspend fun messages(threadId: Long): List<Msg> =
        withContext(Dispatchers.IO) { runCatching { repo.messages(threadId) }.getOrDefault(emptyList()) }

    suspend fun addresses(threadId: Long): List<String> =
        withContext(Dispatchers.IO) { runCatching { repo.addressesOf(threadId) }.getOrDefault(emptyList()) }

    suspend fun display(addresses: List<String>): Pair<String, String?> =
        withContext(Dispatchers.IO) { repo.displayFor(addresses) }

    suspend fun contactName(number: String): String? = withContext(Dispatchers.IO) { repo.contact(number)?.first }

    suspend fun search(query: String): List<Msg> =
        withContext(Dispatchers.IO) { runCatching { repo.search(query) }.getOrDefault(emptyList()) }

    // ─── Réglages de départ ──────────────────────────────────────────────────

    val neededPermissions: List<String>
        get() = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.RECEIVE_MMS)
            add(Manifest.permission.READ_CONTACTS)
            // Double SIM, et reconnaître mon numéro dans les MMS de groupe.
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }

    private fun has(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun refreshSetup() {
        val role = context.getSystemService(RoleManager::class.java)
        _setup.value = SetupState(
            isDefaultSms = role?.isRoleHeld(RoleManager.ROLE_SMS) == true,
            missingPermissions = neededPermissions.filterNot(::has),
        )
    }

    fun smsRoleIntent(): Intent? =
        context.getSystemService(RoleManager::class.java)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_SMS) }
            ?.createRequestRoleIntent(RoleManager.ROLE_SMS)

    fun appSettingsIntent() =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun exactAlarmIntent(): Intent? = if (Build.VERSION.SDK_INT >= 31) {
        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        null
    }

    fun canScheduleExact() = ScheduledSend.canExact(context)

    // ─── Actions ─────────────────────────────────────────────────────────────

    /**
     * Envoie. Une photo, un contact, ou plusieurs destinataires (conversation de groupe) → MMS ;
     * sinon → SMS.
     */
    fun send(addresses: List<String>, body: String, attachments: List<Attachment> = emptyList()) {
        if ((body.isBlank() && attachments.isEmpty()) || addresses.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (attachments.isNotEmpty()) MmsTransport.send(context, addresses, body.trim(), attachments)
                else com.atelierjlg.fern.messages.sms.Outgoing.send(context, addresses, body)
            }.onFailure {
                val text = if (it is MmsTransport.TooBigException) "Trop lourd pour un MMS : ${it.limitKb} Ko maximum chez ton opérateur"
                else "Envoi impossible : ${it.message}"
                withContext(Dispatchers.Main) { toast(text) }
            }
        }
    }

    fun resend(msg: Msg) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            if (msg.isMms) MmsTransport.resend(context, msg.id, repo.addressesOf(msg.threadId))
            else SmsSender.resend(context, msg.id, msg.address, msg.body)
        }.onFailure { withContext(Dispatchers.Main) { toast("Renvoi impossible : ${it.message}") } }
    }

    /** Relance le téléchargement des MMS en attente (au plus une fois toutes les 5 min, sauf demande). */
    fun retryMms(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastMmsRetry < 5 * 60_000L) return
        lastMmsRetry = now
        viewModelScope.launch(Dispatchers.IO) {
            if (MmsTransport.pendingCount(context) > 0) runCatching { MmsTransport.retryPending(context) }
        }
        if (force) toast("Téléchargement relancé")
    }

    /** Ouvre une carte de contact reçue (l'appli Contacts propose de l'enregistrer). */
    fun openVcard(part: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "vcard").apply { mkdirs() }
            val file = java.io.File(dir, "contact.vcf")
            context.contentResolver.openInputStream(part)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            withContext(Dispatchers.Main) { start(intent) }
        }
    }

    fun schedule(addresses: List<String>, body: String, at: Long, attachments: List<Attachment> = emptyList()) =
        viewModelScope.launch(Dispatchers.IO) {
            ScheduledSend.schedule(context, addresses, body.trim(), at, attachments)
            withContext(Dispatchers.Main) { toast("Message programmé") }
            reload()
        }

    /** Enregistre une photo d'un MMS dans la galerie (Images → Fern Messages). */
    fun savePhoto(part: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(part)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val ext = when (mime) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "fern-${System.currentTimeMillis()}.$ext")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Fern Messages")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val target = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("galerie")
            resolver.openInputStream(part)?.use { input -> resolver.openOutputStream(target)?.use { input.copyTo(it) } }
            resolver.update(target, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }.onSuccess { withContext(Dispatchers.Main) { toast("Photo enregistrée dans la galerie") } }
            .onFailure { withContext(Dispatchers.Main) { toast("Enregistrement impossible") } }
    }

    /** Partage une photo d'un MMS (vers Signal, la galerie, un mail…). */
    fun sharePhoto(part: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "pieces").apply { mkdirs() }
            val file = java.io.File(dir, "photo-${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(part)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)
            val send = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            withContext(Dispatchers.Main) { start(Intent.createChooser(send, "Partager la photo").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        }
    }

    /** Supprime une photo : seulement la photo si le message contient autre chose, sinon tout le message. */
    fun deletePhoto(msg: Msg, part: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val alone = msg.body.isBlank() && msg.images.size + msg.vcards.size + msg.files.size <= 1
            if (alone) repo.delete(msg) else context.contentResolver.delete(part, null, null)
        }.onFailure { withContext(Dispatchers.Main) { toast("Suppression impossible") } }
        reload()
    }

    /** Ouvre une pièce jointe reçue (vidéo, son, document) dans l'appli qui convient. */
    fun openPart(file: com.atelierjlg.fern.messages.data.MmsFile) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "pieces").apply { mkdirs() }
            val target = java.io.File(dir, file.name.replace(Regex("[^\\p{L}0-9._ -]"), "_").ifBlank { "piece" })
            context.contentResolver.openInputStream(file.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", target)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            withContext(Dispatchers.Main) { start(intent) }
        }.onFailure { withContext(Dispatchers.Main) { toast("Impossible d'ouvrir la pièce jointe") } }
    }

    fun cancelScheduled(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        ScheduledSend.cancel(context, id)
        reload()
    }

    /** Ouverture d'une conversation : messages lus, notification retirée. */
    fun markRead(threadId: Long) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { repo.markRead(threadId) }
        MessageNotifier.cancel(context, threadId)
    }

    fun delete(msgs: List<Msg>) = viewModelScope.launch(Dispatchers.IO) {
        msgs.forEach { runCatching { repo.delete(it) } }
    }

    fun deleteThreads(ids: Set<Long>) = viewModelScope.launch(Dispatchers.IO) {
        ids.forEach { runCatching { repo.deleteThread(it) } }
        updatePrefs { p -> p.copy(archived = p.archived - ids, drafts = p.drafts - ids) }
    }

    fun markThreadsRead(ids: Set<Long>) = viewModelScope.launch(Dispatchers.IO) {
        ids.forEach { runCatching { repo.markRead(it) }; MessageNotifier.cancel(context, it) }
    }

    fun setArchived(ids: Set<Long>, archived: Boolean) = updatePrefs { p ->
        p.copy(archived = if (archived) p.archived + ids else p.archived - ids)
    }

    fun setNotifyMode(threadId: Long, mode: NotifyMode) = updatePrefs { p ->
        p.copy(notify = if (mode == NotifyMode.Son) p.notify - threadId else p.notify + (threadId to mode))
    }

    fun saveDraft(threadId: Long, text: String) = updatePrefs { p ->
        p.copy(drafts = if (text.isBlank()) p.drafts - threadId else p.drafts + (threadId to text))
    }

    private fun updatePrefs(transform: (MessagesPrefs) -> MessagesPrefs) {
        _prefs.value = PrefsStore.update(context, transform)
    }

    suspend fun threadIdFor(addresses: List<String>): Long =
        withContext(Dispatchers.IO) { repo.threadIdFor(addresses) }

    // ─── Liste noire (celle d'Android, partagée avec Fern Contact) ──────────

    suspend fun block(numbers: List<String>) = withContext(Dispatchers.IO) {
        runCatching {
            numbers.forEach { n ->
                context.contentResolver.insert(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    android.content.ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, n) },
                )
            }
        }.onSuccess { withContext(Dispatchers.Main) { toast("Bloqué") } }
            .onFailure { withContext(Dispatchers.Main) { toast("Blocage impossible : Fern Messages doit être l'appli SMS") } }
    }

    // ─── Contacts (nouveau message) ──────────────────────────────────────────

    suspend fun recipients(): List<Recipient> = withContext(Dispatchers.IO) {
        if (!has(Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val out = mutableListOf<Recipient>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(1) ?: continue
                    val name = c.getString(0) ?: number
                    if (out.none { it.name == name && PhoneNumbers.same(it.number, number) }) out += Recipient(name, number, c.getString(2))
                }
            }
        }
        out.sortedBy { com.atelierjlg.fern.common.SearchText.fold(it.name) }
    }

    // ─── Liens vers les autres applis ────────────────────────────────────────

    /** Appeler : par l'appli Téléphone par défaut (Fern Contact, si c'est elle). */
    fun call(number: String) = start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    /** La fiche du contact (ou « créer un contact » s'il est inconnu). */
    fun openContact(number: String) {
        val intent = Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT, Uri.fromParts("tel", number, null))
        val fern = Intent(intent).setPackage("com.atelierjlg.fern.contact")
        start(if (context.packageManager.resolveActivity(fern, 0) != null) fern else intent)
    }

    fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { toast("Aucune appli pour ça") }
    }

    fun copy(text: String) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Message", text))
        if (Build.VERSION.SDK_INT < 33) toast("Copié")
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
