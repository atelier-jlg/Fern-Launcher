package com.atelierjlg.fern.keyboard.logic

/**
 * Ce que le clavier apprend de Jules, rangé dans le téléphone (jamais ailleurs) :
 * - des mots : un mot inconnu devient « connu » quand il a été gardé deux fois, ou tout de suite
 *   si on annule une correction (⌫ juste après) ou qu'on le choisit dans la barre ;
 * - des enchaînements de mots (« on se » → « voit ») pour proposer le mot suivant.
 *
 * Format texte, une entrée par ligne (lisible, facile à sauvegarder) :
 *   w<TAB>mot<TAB>compte
 *   b<TAB>mot1<TAB>mot2<TAB>compte
 */
class UserDictionary {
    // Lu par la correction (fil d'arrière-plan, parfois l'écran) et modifié par l'apprentissage :
    // chaque fonction prend le verrou de l'objet (@Synchronized), pour qu'un seul fil y touche à la fois.
    private val words = HashMap<String, Int>()
    private val shapes = HashMap<String, String>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()

    val wordCount: Int
        @Synchronized get() = words.count { it.value >= KNOWN }

    /** Poids d'un mot appris (0 si pas encore connu). */
    @Synchronized
    fun weight(lower: String): Int = (words[lower] ?: 0).let { if (it >= KNOWN) it else 0 }

    /** Les mots appris, tels qu'écrits (avec majuscules). */
    @Synchronized
    fun knownWords(): List<String> = words.filter { it.value >= KNOWN }.keys.map { shapes[it] ?: it }

    /** Un mot gardé tel quel (+1), ou confirmé (+KNOWN : connu tout de suite). */
    @Synchronized
    fun learn(word: String, confirmed: Boolean = false) {
        if (word.length < 2 || word.length > 40) return
        val lower = word.lowercase()
        words[lower] = (words[lower] ?: 0) + if (confirmed) KNOWN else 1
        // On garde la forme avec majuscule si on l'a tapée ainsi (un prénom, une ville…).
        if (word != lower || lower !in shapes) shapes[lower] = word
        prune()
    }

    @Synchronized
    fun forget(word: String) {
        val lower = word.lowercase()
        words.remove(lower)
        shapes.remove(lower)
    }

    @Synchronized
    fun clear() {
        words.clear()
        shapes.clear()
        bigrams.clear()
    }

    @Synchronized
    fun learnPair(first: String, second: String) {
        if (first.isEmpty() || second.isEmpty()) return
        val next = bigrams.getOrPut(first.lowercase()) { HashMap() }
        val key = second.lowercase()
        next[key] = (next[key] ?: 0) + 1
        if (next.size > MAX_NEXT) next.entries.minByOrNull { it.value }?.let { next.remove(it.key) }
        if (bigrams.size > MAX_BIGRAM_KEYS) {
            bigrams.entries.minByOrNull { e -> e.value.values.sum() }?.let { bigrams.remove(it.key) }
        }
    }

    /** Les mots qui suivent le plus souvent `previous` chez Jules. */
    @Synchronized
    fun next(previous: String, limit: Int): List<String> =
        bigrams[previous.lowercase()].orEmpty().entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { shapes[it.key] ?: it.key }

    private fun prune() {
        if (words.size <= MAX_WORDS) return
        // On oublie d'abord les mots vus une seule fois.
        val drop = words.entries.sortedBy { it.value }.take(words.size - MAX_WORDS).map { it.key }
        drop.forEach { words.remove(it); shapes.remove(it) }
    }

    @Synchronized
    fun serialize(): String = buildString {
        for ((lower, n) in words) append("w\t").append(shapes[lower] ?: lower).append('\t').append(n).append('\n')
        for ((a, next) in bigrams) for ((b, n) in next) append("b\t").append(a).append('\t').append(b).append('\t').append(n).append('\n')
    }

    companion object {
        /** À partir de combien de fois un mot est connu. */
        const val KNOWN = 2
        private const val MAX_WORDS = 5_000
        private const val MAX_BIGRAM_KEYS = 3_000
        private const val MAX_NEXT = 12

        fun parse(text: String): UserDictionary {
            val d = UserDictionary()
            text.lineSequence().forEach { line ->
                val p = line.split('\t')
                when {
                    p.size == 3 && p[0] == "w" -> {
                        val lower = p[1].lowercase()
                        d.words[lower] = p[2].toIntOrNull() ?: return@forEach
                        d.shapes[lower] = p[1]
                    }
                    p.size == 4 && p[0] == "b" -> {
                        val n = p[3].toIntOrNull() ?: return@forEach
                        d.bigrams.getOrPut(p[1]) { HashMap() }[p[2]] = n
                    }
                }
            }
            return d
        }
    }
}
