package com.atelierjlg.fern.messages.mms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.FileProvider
import com.atelierjlg.fern.messages.sms.MessageNotifier
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread

/**
 * Transport des MMS : téléchargement des MMS annoncés, envoi des MMS (photo, contact, message de groupe).
 * Android s'occupe du réseau (données mobiles, serveur de l'opérateur) ; nous lui passons des fichiers
 * via un petit « fournisseur de fichiers » réservé à ce service.
 */
object MmsTransport {
    private const val TAG = "FernMms"
    private const val PENDING_DIR = "mms-en-attente"

    // ─── Outils ──────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    fun smsManager(context: Context, subId: Int): SmsManager {
        if (Build.VERSION.SDK_INT < 31) {
            return if (subId < 0) SmsManager.getDefault() else SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        val base = context.getSystemService(SmsManager::class.java)
        return if (subId < 0) base else base.createForSubscriptionId(subId)
    }

    /** Téléchargements en cours (nom du fichier d'annonce → début), pour ne jamais lancer le même deux fois. */
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Les fichiers temporaires de plus d'un jour (accusés envoyés, téléchargements interrompus) sont supprimés. */
    private fun cleanCache(context: Context) {
        val limit = System.currentTimeMillis() - 24 * 3600_000L
        File(context.cacheDir, "mms").listFiles().orEmpty().filter { it.lastModified() < limit }.forEach { it.delete() }
    }

    /** Ce MMS (même adresse sur le serveur) est-il déjà rangé dans le téléphone ? */
    private fun alreadyStored(context: Context, location: String): Boolean = runCatching {
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID), "${Telephony.Mms.CONTENT_LOCATION} = ?", arrayOf(location), null,
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    private fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)

    private fun workFile(context: Context, prefix: String): File {
        val dir = File(context.cacheDir, "mms").apply { mkdirs() }
        return File(dir, "$prefix-${System.nanoTime()}.pdu")
    }

    /** Taille maximale d'un MMS chez l'opérateur (300 Ko si on ne sait pas). */
    fun maxSize(context: Context, subId: Int): Int = runCatching {
        smsManager(context, subId).carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 300 * 1024)
    }.getOrDefault(300 * 1024).coerceIn(100 * 1024, 2 * 1024 * 1024)

    /**
     * Mes propres numéros (pour ne pas m'ajouter moi-même aux conversations de groupe) :
     * ceux de ma fiche « Moi » (Fern Contact → Contacts → Moi) et ceux que la SIM connaît.
     */
    fun myNumbers(context: Context): List<String> = (profileNumbers(context) + simNumbers(context)).distinct()

    private fun profileNumbers(context: Context): List<String> = runCatching {
        val out = mutableListOf<String>()
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI, "data"),
            arrayOf(ContactsContract.Data.DATA1),
            "${ContactsContract.Data.MIMETYPE} = ?", arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE), null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(out::add) }
        out
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun simNumbers(context: Context): List<String> {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        return runCatching {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            sm.activeSubscriptionInfoList.orEmpty().mapNotNull { info ->
                if (Build.VERSION.SDK_INT >= 33) sm.getPhoneNumber(info.subscriptionId).ifBlank { null } else info.number?.ifBlank { null }
            }
        }.getOrDefault(emptyList())
    }

    // ─── Réception ───────────────────────────────────────────────────────────

    /** Une annonce de MMS arrive : on la garde (pour pouvoir réessayer) puis on télécharge. */
    fun onNotification(context: Context, pdu: ByteArray, subId: Int) {
        val dir = File(context.filesDir, PENDING_DIR).apply { mkdirs() }
        val notification = Pdu.parseNotification(pdu) ?: return
        // La même annonce peut arriver deux fois : une seule suffit.
        val already = dir.listFiles().orEmpty().any { f ->
            runCatching { Pdu.parseNotification(f.readBytes())?.contentLocation == notification.contentLocation }.getOrDefault(false)
        }
        if (already || alreadyStored(context, notification.contentLocation)) return
        val file = File(dir, "${System.currentTimeMillis()}_$subId.pdu")
        file.writeBytes(pdu)
        download(context, file)
    }

    /** Relance tous les MMS en attente (au démarrage de l'appli, bouton « Réessayer »). */
    fun retryPending(context: Context) {
        cleanCache(context)
        File(context.filesDir, PENDING_DIR).listFiles().orEmpty().filter { it.extension == "pdu" }.forEach { download(context, it) }
    }

    fun pendingCount(context: Context): Int =
        File(context.filesDir, PENDING_DIR).listFiles().orEmpty().count { it.extension == "pdu" }

    private fun download(context: Context, pending: File) {
        // Déjà en cours (moins de 10 min) : on ne relance pas.
        val started = inFlight[pending.name]
        if (started != null && System.currentTimeMillis() - started < 10 * 60_000L) return
        val notification = Pdu.parseNotification(pending.readBytes())
        if (notification == null || alreadyStored(context, notification.contentLocation)) {
            pending.delete()
            return
        }
        // Date limite : absolue, ou relative à l'arrivée de l'annonce (heure inscrite dans le nom du fichier).
        // Au-delà de 7 jours, on abandonne de toute façon (l'opérateur l'a effacé).
        val arrivedAt = pending.nameWithoutExtension.substringBefore('_').toLongOrNull() ?: pending.lastModified()
        val deadline = minOf(
            notification.expiry?.let { it * 1000 } ?: notification.expiryDelta?.let { arrivedAt + it * 1000 } ?: Long.MAX_VALUE,
            arrivedAt + 7 * 24 * 3600_000L,
        )
        if (deadline < System.currentTimeMillis()) {
            pending.delete()
            MessageNotifier.info(context, -1L, "MMS expiré", "Un MMS de ${notification.from ?: "quelqu'un"} n'est plus disponible chez l'opérateur.")
            return
        }
        inFlight[pending.name] = System.currentTimeMillis()
        val subId = pending.nameWithoutExtension.substringAfter('_', "-1").toIntOrNull() ?: -1
        val target = workFile(context, "recu")
        target.createNewFile()
        val intent = Intent(context, MmsDownloadedReceiver::class.java)
            .putExtra(EXTRA_FILE, target.path)
            .putExtra(EXTRA_PENDING, pending.path)
            .putExtra(EXTRA_SUB, subId)
        // Modifiable : Android y ajoute le résultat du téléchargement.
        val pi = PendingIntent.getBroadcast(
            context, pending.name.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        runCatching {
            smsManager(context, subId).downloadMultimediaMessage(context, notification.contentLocation, fileUri(context, target), null, pi)
        }.onFailure {
            Log.e(TAG, "Téléchargement impossible", it)
            inFlight.remove(pending.name)
        }
    }

    /** Le MMS est téléchargé : on le range, on prévient l'opérateur, on notifie. */
    @Synchronized
    internal fun onDownloaded(context: Context, ok: Boolean, file: File, pending: File, subId: Int) {
        inFlight.remove(pending.name)
        // Annonce déjà traitée (téléchargement en double) : rien à faire.
        if (!pending.exists()) {
            file.delete()
            return
        }
        try {
            val bytes = if (ok && file.exists()) file.readBytes() else null
            val message = bytes?.let(Pdu::parseMessage)
            if (message == null || message.type != Pdu.RETRIEVE_CONF) {
                Log.w(TAG, "Téléchargement échoué (ok=$ok)")
                MessageNotifier.info(
                    context, -1L, "MMS pas encore téléchargé",
                    "Vérifie que les données mobiles sont activées. Fern Messages réessaiera à la prochaine ouverture.",
                )
                return
            }
            val location = Pdu.parseNotification(pending.readBytes())?.contentLocation
            val threadId = MmsStore.saveIncoming(context, message, subId, myNumbers(context), location)
            pending.delete()
            acknowledge(context, message, pending, subId)
            MessageNotifier.notifyThread(context, threadId)
        } catch (e: Exception) {
            Log.e(TAG, "MMS reçu non enregistré", e)
        } finally {
            file.delete()
        }
    }

    /** « Bien reçu » pour l'opérateur (sinon il peut renvoyer l'annonce). */
    private fun acknowledge(context: Context, message: MmsMessage, pending: File, subId: Int) {
        runCatching {
            val pdu = message.transactionId?.let(Pdu::acknowledge)
                ?: Pdu.parseNotification(pending.readBytes())?.transactionId?.let(Pdu::notifyResponse)
                ?: return
            val f = workFile(context, "accuse")
            f.writeBytes(pdu)
            smsManager(context, subId).sendMultimediaMessage(context, fileUri(context, f), null, null, null)
        }
    }

    // ─── Envoi ───────────────────────────────────────────────────────────────

    /** Une pièce jointe choisie dans l'appli. */
    sealed interface Attachment {
        data class Photo(val uri: Uri) : Attachment
        data class Contact(val uri: Uri) : Attachment
        /** Un fichier .vcf reçu d'une autre appli (ex. « Envoyer ma carte » depuis Fern Contact). */
        data class VcardFile(val uri: Uri) : Attachment
        /** Ma propre carte (fiche « Moi » d'Android). */
        data object MyCard : Attachment
        /** Tout autre fichier : vidéo, message vocal, son, PDF… envoyé tel quel. */
        data class File(val uri: Uri, val mime: String, val name: String) : Attachment
    }

    /** Pièce jointe trop lourde pour un MMS chez cet opérateur. */
    class TooBigException(val limitKb: Int) : Exception("Trop lourd pour un MMS ($limitKb Ko maximum)")

    /**
     * Envoie un MMS (photo, contact, ou message de groupe) à [to].
     * Le message apparaît tout de suite dans la conversation (« Envoi… »).
     */
    fun send(context: Context, to: List<String>, text: String, attachments: List<Attachment>, subId: Int = -1) {
        val limit = maxSize(context, subId)
        val parts = mutableListOf<MmsPart>()
        // D'abord les pièces qu'on ne peut pas réduire (cartes, fichiers) : elles doivent tenir telles quelles.
        attachments.forEachIndexed { i, a ->
            when (a) {
                is Attachment.Photo -> Unit
                is Attachment.Contact, is Attachment.VcardFile, Attachment.MyCard -> {
                    val vcard = when (a) {
                        is Attachment.Contact -> readVcard(context, a.uri)
                        is Attachment.VcardFile -> readBytes(context, a.uri)
                        else -> myVcard(context)
                    } ?: return@forEachIndexed
                    val name = "contact$i.vcf"
                    parts += MmsPart("text/x-vCard", vcard, name = name, contentId = "contact$i", contentLocation = name)
                }
                is Attachment.File -> {
                    val bytes = readBytes(context, a.uri) ?: return@forEachIndexed
                    val name = safeName(a.name, i)
                    parts += MmsPart(a.mime, bytes, name = name, contentId = "file$i", contentLocation = name)
                }
            }
        }
        if (text.isNotBlank()) {
            parts += MmsPart("text/plain", text.toByteArray(), name = "text0.txt", contentId = "text0", contentLocation = "text0.txt", charset = 106)
        }
        // Puis les photos, réduites pour remplir ce qui reste.
        val left = limit - 8 * 1024 - parts.sumOf { it.data.size }
        val photos = attachments.filterIsInstance<Attachment.Photo>()
        if (left < 0 || (photos.isNotEmpty() && left / photos.size < 20 * 1024)) throw TooBigException(limit / 1024)
        photos.forEachIndexed { i, a ->
            val jpeg = compressPhoto(context, a.uri, left / photos.size)
            val name = "image$i.jpg"
            parts.add(i, MmsPart("image/jpeg", jpeg, name = name, contentId = "image$i", contentLocation = name))
        }
        parts.add(0, MmsPart("application/smil", Pdu.smilFor(parts).toByteArray(), name = "smil.xml", contentId = "smil", contentLocation = "smil.xml"))
        sendParts(context, to, parts, subId)
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

    /** Nom de fichier sans caractères gênants (« Cours n°3.pdf » → « Cours_n_3.pdf »). */
    private fun safeName(name: String, i: Int): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "fichier$i" }

    /** Renvoie un MMS en échec : mêmes pièces, nouvel envoi, l'ancien est retiré. */
    fun resend(context: Context, mmsId: Long, to: List<String>) {
        val parts = MmsStore.readParts(context, mmsId).filterNot { it.isSmil }
        if (parts.isEmpty() || to.isEmpty()) return
        val smil = Pdu.smilFor(parts)
        context.contentResolver.delete(Uri.parse("content://mms/$mmsId"), null, null)
        sendParts(context, to, listOf(MmsPart("application/smil", smil.toByteArray(), name = "smil.xml", contentId = "smil", contentLocation = "smil.xml")) + parts, -1)
    }

    /** Range le MMS (« Envoi… ») puis le confie à Android. */
    private fun sendParts(context: Context, to: List<String>, parts: List<MmsPart>, subId: Int) {
        val threadId = if (to.size == 1) Telephony.Threads.getOrCreateThreadId(context, to.first())
        else Telephony.Threads.getOrCreateThreadId(context, to.toSet())
        val stored = MmsStore.saveOutgoing(context, threadId, to, parts, subId)
        val pdu = Pdu.sendRequest("F${System.currentTimeMillis()}", to, parts)
        val file = workFile(context, "envoi")
        file.writeBytes(pdu)
        val pi = PendingIntent.getBroadcast(
            context, stored.hashCode(),
            Intent(context, MmsSentReceiver::class.java).putExtra(EXTRA_FILE, file.path).putExtra(EXTRA_URI, stored.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        runCatching {
            smsManager(context, subId).sendMultimediaMessage(context, fileUri(context, file), null, null, pi)
        }.onFailure {
            Log.e(TAG, "Envoi impossible", it)
            MmsStore.setBox(context, stored, 5)
        }
    }

    /** Photo → JPEG qui tient dans la taille permise par l'opérateur (on réduit jusqu'à ce que ça passe). */
    fun compressPhoto(context: Context, uri: Uri, maxBytes: Int): ByteArray {
        var side = 1600
        while (true) {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = side.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            for (quality in listOf(85, 70, 55, 40)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes || side <= 320 && quality == 40) return out.toByteArray()
            }
            side = (side * 0.7f).toInt()
        }
    }

    /** Ma carte de visite (fiche « Moi »). */
    fun myVcard(context: Context): ByteArray? = runCatching {
        context.contentResolver.openInputStream(ContactsContract.Profile.CONTENT_VCARD_URI)?.use { it.readBytes() }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** La carte de visite (vCard) d'un contact choisi. */
    private fun readVcard(context: Context, contactUri: Uri): ByteArray? = runCatching {
        val key = context.contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return null
        val vcardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, key)
        context.contentResolver.openInputStream(vcardUri)?.use { it.readBytes() }
    }.getOrNull()

    internal const val EXTRA_FILE = "fichier"
    internal const val EXTRA_PENDING = "attente"
    internal const val EXTRA_SUB = "sim"
    internal const val EXTRA_URI = "mms"
}

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = resultCode == Activity.RESULT_OK
        val file = File(intent.getStringExtra(MmsTransport.EXTRA_FILE) ?: return)
        val pending = File(intent.getStringExtra(MmsTransport.EXTRA_PENDING) ?: return)
        val sub = intent.getIntExtra(MmsTransport.EXTRA_SUB, -1)
        val result = goAsync()
        thread {
            try {
                MmsTransport.onDownloaded(context, ok, file, pending, sub)
            } finally {
                result.finish()
            }
        }
    }
}

class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = resultCode == Activity.RESULT_OK
        val uri = Uri.parse(intent.getStringExtra(MmsTransport.EXTRA_URI) ?: return)
        val file = intent.getStringExtra(MmsTransport.EXTRA_FILE)?.let(::File)
        val response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
        val result = goAsync()
        thread {
            try {
                val conf = response?.let(Pdu::parseMessage)
                val accepted = ok && (conf == null || conf.responseStatus == null || conf.responseStatus == Pdu.RESPONSE_OK)
                MmsStore.setBox(context, uri, if (accepted) Telephony.Mms.MESSAGE_BOX_SENT else 5, conf?.messageId)
            } catch (e: Exception) {
                Log.e("FernMms", "Statut d'envoi non enregistré", e)
            } finally {
                file?.delete()
                result.finish()
            }
        }
    }
}
