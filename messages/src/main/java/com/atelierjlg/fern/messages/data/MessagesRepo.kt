package com.atelierjlg.fern.messages.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.atelierjlg.fern.common.SearchText

/** Une conversation (un « fil »). */
data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val snippet: String,
    val date: Long,
    val unread: Int,
    /** Nom(s) trouvé(s) dans les contacts, sinon les numéros. */
    val name: String,
    val photoUri: String?,
)

enum class MsgStatus { Received, Sending, Sent, Delivered, Failed }

/** Un message, SMS ou MMS. La clé distingue les deux : « s12 » ≠ « m12 » (l'ancienne appli les confondait). */
data class Msg(
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val status: MsgStatus,
    /** Images d'un MMS (content://mms/part/…). */
    val images: List<Uri> = emptyList(),
    /** Autres pièces jointes d'un MMS (vidéo, son, contact…), pas encore affichées. */
    val otherParts: Int = 0,
) {
    val key: String get() = (if (isMms) "m" else "s") + id
}

/**
 * Lecture et écriture des SMS / MMS dans la base d'Android (Telephony).
 * Fern Messages n'a pas de base à lui : l'historique du téléphone est la seule source.
 * À appeler hors du fil principal.
 */
class MessagesRepo(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val contactCache = HashMap<String, Pair<String, String?>?>()

    // ─── Conversations ───────────────────────────────────────────────────────

    fun conversations(): List<Conversation> {
        val addresses = canonicalAddresses()
        val unread = unreadCounts()
        val out = mutableListOf<Conversation>()
        resolver.query(
            Uri.parse("content://mms-sms/conversations?simple=true"),
            arrayOf(Telephony.Threads._ID, Telephony.Threads.DATE, Telephony.Threads.RECIPIENT_IDS, Telephony.Threads.SNIPPET, Telephony.Threads.MESSAGE_COUNT),
            null, null, "${Telephony.Threads.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(4) == 0) continue
                val id = c.getLong(0)
                val nums = c.getString(2).orEmpty().split(' ').mapNotNull { it.toLongOrNull()?.let(addresses::get) }
                val (name, photo) = displayFor(nums)
                out += Conversation(
                    threadId = id,
                    addresses = nums,
                    snippet = c.getString(3).orEmpty().ifBlank { "Photo ou pièce jointe" },
                    date = c.getLong(1),
                    unread = unread[id] ?: 0,
                    name = name,
                    photoUri = photo,
                )
            }
        }
        return out
    }

    private fun canonicalAddresses(): Map<Long, String> {
        val map = HashMap<Long, String>()
        runCatching {
            resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)?.use { c ->
                while (c.moveToNext()) map[c.getLong(0)] = c.getString(1).orEmpty()
            }
        }
        return map
    }

    private fun unreadCounts(): Map<Long, Int> {
        val map = HashMap<Long, Int>()
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID), "${Telephony.Sms.READ} = 0", null, null,
        )?.use { c -> while (c.moveToNext()) map.merge(c.getLong(0), 1) { a, b -> a + b } }
        runCatching {
            resolver.query(
                Telephony.Mms.Inbox.CONTENT_URI, arrayOf(Telephony.Mms.THREAD_ID), "${Telephony.Mms.READ} = 0", null, null,
            )?.use { c -> while (c.moveToNext()) map.merge(c.getLong(0), 1) { a, b -> a + b } }
        }
        return map
    }

    /** « Léa », « Léa, Paul », ou le numéro s'il n'est pas dans les contacts. */
    fun displayFor(addresses: List<String>): Pair<String, String?> {
        if (addresses.isEmpty()) return "Inconnu" to null
        val found = addresses.map { it to contact(it) }
        val name = found.joinToString(", ") { (num, ct) -> ct?.first ?: com.atelierjlg.fern.common.PhoneNumbers.format(num) }
        val photo = if (addresses.size == 1) found.first().second?.second else null
        return name to photo
    }

    /** Nom et photo d'un numéro (gardés en mémoire pendant que l'appli tourne). */
    fun contact(number: String): Pair<String, String?>? = contactCache.getOrPut(number) {
        if (number.isBlank()) return@getOrPut null
        runCatching {
            resolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: number) to c.getString(1) else null }
        }.getOrNull()
    }

    fun forgetContacts() = contactCache.clear()

    /** Les numéros d'une conversation. */
    fun addressesOf(threadId: Long): List<String> =
        conversations().firstOrNull { it.threadId == threadId }?.addresses.orEmpty()

    fun threadIdFor(addresses: List<String>): Long =
        if (addresses.size == 1) Telephony.Threads.getOrCreateThreadId(context, addresses.first())
        else Telephony.Threads.getOrCreateThreadId(context, addresses.toSet())

    // ─── Messages d'une conversation ─────────────────────────────────────────

    fun messages(threadId: Long): List<Msg> {
        val out = mutableListOf<Msg>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(4)
                val status = when (type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> MsgStatus.Received
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> MsgStatus.Sending
                    Telephony.Sms.MESSAGE_TYPE_FAILED -> MsgStatus.Failed
                    else -> if (c.getInt(5) == Telephony.Sms.STATUS_COMPLETE) MsgStatus.Delivered else MsgStatus.Sent
                }
                out += Msg(
                    id = c.getLong(0), isMms = false, threadId = threadId, address = c.getString(1).orEmpty(),
                    body = c.getString(2).orEmpty(), date = c.getLong(3), outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = status,
                )
            }
        }
        runCatching { out += mms(threadId) }
        return out.sortedBy { it.date }
    }

    private fun mms(threadId: Long): List<Msg> {
        val out = mutableListOf<Msg>()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
            "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val box = c.getInt(2)
                val text = StringBuilder()
                val images = mutableListOf<Uri>()
                var others = 0
                resolver.query(Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(id.toString()), null)?.use { p ->
                    while (p.moveToNext()) {
                        val ct = p.getString(1).orEmpty()
                        when {
                            ct == "text/plain" -> p.getString(2)?.let { if (text.isNotEmpty()) text.append('\n'); text.append(it) }
                            ct.startsWith("image/") -> images += Uri.parse("content://mms/part/${p.getLong(0)}")
                            ct == "application/smil" -> Unit
                            else -> others++
                        }
                    }
                }
                val outgoing = box != Telephony.Mms.MESSAGE_BOX_INBOX
                out += Msg(
                    id = id, isMms = true, threadId = threadId,
                    address = if (outgoing) "" else mmsSender(id),
                    body = text.toString(),
                    // Les dates des MMS sont en secondes (celles des SMS en millisecondes).
                    date = c.getLong(1) * 1000L,
                    outgoing = outgoing,
                    status = when (box) {
                        Telephony.Mms.MESSAGE_BOX_INBOX -> MsgStatus.Received
                        Telephony.Mms.MESSAGE_BOX_OUTBOX -> MsgStatus.Sending
                        5 -> MsgStatus.Failed // « échec » (MESSAGE_BOX_FAILED)
                        else -> MsgStatus.Sent
                    },
                    images = images,
                    otherParts = others,
                )
            }
        }
        return out
    }

    /** L'expéditeur d'un MMS reçu (adresse de type « From » = 137). */
    private fun mmsSender(mmsId: Long): String =
        runCatching {
            resolver.query(Uri.parse("content://mms/$mmsId/addr"), arrayOf("address"), "type = 137", null, null)
                ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
        }.getOrNull().orEmpty()

    // ─── Écriture ────────────────────────────────────────────────────────────

    fun markRead(threadId: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        resolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
        runCatching {
            resolver.update(Telephony.Mms.CONTENT_URI, values, "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
        }
    }

    fun delete(msg: Msg) {
        val base = if (msg.isMms) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI
        resolver.delete(ContentUris.withAppendedId(base, msg.id), null, null)
    }

    /** Supprime toute la conversation : SMS ET MMS. */
    fun deleteThread(threadId: Long) {
        resolver.delete(ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), null, null)
    }

    /** Écrit un SMS reçu. Renvoie l'identifiant de la conversation. */
    fun writeIncoming(address: String, body: String, dateSent: Long, subscriptionId: Int): Long {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, address)
        resolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, dateSent)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.THREAD_ID, threadId)
                if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            },
        )
        return threadId
    }

    /** Les SMS non lus d'une conversation (pour la notification), du plus ancien au plus récent. */
    fun unreadIn(threadId: Long, max: Int = 6): List<Msg> =
        messages(threadId).filter { !it.outgoing }.let { all ->
            val unreadIds = HashSet<Long>()
            resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()), null,
            )?.use { c -> while (c.moveToNext()) unreadIds += c.getLong(0) }
            all.filter { !it.isMms && it.id in unreadIds }.takeLast(max)
        }

    // ─── Recherche ───────────────────────────────────────────────────────────

    /** Cherche dans tous les SMS (sans accents, tous les mots). */
    fun search(query: String, limit: Int = 200): List<Msg> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<Msg>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            null, null, "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val body = c.getString(3).orEmpty()
                if (!SearchText.matches(body, query)) continue
                val type = c.getInt(5)
                out += Msg(
                    id = c.getLong(0), isMms = false, threadId = c.getLong(1), address = c.getString(2).orEmpty(), body = body,
                    date = c.getLong(4), outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX, status = MsgStatus.Sent,
                )
            }
        }
        return out
    }
}
