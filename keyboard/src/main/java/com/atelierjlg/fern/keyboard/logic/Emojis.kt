package com.atelierjlg.fern.keyboard.logic

/** Un emoji, avec son nom et ses mots-clés en français (données Unicode CLDR). */
data class Emoji(val char: String, val group: Int, val skinTones: Boolean, val name: String, val keywords: List<String>)

/**
 * Tous les emoji, rangés par groupe, et la recherche en français (« chat », « coeur », « fête »…).
 * Construit depuis assets/emoji_fr.tsv (tools/build_emoji.py).
 */
class EmojiIndex(val all: List<Emoji>) {
    val groups: List<List<Emoji>> = (0 until GROUP_NAMES.size).map { g -> all.filter { it.group == g } }
    private val folded: List<Pair<String, List<String>>> = all.map { e -> norm(e.name) to e.keywords.map { norm(it) } }

    /** Sans accents ni majuscules, et « œ » écrit « oe » (on tape « coeur » pour « cœur »). */
    private fun norm(s: String) = Words.fold(s.replace("œ", "oe").replace("Œ", "oe").replace("æ", "ae"))

    /**
     * Recherche (sans accents, sans majuscules) : le nom exact d'abord (« chat » → 🐈), puis un mot-clé
     * exact, puis un mot-clé qui commence pareil, puis le nom qui contient la recherche.
     */
    fun search(query: String, limit: Int = 40): List<Emoji> {
        val q = norm(query.trim())
        if (q.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Int, Int>>()
        for (i in all.indices) {
            val (name, keywords) = folded[i]
            val score = when {
                name == q -> 4
                keywords.any { it == q } -> 3
                keywords.any { k -> k.startsWith(q) || k.split(' ').any { it.startsWith(q) } } -> 2
                name.contains(q) -> 1
                else -> 0
            }
            if (score > 0) scored.add(score to i)
        }
        return scored.sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .take(limit).map { all[it.second] }
    }

    /** L'emoji qui va avec un mot tapé (« chat » → 🐈), seulement sur un mot-clé exact. */
    fun forWord(word: String): Emoji? {
        val q = norm(word)
        if (q.length < 3) return null
        val i = folded.indexOfFirst { (name, keywords) -> name == q || keywords.any { it == q } }
        return if (i >= 0) all[i] else null
    }

    companion object {
        val GROUP_NAMES = listOf("Smileys", "Gens", "Nature", "Nourriture", "Voyages", "Activités", "Objets", "Symboles", "Drapeaux")
        /** Un emoji pour représenter chaque groupe dans les onglets. */
        val GROUP_ICONS = listOf("😀", "👋", "🌿", "🍓", "🚲", "⚽", "💡", "❤️", "🏳️")

        fun parse(lines: Sequence<String>): EmojiIndex = EmojiIndex(
            lines.mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 5) return@mapNotNull null
                Emoji(p[1], p[0].toIntOrNull() ?: return@mapNotNull null, p[2] == "1", p[3], p[4].split('|').filter { it.isNotEmpty() })
            }.toList(),
        )

        /** Teintes de peau (modificateurs Fitzpatrick), dans l'ordre des réglages. */
        val SKIN_TONES = listOf("", "🏻", "🏼", "🏽", "🏾", "🏿")

        /**
         * Applique une teinte à un emoji qui l'accepte (👍 → 👍🏽). Seulement les emoji simples
         * (pas les familles ou couples composés de plusieurs personnes).
         */
        fun withSkinTone(e: Emoji, tone: Int): String {
            val mod = SKIN_TONES.getOrNull(tone).orEmpty()
            if (mod.isEmpty() || !e.skinTones || '‍' in e.char) return e.char
            val base = e.char.replace("️", "")
            val first = base.codePointAt(0)
            val firstLen = Character.charCount(first)
            return base.substring(0, firstLen) + mod + base.substring(firstLen)
        }
    }
}

/** Emoji favoris (choisis par Jules, à l'appui long) et récents. */
class EmojiMemory {
    val favorites = ArrayList<String>()
    val recents = ArrayList<String>()

    fun used(emoji: String) {
        recents.remove(emoji)
        recents.add(0, emoji)
        while (recents.size > MAX_RECENTS) recents.removeAt(recents.lastIndex)
    }

    /** Ajoute ou retire des favoris. Renvoie true s'il est maintenant favori. */
    fun toggleFavorite(emoji: String): Boolean {
        if (!favorites.remove(emoji)) {
            favorites.add(emoji)
            return true
        }
        return false
    }

    fun serialize(): String = "f\t" + favorites.joinToString("\t") + "\nr\t" + recents.joinToString("\t") + "\n"

    companion object {
        private const val MAX_RECENTS = 32

        fun parse(text: String): EmojiMemory {
            val m = EmojiMemory()
            text.lineSequence().forEach { line ->
                val p = line.split('\t')
                when (p.firstOrNull()) {
                    "f" -> m.favorites.addAll(p.drop(1).filter { it.isNotEmpty() })
                    "r" -> m.recents.addAll(p.drop(1).filter { it.isNotEmpty() })
                }
            }
            return m
        }
    }
}
