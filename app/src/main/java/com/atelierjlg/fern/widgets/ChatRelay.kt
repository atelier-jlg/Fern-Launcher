package com.atelierjlg.fern.widgets

import com.atelierjlg.fern.data.ChatEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Le relais du widget Chat, via ntfy (https://ntfy.sh, logiciel libre, sans compte).
 * C'est comme une boîte aux lettres publique dont seul vous deux connaissez le nom :
 * - envoyer = POST d'un petit texte JSON sur https://ntfy.sh/<sujet> ;
 * - lire = GET https://ntfy.sh/<sujet>/json?poll=1&since=<dernier lu>.
 * Les messages ne restent que quelques heures sur le serveur. Rien n'apparaît dans les SMS.
 */
object ChatRelay {

    private val json = Json { ignoreUnknownKeys = true }
    private val topicRegex = Regex("^[A-Za-z0-9_-]{8,64}$")

    /** Un nouveau code secret : « fern-chat- » + 20 caractères au hasard. */
    fun newTopic(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        return "fern-chat-" + (1..20).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    fun newDeviceId(): String = (1..12).map { "0123456789abcdef"[SecureRandom().nextInt(16)] }.joinToString("")

    /** Accepte le code seul ou un lien complet (https://ntfy.sh/fern-chat-…). Null si invalide. */
    fun parseCode(text: String): String? {
        val code = text.trim().trimEnd('/').substringAfterLast('/')
        return code.takeIf { topicRegex.matches(it) }
    }

    fun encode(event: ChatEvent): String = json.encodeToString(ChatEvent.serializer(), event)

    /**
     * Lit la réponse de ntfy (un objet JSON par ligne) : renvoie nos événements et l'identifiant du dernier message.
     * Les lignes qui ne sont pas à nous (autre format) sont ignorées.
     */
    fun parsePoll(body: String): Pair<List<ChatEvent>, String> {
        var lastId = ""
        val events = mutableListOf<ChatEvent>()
        for (line in body.lineSequence().filter { it.isNotBlank() }) {
            runCatching {
                val obj = json.parseToJsonElement(line).jsonObject
                if (obj["event"]?.jsonPrimitive?.content != "message") return@runCatching
                obj["id"]?.jsonPrimitive?.content?.let { lastId = it }
                val message = obj["message"]?.jsonPrimitive?.content ?: return@runCatching
                events += json.decodeFromString(ChatEvent.serializer(), message)
            }
        }
        return events to lastId
    }

    /** Envoie un changement. true si le serveur l'a bien reçu. (À appeler sur Dispatchers.IO.) */
    fun send(server: String, topic: String, event: ChatEvent): Boolean = runCatching {
        val connection = URL("${server.trimEnd('/')}/$topic").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            connection.outputStream.use { it.write(encode(event).toByteArray()) }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    /** Les nouveaux messages depuis `lastId` (ou les 12 dernières heures). Null si pas de réseau. */
    fun poll(server: String, topic: String, lastId: String): Pair<List<ChatEvent>, String>? = runCatching {
        val since = URLEncoder.encode(lastId.ifEmpty { "12h" }, "UTF-8")
        val connection = URL("${server.trimEnd('/')}/$topic/json?poll=1&since=$since").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode != 200) return null
            parsePoll(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
