package com.atelierjlg.fern.keyboard.logic

/**
 * La bibliothèque de memes et stickers de Jules : des images importées une fois (depuis la galerie
 * ou depuis les stickers de Fern), avec des mots-clés pour les retrouver (« chat », « lundi »…).
 * Les fichiers sont dans le téléphone (dossier privé du clavier) ; ici, seulement la liste.
 */
class MemeLibrary {
    data class Meme(
        val id: String,
        /** Nom du fichier dans le dossier memes/. */
        val file: String,
        val mime: String,
        val tags: List<String> = emptyList(),
        val added: Long = 0L,
        val uses: Int = 0,
        /** Venu des stickers de Fern (nom d'origine, pour ne pas l'importer deux fois). */
        val fromFern: String? = null,
    )

    private val items = ArrayList<Meme>()

    /** Les plus utilisés d'abord, puis les plus récents. */
    val all: List<Meme> get() = items.sortedWith(compareByDescending<Meme> { it.uses }.thenByDescending { it.added })

    fun add(meme: Meme) {
        items.removeAll { it.id == meme.id }
        items.add(meme)
    }

    fun remove(id: String): Meme? {
        val i = items.indexOfFirst { it.id == id }
        return if (i >= 0) items.removeAt(i) else null
    }

    fun setTags(id: String, text: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(tags = parseTags(text))
    }

    fun used(id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(uses = items[i].uses + 1)
    }

    fun hasFernSticker(name: String) = items.any { it.fromFern == name }

    /** Tous les mots-clés, du plus courant au plus rare (pour les filtres en pilules). */
    fun topTags(limit: Int): List<String> =
        items.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key }

    /** Les memes dont un mot-clé commence par la recherche (sans accents ni majuscules). */
    fun search(query: String): List<Meme> {
        val q = Words.fold(query.trim())
        if (q.isEmpty()) return all
        return all.filter { m -> m.tags.any { Words.fold(it).startsWith(q) } }
    }

    fun serialize(): String = buildString {
        for (m in items) {
            append(m.id).append('\t').append(m.file).append('\t').append(m.mime).append('\t')
                .append(m.tags.joinToString("|")).append('\t').append(m.added).append('\t').append(m.uses).append('\t')
                .append(m.fromFern.orEmpty()).append('\n')
        }
    }

    companion object {
        /** « chat, lundi  Fatigue » → [chat, lundi, fatigue]. */
        fun parseTags(text: String): List<String> =
            text.split(',', ';', ' ', '#').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

        fun parse(text: String): MemeLibrary {
            val lib = MemeLibrary()
            text.lineSequence().forEach { line ->
                val p = line.split('\t')
                if (p.size >= 6) {
                    lib.items.add(
                        Meme(
                            id = p[0], file = p[1], mime = p[2],
                            tags = p[3].split('|').filter { it.isNotEmpty() },
                            added = p[4].toLongOrNull() ?: 0L,
                            uses = p[5].toIntOrNull() ?: 0,
                            fromFern = p.getOrNull(6)?.ifEmpty { null },
                        ),
                    )
                }
            }
            return lib
        }

        /** Type d'image d'après les premiers octets du fichier (PNG, GIF, WebP, JPEG). */
        fun sniffMime(head: ByteArray): String = when {
            head.size >= 4 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> "image/png"
            head.size >= 3 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() -> "image/gif"
            head.size >= 12 && String(head, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "image/jpeg"
            else -> "image/png"
        }

        fun extension(mime: String) = when (mime) {
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/jpeg" -> "jpg"
            else -> "png"
        }
    }
}
