package com.atelierjlg.fern.messages.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.atelierjlg.fern.common.PhoneNumbers

/**
 * Range les MMS dans la base d'Android (content://mms), comme le faisait l'appli d'origine :
 * une ligne pour le message, une par adresse (de, à, copie), une par pièce (texte, photo…).
 */
object MmsStore {
    private const val ADDR_FROM = 137
    private const val ADDR_TO = 151
    private const val ADDR_CC = 130
    private const val CHARSET_UTF8 = 106

    /** Enregistre un MMS reçu. [myNumbers] : mes numéros, retirés des participants du groupe. Renvoie la conversation. */
    fun saveIncoming(context: Context, m: MmsMessage, subId: Int, myNumbers: List<String>, contentLocation: String? = null): Long {
        val from = m.from?.takeIf { it.isNotBlank() } ?: "Inconnu"
        val others = (m.to + m.cc).filterNot { addr -> myNumbers.any { PhoneNumbers.same(it, addr) } }
        // Groupe : l'expéditeur + les autres destinataires (sans moi). Sinon : l'expéditeur seul.
        val participants = (listOf(from) + if (m.to.size + m.cc.size > 1) others else emptyList())
            .distinctBy { PhoneNumbers.key(it) }
        val threadId = Telephony.Threads.getOrCreateThreadId(context, participants.toSet())
        val now = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, now)
            put(Telephony.Mms.DATE_SENT, m.date ?: now)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_TYPE, Pdu.RETRIEVE_CONF)
            put(Telephony.Mms.MMS_VERSION, Pdu.VERSION_1_2 and 0x7F)
            put(Telephony.Mms.CONTENT_TYPE, m.contentType ?: Pdu.MULTIPART_RELATED)
            m.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            m.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
            // L'adresse sur le serveur de l'opérateur : évite de ranger deux fois le même MMS.
            contentLocation?.let { put(Telephony.Mms.CONTENT_LOCATION, it) }
            m.subject?.let {
                put(Telephony.Mms.SUBJECT, it)
                put(Telephony.Mms.SUBJECT_CHARSET, CHARSET_UTF8)
            }
            put(Telephony.Mms.TEXT_ONLY, if (m.parts.all { it.isText || it.isSmil }) 1 else 0)
            if (subId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subId)
        }
        val uri = context.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: error("MMS non enregistré")
        val id = ContentUris.parseId(uri)
        addAddress(context, id, from, ADDR_FROM)
        m.to.forEach { addAddress(context, id, it, ADDR_TO) }
        m.cc.forEach { addAddress(context, id, it, ADDR_CC) }
        m.parts.forEach { addPart(context, id, it) }
        return threadId
    }

    /** Enregistre un MMS en partance (boîte d'envoi). Renvoie son adresse dans la base. */
    fun saveOutgoing(context: Context, threadId: Long, to: List<String>, parts: List<MmsPart>, subId: Int): Uri {
        val now = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, now)
            put(Telephony.Mms.DATE_SENT, now)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.MESSAGE_TYPE, Pdu.SEND_REQ)
            put(Telephony.Mms.MMS_VERSION, Pdu.VERSION_1_2 and 0x7F)
            put(Telephony.Mms.CONTENT_TYPE, Pdu.MULTIPART_RELATED)
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            put(Telephony.Mms.TEXT_ONLY, if (parts.all { it.isText || it.isSmil }) 1 else 0)
            if (subId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subId)
        }
        val uri = context.contentResolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values) ?: error("MMS non enregistré")
        val id = ContentUris.parseId(uri)
        addAddress(context, id, "insert-address-token", ADDR_FROM)
        to.forEach { addAddress(context, id, it, ADDR_TO) }
        parts.forEach { addPart(context, id, it) }
        return uri
    }

    /** Envoyé (box 2) ou échec (box 5). */
    fun setBox(context: Context, uri: Uri, box: Int, messageId: String? = null) {
        context.contentResolver.update(
            uri,
            ContentValues().apply {
                put(Telephony.Mms.MESSAGE_BOX, box)
                messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            },
            null, null,
        )
    }

    private fun addAddress(context: Context, mmsId: Long, address: String, type: Int) {
        context.contentResolver.insert(
            Uri.parse("content://mms/$mmsId/addr"),
            ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, type)
                put(Telephony.Mms.Addr.CHARSET, CHARSET_UTF8)
            },
        )
    }

    private fun addPart(context: Context, mmsId: Long, part: MmsPart) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            part.name?.let { put(Telephony.Mms.Part.NAME, it) }
            part.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, "<$it>") }
            part.contentLocation?.let { put(Telephony.Mms.Part.CONTENT_LOCATION, it) }
            part.charset?.let { put(Telephony.Mms.Part.CHARSET, it) }
            // Le texte (et la mise en page) est rangé directement ; le reste dans un fichier.
            if (part.isText || part.isSmil) {
                put(Telephony.Mms.Part.TEXT, part.text())
                if (part.charset == null) put(Telephony.Mms.Part.CHARSET, CHARSET_UTF8)
            }
        }
        val uri = context.contentResolver.insert(Uri.parse("content://mms/$mmsId/part"), values) ?: return
        if (!part.isText && !part.isSmil) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(part.data) }
        }
    }

    /** Relit les pièces d'un MMS déjà rangé (pour le renvoyer). */
    fun readParts(context: Context, mmsId: Long): List<MmsPart> {
        val out = mutableListOf<MmsPart>()
        context.contentResolver.query(
            Uri.parse("content://mms/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT, Telephony.Mms.Part.NAME, Telephony.Mms.Part.CONTENT_ID, Telephony.Mms.Part.CONTENT_LOCATION),
            "${Telephony.Mms.Part.MSG_ID} = ?", arrayOf(mmsId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getString(1).orEmpty()
                val data = if (ct == "text/plain" || ct == "application/smil") {
                    c.getString(2).orEmpty().toByteArray()
                } else {
                    context.contentResolver.openInputStream(Uri.parse("content://mms/part/${c.getLong(0)}"))?.use { it.readBytes() } ?: continue
                }
                out += MmsPart(ct, data, c.getString(3), c.getString(4)?.trim('<', '>'), c.getString(5), if (ct == "text/plain") CHARSET_UTF8 else null)
            }
        }
        return out
    }
}
