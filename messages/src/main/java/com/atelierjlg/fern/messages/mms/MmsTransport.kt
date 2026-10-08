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
        val base = context.getSystemService(SmsManager::class.java)
        if (subId < 0) return base
        return if (Build.VERSION.SDK_INT >= 31) base.createForSubscriptionId(subId) else SmsManager.getSmsManagerForSubscriptionId(subId)
    }

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

    /** Mes propres numéros (pour ne pas m'ajouter moi-même aux conversations de groupe). */
    @Suppress("DEPRECATION")
    fun myNumbers(context: Context): List<String> {
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
        if (already) return
        val file = File(dir, "${System.currentTimeMillis()}_$subId.pdu")
        file.writeBytes(pdu)
        download(context, file)
    }

    /** Relance tous les MMS en attente (au démarrage de l'appli, bouton « Réessayer »). */
    fun retryPending(context: Context) {
        File(context.filesDir, PENDING_DIR).listFiles().orEmpty().filter { it.extension == "pdu" }.forEach { download(context, it) }
    }

    fun pendingCount(context: Context): Int =
        File(context.filesDir, PENDING_DIR).listFiles().orEmpty().count { it.extension == "pdu" }

    private fun download(context: Context, pending: File) {
        val notification = Pdu.parseNotification(pending.readBytes())
        if (notification == null) {
            pending.delete()
            return
        }
        val expired = notification.expiry?.let { it * 1000 < System.currentTimeMillis() } == true
        if (expired) {
            pending.delete()
            MessageNotifier.info(context, -1L, "MMS expiré", "Un MMS de ${notification.from ?: "quelqu'un"} n'est plus disponible chez l'opérateur.")
            return
        }
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
        }.onFailure { Log.e(TAG, "Téléchargement impossible", it) }
    }

    /** Le MMS est téléchargé : on le range, on prévient l'opérateur, on notifie. */
    internal fun onDownloaded(context: Context, ok: Boolean, file: File, pending: File, subId: Int) {
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
            val threadId = MmsStore.saveIncoming(context, message, subId, myNumbers(context))
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
    }

    /**
     * Envoie un MMS (photo, contact, ou message de groupe) à [to].
     * Le message apparaît tout de suite dans la conversation (« Envoi… »).
     */
    fun send(context: Context, to: List<String>, text: String, attachments: List<Attachment>, subId: Int = -1) {
        val parts = mutableListOf<MmsPart>()
        var imageName: String? = null
        var vcardName: String? = null
        val budget = maxSize(context, subId) - 8 * 1024 - text.length * 4
        val photos = attachments.filterIsInstance<Attachment.Photo>()
        photos.forEachIndexed { i, a ->
            val jpeg = compressPhoto(context, a.uri, budget / photos.size)
            val name = "image$i.jpg"
            if (i == 0) imageName = name
            parts += MmsPart("image/jpeg", jpeg, name = name, contentId = "image$i", contentLocation = name)
        }
        attachments.filterIsInstance<Attachment.Contact>().forEachIndexed { i, a ->
            val vcard = readVcard(context, a.uri) ?: return@forEachIndexed
            val name = "contact$i.vcf"
            if (i == 0) vcardName = name
            parts += MmsPart("text/x-vCard", vcard, name = name, contentId = "contact$i", contentLocation = name)
        }
        if (text.isNotBlank()) {
            parts += MmsPart("text/plain", text.toByteArray(), name = "text0.txt", contentId = "text0", contentLocation = "text0.txt", charset = 106)
        }
        val smil = Pdu.smil(imageName, if (text.isNotBlank()) "text0.txt" else null, vcardName)
        parts.add(0, MmsPart("application/smil", smil.toByteArray(), name = "smil.xml", contentId = "smil", contentLocation = "smil.xml"))
        sendParts(context, to, parts, subId)
    }

    /** Renvoie un MMS en échec : mêmes pièces, nouvel envoi, l'ancien est retiré. */
    fun resend(context: Context, mmsId: Long, to: List<String>) {
        val parts = MmsStore.readParts(context, mmsId).filterNot { it.isSmil }
        if (parts.isEmpty() || to.isEmpty()) return
        val smil = Pdu.smil(
            parts.firstOrNull { it.isImage }?.contentLocation,
            parts.firstOrNull { it.isText }?.contentLocation,
            parts.firstOrNull { it.isVcard }?.contentLocation,
        )
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
