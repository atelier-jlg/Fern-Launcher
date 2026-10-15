package com.atelierjlg.fern.keyboard.logic

import kotlin.math.abs
import kotlin.math.ln

/** Un mot proposé, avec son « coût » (combien de fautes, pondérées) et sa fréquence. */
data class Candidate(val word: String, val cost: Float, val freq: Int) {
    /** Plus c'est haut, mieux c'est : un mot fréquent ET proche de ce qui a été tapé. */
    val score: Float get() = ln(1f + freq) - COST_WEIGHT * cost

    companion object {
        const val COST_WEIGHT = 3.2f
    }
}

/**
 * Ce que la barre de suggestions affiche pendant la frappe : 3 cases.
 * `middle` est ce qu'écrira l'espace (en surbrillance quand c'est une correction).
 */
data class Strip(
    val left: String? = null,
    val middle: String? = null,
    val right: String? = null,
    /** La case du milieu est une correction qui sera appliquée à l'espace. */
    val middleIsCorrection: Boolean = false,
    /** La case de gauche est le mot tel que tapé (affiché entre guillemets). */
    val leftIsLiteral: Boolean = false,
) {
    val isEmpty get() = left == null && middle == null && right == null

    companion object {
        val Empty = Strip()
    }
}

/** Mots connus en plus du dictionnaire : mots appris, noms des contacts. */
fun interface ExtraWords {
    /** Poids du mot (≥ 1 s'il est connu), 0 sinon. Clé en minuscules. */
    fun weight(lower: String): Int
}

/**
 * La correction automatique, pensée pour être PRUDENTE :
 * - on ne touche jamais à un mot connu (dictionnaire, mots appris, contacts, formes familières) ;
 * - les élisions sont gérées : « m'ennuie » = « m' » + « ennuie », deux morceaux connus ;
 * - un accent ou une apostrophe oubliés se corrigent tout seuls (« deja » → « déjà », « jai » → « j'ai ») ;
 * - sinon, on ne corrige que si un mot fréquent est vraiment proche (touches voisines sur l'AZERTY,
 *   lettre doublée, lettres inversées…) et nettement meilleur que les autres.
 *
 * Analogie Python : c'est un `difflib.get_close_matches` pondéré par la fréquence des mots.
 */
class Corrector(
    private val dict: Dictionary,
    private val extra: ExtraWords = ExtraWords { 0 },
    private val extraList: () -> Collection<String> = { emptyList() },
) {
    private val distance = Distance()

    // ─── Mot connu ? ──────────────────────────────────────────────────────────

    fun isKnown(token: String): Boolean {
        if (token.length <= 1) return true
        if (token.any { it.isDigit() }) return true
        // Sigle tout en majuscules (SNCF, ESB…).
        if (token.all { !it.isLetter() || it.isUpperCase() }) return true
        val (_, rest) = Words.splitElision(token)
        return isKnownWord(rest)
    }

    private fun isKnownWord(word: String): Boolean {
        if (word.isEmpty()) return true
        val lower = word.lowercase().replace('’', '\'')
        if (dict.contains(lower) || extra.weight(lower) > 0) return true
        // Mots composés : « dis-moi », « va-t-il »… connus si chaque morceau l'est.
        if ('-' in lower) {
            val parts = lower.split('-')
            if (parts.all { it.isEmpty() || it == "t" || dict.contains(it) || extra.weight(it) > 0 }) return true
        }
        return false
    }

    // ─── Candidats ────────────────────────────────────────────────────────────

    /** Les mots proches de `typed` (sans élision), du meilleur au moins bon. */
    @Synchronized
    fun corrections(typed: String, limit: Int = 3): List<Candidate> {
        val t = Words.fold(typed)
        if (t.length < 2 || t.length > Distance.MAX_LEN) return emptyList()
        val maxCost = when {
            t.length <= 3 -> 1.0f
            t.length <= 5 -> 1.5f
            else -> 2.0f
        }
        val lowerTyped = typed.lowercase()
        val found = HashMap<String, Candidate>()
        fun offer(word: String, cost: Float, freq: Int) {
            if (word == lowerTyped) return
            val old = found[word]
            if (old == null || old.cost > cost) found[word] = Candidate(word, cost, freq)
        }

        // 0. Expressions collées très courantes à l'écrit rapide.
        GLUED[t]?.let { offer(it, ACCENT_COST, 50_000_000) }

        // 1. Accents oubliés : même mot sans accents.
        for (i in dict.sameFolded(t)) offer(dict.word(i), ACCENT_COST, dict.frequency(i))

        // 2. Apostrophe oubliée : « jai » → « j'ai », « daccord » → « d'accord », « nimporte » → « n'importe ».
        for (e in Words.elisions) {
            val bare = e.dropLast(1)
            if (t.length > bare.length + 1 && t.startsWith(bare)) {
                val rest = t.substring(bare.length)
                if (rest[0] in "aeiouyh") {
                    val i = bestFolded(rest) ?: continue
                    offer(e + dict.word(i), APOSTROPHE_COST, dict.frequency(i))
                }
            }
        }

        // 3. Fautes de frappe : on compare aux tiroirs proches (première lettre et longueur voisines).
        val firsts = buildSet {
            add(t[0])
            addAll(Keyboard.neighbors(t[0]))
        }
        for (first in firsts) {
            for (len in (t.length - 2)..(t.length + 2)) {
                if (len < 1) continue
                for (i in dict.bucket(first, len)) {
                    val w = dict.word(i)
                    val d = distance.compute(t, w, maxCost)
                    if (d <= maxCost) offer(w, if (first == t[0]) d else maxOf(d, 0.5f), dict.frequency(i))
                }
            }
        }

        // 4. Deux mots collés : « quelquechose » → « quelque chose ».
        if (t.length >= 5 && t.all { it.isLetter() }) {
            for (i in 2..t.length - 2) {
                val a = bestFolded(t.substring(0, i)) ?: continue
                val b = bestFolded(t.substring(i)) ?: continue
                val fa = dict.frequency(a)
                val fb = dict.frequency(b)
                if (fa >= SPLIT_MIN_FREQ && fb >= SPLIT_MIN_FREQ) offer(dict.word(a) + " " + dict.word(b), SPLIT_COST, minOf(fa, fb))
            }
        }

        // 5. Mots appris et contacts.
        for (w in extraList()) {
            val d = distance.compute(t, Words.fold(w), maxCost)
            if (d <= maxCost) offer(w, d, 50 * extra.weight(w))
        }
        return found.values.sortedByDescending { it.score }.take(limit)
    }

    /** Le mot le plus fréquent qui s'écrit comme `folded` aux accents près. */
    private fun bestFolded(folded: String): Int? = dict.sameFolded(folded).maxByOrNull { dict.frequency(it) }

    /** La décision « on corrige ou pas », à partir des candidats déjà calculés. */
    private fun decide(rest: String, cands: List<Candidate>): String? {
        val best = cands.firstOrNull() ?: return null
        val second = cands.getOrNull(1)
        val n = rest.length
        val limit = when {
            n <= 2 -> 0.2f      // deux lettres : seulement un accent (« ca » → « ça »)
            n == 3 -> 0.6f
            n <= 5 -> 1.0f
            n <= 8 -> 1.5f
            else -> 2.0f
        }
        val sure = best.cost <= APOSTROPHE_COST
        val confident = best.cost <= limit && best.freq >= MIN_FREQ &&
            (sure || second == null || best.score - second.score >= MARGIN)
        return if (confident) best.word else null
    }

    private fun correctable(typed: String) = !isKnown(typed) && typed.all { Words.isWordChar(it) }

    /**
     * Le mot à écrire à la place de `typed` quand on tape espace ou ponctuation, ou null pour ne rien
     * toucher. La casse et l'élision sont conservées (« M'ennuei » → « M'ennuie »).
     */
    fun autoCorrect(typed: String): String? {
        if (!correctable(typed)) return null
        val (prefix, rest) = Words.splitElision(typed)
        if (rest.length < 2) return null
        val fixed = decide(rest, corrections(rest, limit = 2)) ?: return null
        return prefix + Words.matchCase(rest, fixed)
    }

    /** Les mots les plus fréquents qui commencent par `typed` (élision gardée devant). */
    fun completions(typed: String, limit: Int = 3): List<String> {
        val (prefix, rest) = Words.splitElision(typed)
        if (rest.length < 2) return emptyList()
        val lower = rest.lowercase()
        return dict.completions(rest, limit + 1)
            .map { dict.word(it) }
            .filter { it != lower && it.length > rest.length }
            .take(limit)
            .map { prefix + Words.matchCase(rest, it) }
    }

    /** Les 3 cases de la barre pendant qu'on tape `typed` (et ce que l'espace écrira). */
    fun strip(typed: String): Strip {
        if (typed.isEmpty()) return Strip.Empty
        val (prefix, rest) = Words.splitElision(typed)
        val cands = if (correctable(typed) && rest.length >= 2) corrections(rest, limit = 3) else emptyList()
        val auto = decide(rest, cands)?.let { prefix + Words.matchCase(rest, it) }
        val others = LinkedHashSet<String>()
        cands.forEach { others.add(prefix + Words.matchCase(rest, it.word)) }
        completions(typed, 3).forEach { others.add(it) }
        others.remove(typed)
        if (auto != null) others.remove(auto)
        val list = others.toList()
        return if (auto != null) {
            Strip(left = typed, middle = auto, right = list.getOrNull(0), middleIsCorrection = true, leftIsLiteral = true)
        } else {
            Strip(left = list.getOrNull(0), middle = typed, right = list.getOrNull(1))
        }
    }

    companion object {
        const val ACCENT_COST = 0.05f
        const val APOSTROPHE_COST = 0.25f
        const val MIN_FREQ = 5
        const val MARGIN = 0.9f
        private const val SPLIT_COST = 0.4f

        /** Écrit collé → expression (quand le découpage automatique se tromperait d'accent). */
        private val GLUED = mapOf(
            "biensur" to "bien sûr", "bienssur" to "bien sûr", "sava" to "ça va", "cava" to "ça va",
            "dacord" to "d'accord", "dac" to "dac", "aplus" to "à plus", "atout" to "atout",
            "toutafait" to "tout à fait", "tantpis" to "tant pis", "peutetre" to "peut-être",
        ).filter { it.key != it.value }
        private const val SPLIT_MIN_FREQ = 3000

        /** Distance entre deux mots (voir [Distance]). Pour les tests. */
        fun distance(typed: String, word: String, max: Float): Float = Distance().compute(Words.fold(typed), word, max)
    }
}

/**
 * Distance entre deux mots, en « fautes » pondérées (sur les formes sans accents) :
 * touche voisine 0,55 · autre lettre 1 · lettre en trop / en moins 0,9 (0,45 si doublée,
 * 0,25 pour une apostrophe ou un trait d'union) · deux lettres inversées 0,6.
 *
 * Les tableaux de calcul sont réutilisés d'un mot à l'autre (des dizaines de milliers de
 * comparaisons à chaque lettre : pas question de créer des tableaux neufs à chaque fois).
 */
class Distance {
    private var prev2 = FloatArray(MAX_LEN + 2)
    private var prev = FloatArray(MAX_LEN + 2)
    private var cur = FloatArray(MAX_LEN + 2)
    private val folded = CharArray(MAX_LEN + 2)

    /** @param typed déjà sans accents (Words.fold). Renvoie une valeur > max dès qu'on dépasse `max`. */
    fun compute(typed: String, word: String, max: Float): Float {
        val n = typed.length
        val m = word.length
        if (m > MAX_LEN || n > MAX_LEN) return max + 1f
        if (abs(n - m) * 0.25f > max) return max + 1f
        for (j in 0 until m) folded[j] = Words.fold(word[j])
        var p2 = prev2
        var p = prev
        var c = cur
        p[0] = 0f
        for (j in 1..m) p[j] = p[j - 1] + insertCost(j - 1)
        for (i in 1..n) {
            val ca = typed[i - 1]
            val del = deleteCost(typed, i - 1)
            c[0] = p[0] + del
            var rowMin = c[0]
            for (j in 1..m) {
                val cb = folded[j - 1]
                var best = p[j - 1] + substituteCost(ca, cb)
                val d = p[j] + del
                if (d < best) best = d
                val ins = c[j - 1] + insertCost(j - 1)
                if (ins < best) best = ins
                if (i > 1 && j > 1 && ca == folded[j - 2] && typed[i - 2] == cb && ca != cb) {
                    val tr = p2[j - 2] + TRANSPOSE
                    if (tr < best) best = tr
                }
                c[j] = best
                if (best < rowMin) rowMin = best
            }
            if (rowMin > max) return max + 1f
            val tmp = p2
            p2 = p
            p = c
            c = tmp
        }
        prev2 = p2; prev = p; cur = c
        return p[m]
    }

    private fun substituteCost(a: Char, b: Char): Float = when {
        a == b -> 0f
        Keyboard.adjacent(a, b) -> 0.55f
        else -> 1f
    }

    /** Lettre tapée en trop dans `typed` (position i). */
    private fun deleteCost(typed: String, i: Int): Float {
        val ch = typed[i]
        return when {
            ch == '\'' || ch == '-' -> 0.25f
            (i > 0 && typed[i - 1] == ch) || (i + 1 < typed.length && typed[i + 1] == ch) -> 0.45f
            else -> 0.9f
        }
    }

    /** Lettre oubliée (présente dans le mot du dictionnaire à la position j). */
    private fun insertCost(j: Int): Float {
        val ch = folded[j]
        return when {
            ch == '\'' || ch == '-' -> 0.25f
            j > 0 && folded[j - 1] == ch -> 0.45f
            else -> 0.9f
        }
    }

    companion object {
        const val MAX_LEN = 40
        private const val TRANSPOSE = 0.6f
    }
}

/** La géographie du clavier AZERTY : quelles touches sont voisines. */
object Keyboard {
    private val centers: Map<Char, Pair<Float, Float>> = buildMap {
        "azertyuiop".forEachIndexed { i, c -> put(c, i + 0.5f to 0f) }
        "qsdfghjklm".forEachIndexed { i, c -> put(c, i + 0.5f to 1f) }
        "wxcvbn".forEachIndexed { i, c -> put(c, i + 2f to 2f) }
    }

    /** Voisinage précalculé pour les 26 lettres (a–z). */
    private val adjacency: Array<BooleanArray> = Array(26) { a ->
        BooleanArray(26) { b ->
            val pa = centers['a' + a]
            val pb = centers['a' + b]
            pa != null && pb != null && a != b && abs(pa.second - pb.second) <= 1f && abs(pa.first - pb.first) <= 1.01f
        }
    }

    fun adjacent(a: Char, b: Char): Boolean {
        val i = a - 'a'
        val j = b - 'a'
        return i in 0..25 && j in 0..25 && adjacency[i][j]
    }

    fun neighbors(c: Char): List<Char> = ('a'..'z').filter { adjacent(c, it) }
}
