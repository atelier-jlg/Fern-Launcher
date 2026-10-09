package com.atelierjlg.fern.messages.mms

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/*
 * Le format binaire des MMS (« PDU », norme OMA-MMS-ENC / WAP-230-WSP).
 *
 * Un MMS voyage comme une petite enveloppe binaire : des en-têtes codés sur un octet
 * (0x89 = « De », 0x97 = « À », 0x84 = « Type de contenu »…), puis un corps en plusieurs parties
 * (texte, photo, carte de contact…). Android télécharge et envoie ces enveloppes pour nous,
 * mais c'est à l'appli SMS par défaut de les lire et de les fabriquer. Code pur, testé.
 */

/** Une pièce d'un MMS. */
class MmsPart(
    val contentType: String,
    val data: ByteArray,
    val name: String? = null,
    val contentId: String? = null,
    val contentLocation: String? = null,
    /** Code MIBenum du jeu de caractères (106 = UTF-8). */
    val charset: Int? = null,
) {
    val isText get() = contentType.equals("text/plain", true)
    val isSmil get() = contentType.equals("application/smil", true)
    val isImage get() = contentType.startsWith("image/", true)
    val isVcard get() = contentType.equals("text/x-vcard", true) || contentType.equals("text/vcard", true)

    fun text(): String = String(data, Pdu.charsetOf(charset ?: 106))
}

/** L'annonce d'un MMS (« un MMS t'attend sur le serveur de l'opérateur »). */
data class MmsNotification(
    val transactionId: String,
    val contentLocation: String,
    val from: String?,
    val size: Long,
    /** Date limite absolue (secondes depuis 1970), ou null. */
    val expiry: Long?,
    val subject: String?,
    /** Ou bien : durée de validité (secondes) à compter de l'arrivée de l'annonce. */
    val expiryDelta: Long? = null,
)

/** Un MMS complet (reçu ou à envoyer). */
class MmsMessage(
    val type: Int,
    val transactionId: String? = null,
    val messageId: String? = null,
    val from: String? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val subject: String? = null,
    /** Secondes depuis 1970. */
    val date: Long? = null,
    val contentType: String? = null,
    val parts: List<MmsPart> = emptyList(),
    val responseStatus: Int? = null,
)

object Pdu {
    // Types de messages
    const val SEND_REQ = 0x80
    const val SEND_CONF = 0x81
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85
    const val DELIVERY_IND = 0x86

    // En-têtes (déjà « | 0x80 »)
    private const val BCC = 0x81
    private const val CC = 0x82
    private const val CONTENT_LOCATION = 0x83
    private const val CONTENT_TYPE = 0x84
    private const val DATE = 0x85
    private const val DELIVERY_REPORT = 0x86
    private const val EXPIRY = 0x88
    private const val FROM = 0x89
    private const val MESSAGE_CLASS = 0x8A
    private const val MESSAGE_ID = 0x8B
    private const val MESSAGE_TYPE = 0x8C
    private const val MMS_VERSION = 0x8D
    private const val MESSAGE_SIZE = 0x8E
    private const val PRIORITY = 0x8F
    private const val READ_REPORT = 0x90
    private const val REPORT_ALLOWED = 0x91
    private const val RESPONSE_STATUS = 0x92
    private const val STATUS = 0x95
    private const val SUBJECT = 0x96
    private const val TO = 0x97
    private const val TRANSACTION_ID = 0x98

    const val VERSION_1_2 = 0x92
    const val STATUS_RETRIEVED = 0x81
    const val RESPONSE_OK = 0x80

    /** Types de contenu « bien connus » (codés sur un octet). */
    private val WELL_KNOWN = mapOf(
        0x00 to "*/*", 0x01 to "text/*", 0x02 to "text/html", 0x03 to "text/plain",
        0x06 to "text/x-vCalendar", 0x07 to "text/x-vCard", 0x08 to "text/vnd.wap.wml",
        0x0B to "multipart/*", 0x0C to "multipart/mixed", 0x0F to "multipart/alternative",
        0x10 to "application/*", 0x1C to "image/*", 0x1D to "image/gif", 0x1E to "image/jpeg",
        0x1F to "image/tiff", 0x20 to "image/png", 0x21 to "image/vnd.wap.wbmp",
        0x22 to "application/vnd.wap.multipart.*", 0x23 to "application/vnd.wap.multipart.mixed",
        0x26 to "application/vnd.wap.multipart.alternative", 0x27 to "application/xml", 0x28 to "text/xml",
        0x33 to "application/vnd.wap.multipart.related", 0x3E to "application/vnd.wap.mms-message",
    )
    private val WELL_KNOWN_CODES = WELL_KNOWN.entries.associate { it.value.lowercase() to it.key }

    const val MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    const val MULTIPART_MIXED = "application/vnd.wap.multipart.mixed"

    /** Jeux de caractères : numéro MIBenum → nom Java. */
    fun charsetOf(code: Int): Charset = when (code) {
        3 -> Charsets.US_ASCII
        4 -> Charsets.ISO_8859_1
        1000 -> Charsets.UTF_16BE // UCS-2
        1013 -> Charsets.UTF_16BE
        1014 -> Charsets.UTF_16LE
        1015 -> Charsets.UTF_16
        else -> Charsets.UTF_8
    }

    /** « +33612345678/TYPE=PLMN » → « +33612345678 ». */
    fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE", raw).trim()

    // ─── Lecture ─────────────────────────────────────────────────────────────

    fun parseNotification(bytes: ByteArray): MmsNotification? = runCatching {
        val r = Reader(bytes)
        var type = -1
        var trId: String? = null
        var location: String? = null
        var from: String? = null
        var size = 0L
        var expiry: Long? = null
        var expiryDelta: Long? = null
        var subject: String? = null
        while (r.hasMore()) {
            val field = r.peek()
            if (field < 0x80) {
                r.skipApplicationHeader()
                continue
            }
            r.read()
            when (field) {
                MESSAGE_TYPE -> type = r.read() and 0xFF
                TRANSACTION_ID -> trId = r.textString()
                CONTENT_LOCATION -> location = r.textString()
                FROM -> from = r.fromValue()
                MESSAGE_SIZE -> size = r.longInteger()
                EXPIRY -> r.expiry().let { (absolute, v) -> if (absolute) expiry = v else expiryDelta = v }
                SUBJECT -> subject = r.encodedString()
                else -> r.skipValue()
            }
        }
        if (type != NOTIFICATION_IND || location == null) null
        else MmsNotification(trId.orEmpty(), location, from?.let(::cleanAddress), size, expiry, subject, expiryDelta)
    }.getOrNull()

    /** Lit un MMS complet (reçu : « retrieve-conf », envoyé : « send-req », réponse « send-conf »…). */
    fun parseMessage(bytes: ByteArray): MmsMessage? = runCatching {
        val r = Reader(bytes)
        var type = -1
        var trId: String? = null
        var msgId: String? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var subject: String? = null
        var date: Long? = null
        var status: Int? = null
        var contentType: ContentType? = null
        while (r.hasMore() && contentType == null) {
            val field = r.peek()
            if (field < 0x80) {
                r.skipApplicationHeader()
                continue
            }
            r.read()
            when (field) {
                MESSAGE_TYPE -> type = r.read() and 0xFF
                TRANSACTION_ID -> trId = r.textString()
                MESSAGE_ID -> msgId = r.textString()
                FROM -> from = r.fromValue()
                TO -> to += cleanAddress(r.encodedString())
                CC -> cc += cleanAddress(r.encodedString())
                BCC -> r.encodedString()
                SUBJECT -> subject = r.encodedString()
                DATE -> date = r.longInteger()
                RESPONSE_STATUS -> status = r.read() and 0xFF
                CONTENT_TYPE -> contentType = r.contentType()
                else -> r.skipValue()
            }
        }
        val parts = if (contentType != null && contentType.mime.startsWith("application/vnd.wap.multipart", true)) {
            r.multipart()
        } else if (contentType != null && r.hasMore()) {
            listOf(MmsPart(contentType.mime, r.rest(), charset = contentType.charset))
        } else {
            emptyList()
        }
        MmsMessage(type, trId, msgId, from?.let(::cleanAddress), to, cc, subject, date, contentType?.mime, parts, status)
    }.getOrNull()

    internal class ContentType(val mime: String, val charset: Int? = null, val name: String? = null, val start: String? = null, val type: String? = null)

    private class Reader(private val b: ByteArray, private var pos: Int = 0, private val end: Int = b.size) {
        fun hasMore() = pos < end
        fun peek(): Int = b[pos].toInt() and 0xFF
        fun read(): Int = b[pos++].toInt() and 0xFF
        fun bytes(n: Int): ByteArray = b.copyOfRange(pos, pos + n).also { pos += n }
        fun rest(): ByteArray = bytes(end - pos)

        fun uintvar(): Long {
            var v = 0L
            while (true) {
                val x = read()
                v = (v shl 7) or (x and 0x7F).toLong()
                if (x and 0x80 == 0) return v
            }
        }

        /** Longueur d'une valeur : 0–30 directement, ou 31 suivi d'un uintvar. */
        fun valueLength(): Int {
            val x = read()
            return if (x < 31) x else if (x == 31) uintvar().toInt() else error("longueur invalide")
        }

        fun rawText(): ByteArray {
            if (peek() == 0x7F || peek() == 0x22) read() // guillemet / « quote »
            val start = pos
            while (pos < end && b[pos].toInt() != 0) pos++
            val out = b.copyOfRange(start, pos)
            if (pos < end) pos++ // le 0 final
            return out
        }

        fun textString(): String = String(rawText(), Charsets.UTF_8)

        fun longInteger(): Long {
            val n = read()
            var v = 0L
            repeat(n) { v = (v shl 8) or read().toLong() }
            return v
        }

        fun integerValue(): Long = if (peek() >= 0x80) (read() and 0x7F).toLong() else longInteger()

        /** Texte avec, éventuellement, son jeu de caractères. */
        fun encodedString(): String {
            if (peek() <= 31) {
                val len = valueLength()
                val stop = pos + len
                val charset = integerValue().toInt()
                val text = rawText()
                pos = stop
                return String(text, charsetOf(charset))
            }
            return textString()
        }

        /** « De » : soit une adresse, soit « à remplir par le réseau ». */
        fun fromValue(): String? {
            val len = valueLength()
            val stop = pos + len
            val token = read()
            val value = if (token == 0x80) encodedString() else null
            pos = stop
            return value
        }

        /** Expiration : (absolue ?, valeur). Relative = secondes à compter de la réception de l'annonce. */
        fun expiry(): Pair<Boolean, Long> {
            val len = valueLength()
            val stop = pos + len
            val token = read()
            val v = integerValue()
            pos = stop
            return (token == 0x80) to v
        }

        /** Saute une valeur inconnue (règle générale des en-têtes WSP). */
        fun skipValue() {
            val x = peek()
            when {
                x < 31 -> { read(); pos += x }
                x == 31 -> { read(); pos += uintvar().toInt() }
                x < 128 -> rawText()
                else -> read()
            }
        }

        /** En-tête « texte » (nom: valeur), rare dans les MMS. */
        fun skipApplicationHeader() {
            rawText()
            if (hasMore()) rawText()
        }

        fun contentType(): ContentType {
            val x = peek()
            if (x >= 0x80) return ContentType(WELL_KNOWN[read() and 0x7F] ?: "application/octet-stream")
            if (x in 32..127) return ContentType(textString())
            val len = valueLength()
            val stop = pos + len
            val mime = when {
                peek() >= 0x80 -> WELL_KNOWN[read() and 0x7F] ?: "application/octet-stream"
                peek() < 31 -> WELL_KNOWN[longInteger().toInt()] ?: "application/octet-stream"
                else -> textString()
            }
            var charset: Int? = null
            var name: String? = null
            var start: String? = null
            var type: String? = null
            while (pos < stop) {
                val p = peek()
                if (p < 0x80) {
                    // Paramètre « texte » : nom=valeur
                    val key = textString().lowercase()
                    val value = if (pos < stop && peek() < 0x80 && peek() >= 32) textString() else { integerValue().toString() }
                    when (key) {
                        "name", "filename" -> name = value
                        "charset" -> charset = value.toIntOrNull()
                        "start" -> start = value
                        "type" -> type = value
                    }
                    continue
                }
                read()
                when (p) {
                    0x81 -> charset = if (peek() == 0x80) { read(); null } else integerValue().toInt()
                    0x83, 0x89 -> type = if (peek() >= 0x80) WELL_KNOWN[read() and 0x7F] else textString()
                    0x8A, 0x99 -> start = textString()
                    0x85, 0x86, 0x97, 0x98 -> name = textString()
                    else -> skipValue()
                }
            }
            pos = stop
            return ContentType(mime, charset, name, start, type)
        }

        fun multipart(): List<MmsPart> {
            val count = uintvar().toInt()
            val parts = ArrayList<MmsPart>(count)
            repeat(count) {
                val headersLen = uintvar().toInt()
                val dataLen = uintvar().toInt()
                val headersEnd = pos + headersLen
                val ct = contentType()
                var cid: String? = null
                var location: String? = null
                var name = ct.name
                while (pos < headersEnd) {
                    val h = peek()
                    if (h < 0x80) {
                        val key = textString().lowercase()
                        val value = if (pos < headersEnd) textString() else ""
                        when (key) {
                            "content-id" -> cid = value
                            "content-location" -> location = value
                        }
                        continue
                    }
                    read()
                    when (h) {
                        0xC0 -> cid = textString()
                        0x8E -> location = textString()
                        0xAE, 0xC5 -> {
                            // Content-Disposition : on n'y lit que le nom de fichier.
                            val len = valueLength()
                            val stop = pos + len
                            if (pos < stop) read()
                            while (pos < stop) {
                                val p = read()
                                if (p == 0x85 || p == 0x86 || p == 0x97 || p == 0x98) name = name ?: textString() else { pos = stop }
                            }
                            pos = stop
                        }
                        else -> skipValue()
                    }
                }
                pos = headersEnd
                parts += MmsPart(ct.mime, bytes(dataLen), name, cid?.trim('<', '>', '"'), location, ct.charset)
            }
            return parts
        }
    }

    // ─── Écriture ────────────────────────────────────────────────────────────

    private class Writer {
        val out = ByteArrayOutputStream()
        fun byte(v: Int) = out.write(v and 0xFF)
        fun bytes(b: ByteArray) = out.write(b)
        fun shortInt(v: Int) = byte(v or 0x80)

        fun uintvar(value: Long) {
            val stack = ArrayList<Int>()
            var v = value
            stack += (v and 0x7F).toInt()
            v = v shr 7
            while (v > 0) {
                stack += ((v and 0x7F) or 0x80).toInt()
                v = v shr 7
            }
            stack.asReversed().forEach(::byte)
        }

        fun valueLength(len: Int) = if (len < 31) byte(len) else { byte(31); uintvar(len.toLong()) }

        fun text(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            if (b.isNotEmpty() && (b[0].toInt() and 0xFF) >= 0x80) byte(0x7F)
            bytes(b)
            byte(0)
        }

        fun quoted(s: String) {
            byte(0x22)
            bytes(s.toByteArray(Charsets.UTF_8))
            byte(0)
        }

        fun longInt(v: Long) {
            val b = ArrayList<Int>()
            var x = v
            do {
                b += (x and 0xFF).toInt()
                x = x shr 8
            } while (x > 0)
            byte(b.size)
            b.asReversed().forEach(::byte)
        }

        /** Texte en UTF-8 annoncé comme tel (sujet, adresse avec accents…). */
        fun utf8String(s: String) {
            val inner = Writer()
            inner.shortInt(106)
            inner.text(s)
            val b = inner.out.toByteArray()
            valueLength(b.size)
            bytes(b)
        }

        fun block(build: Writer.() -> Unit): ByteArray = Writer().apply(build).out.toByteArray()
    }

    /** Le type de contenu d'une pièce (+ jeu de caractères, nom). */
    private fun Writer.partContentType(part: MmsPart) {
        val code = WELL_KNOWN_CODES[part.contentType.lowercase()]
        val params = block {
            if (part.charset != null) {
                shortInt(0x01)
                shortInt(part.charset)
            }
            if (part.name != null) {
                shortInt(0x05)
                text(part.name)
            }
        }
        if (params.isEmpty()) {
            if (code != null) shortInt(code) else text(part.contentType)
            return
        }
        val media = block { if (code != null) shortInt(code) else text(part.contentType) }
        valueLength(media.size + params.size)
        bytes(media)
        bytes(params)
    }

    /** Fabrique un MMS à envoyer (« send-req »). */
    fun sendRequest(transactionId: String, to: List<String>, parts: List<MmsPart>, subject: String? = null, deliveryReport: Boolean = false): ByteArray {
        val w = Writer()
        w.byte(MESSAGE_TYPE); w.byte(SEND_REQ)
        w.byte(TRANSACTION_ID); w.text(transactionId)
        w.byte(MMS_VERSION); w.byte(VERSION_1_2)
        // « De » : laissé au réseau (jeton « insert-address »).
        w.byte(FROM); w.byte(1); w.byte(0x81)
        to.forEach { addr ->
            w.byte(TO)
            val full = if (addr.contains('@')) addr else addr.filter { it.isDigit() || it == '+' } + "/TYPE=PLMN"
            w.text(full)
        }
        if (!subject.isNullOrBlank()) { w.byte(SUBJECT); w.utf8String(subject) }
        w.byte(MESSAGE_CLASS); w.byte(0x80) // personnel
        w.byte(PRIORITY); w.byte(0x81) // normale
        w.byte(DELIVERY_REPORT); w.byte(if (deliveryReport) 0x80 else 0x81)
        w.byte(READ_REPORT); w.byte(0x81)

        // Type : multipart/related, qui commence par la mise en page SMIL.
        val smil = parts.firstOrNull { it.isSmil }
        w.byte(CONTENT_TYPE)
        val ct = w.block {
            shortInt(WELL_KNOWN_CODES[MULTIPART_RELATED]!!)
            if (smil != null) {
                shortInt(0x09); text("application/smil")
                shortInt(0x0A); text("<${smil.contentId ?: "smil"}>")
            }
        }
        w.valueLength(ct.size)
        w.bytes(ct)
        w.uintvar(parts.size.toLong())
        parts.forEach { part ->
            val headers = w.block {
                partContentType(part)
                part.contentId?.let { byte(0xC0); quoted("<$it>") }
                part.contentLocation?.let { byte(0x8E); text(it) }
            }
            w.uintvar(headers.size.toLong())
            w.uintvar(part.data.size.toLong())
            w.bytes(headers)
            w.bytes(part.data)
        }
        return w.out.toByteArray()
    }

    /** « Bien reçu » envoyé à l'opérateur après un téléchargement. */
    fun notifyResponse(transactionId: String): ByteArray = Writer().run {
        byte(MESSAGE_TYPE); byte(NOTIFYRESP_IND)
        byte(TRANSACTION_ID); text(transactionId)
        byte(MMS_VERSION); byte(VERSION_1_2)
        byte(STATUS); byte(STATUS_RETRIEVED)
        byte(REPORT_ALLOWED); byte(0x80)
        out.toByteArray()
    }

    fun acknowledge(transactionId: String): ByteArray = Writer().run {
        byte(MESSAGE_TYPE); byte(ACKNOWLEDGE_IND)
        byte(TRANSACTION_ID); text(transactionId)
        byte(MMS_VERSION); byte(VERSION_1_2)
        byte(REPORT_ALLOWED); byte(0x80)
        out.toByteArray()
    }

    /** Une annonce de MMS, comme en envoie l'opérateur (sert aux tests). */
    fun notification(transactionId: String, location: String, from: String, size: Long, expirySeconds: Long): ByteArray = Writer().run {
        byte(MESSAGE_TYPE); byte(NOTIFICATION_IND)
        byte(TRANSACTION_ID); text(transactionId)
        byte(MMS_VERSION); byte(0x90)
        byte(FROM)
        val addr = block { byte(0x80); text("$from/TYPE=PLMN") }
        valueLength(addr.size); bytes(addr)
        byte(MESSAGE_CLASS); byte(0x80)
        byte(MESSAGE_SIZE); longInt(size)
        byte(EXPIRY)
        val exp = block { byte(0x81); longInt(expirySeconds) }
        valueLength(exp.size); bytes(exp)
        byte(CONTENT_LOCATION); text(location)
        out.toByteArray()
    }

    /** La mise en page SMIL d'un MMS : la photo en haut, le texte en dessous. */
    fun smil(image: String?, text: String?, vcard: String? = null): String = buildString {
        append("<smil><head><layout><root-layout/>")
        if (image != null) append("<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>")
        if (text != null) append("<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>")
        append("</layout></head><body><par dur=\"5000ms\">")
        if (image != null) append("<img src=\"$image\" region=\"Image\"/>")
        if (text != null) append("<text src=\"$text\" region=\"Text\"/>")
        if (vcard != null) append("<ref src=\"$vcard\"/>")
        append("</par></body></smil>")
    }
}
