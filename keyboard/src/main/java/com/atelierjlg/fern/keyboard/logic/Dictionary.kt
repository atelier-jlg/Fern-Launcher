package com.atelierjlg.fern.keyboard.logic

import java.io.BufferedReader
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Le dictionnaire français du clavier (≈ 358 000 mots) avec la fréquence de chaque mot dans des
 * sous-titres de films (= le français parlé). Construit par tools/build_dictionary.py.
 *
 * Les mots sont triés par leur forme « sans accents » : on peut donc chercher par dichotomie
 * (comme dans un vrai dictionnaire papier) un mot, ses variantes accentuées, ou les mots qui
 * commencent par quelques lettres.
 *
 * Pour la correction, on range aussi les mots « vivants » (fréquence > 0) dans des tiroirs
 * (première lettre, longueur) : on ne compare le mot tapé qu'aux tiroirs proches.
 */
class Dictionary(private val words: Array<String>, private val freqs: IntArray) {
    val size get() = words.size

    fun word(i: Int) = words[i]
    fun frequency(i: Int) = freqs[i]

    private val buckets: Map<Int, IntArray> = buildBuckets()

    private fun bucketKey(first: Char, length: Int) = (first.code shl 6) or length.coerceAtMost(63)

    private fun buildBuckets(): Map<Int, IntArray> {
        val counts = HashMap<Int, Int>()
        for (i in words.indices) {
            if (freqs[i] <= 0) continue
            val k = bucketKey(Words.fold(words[i][0]), words[i].length)
            counts[k] = (counts[k] ?: 0) + 1
        }
        val arrays = HashMap<Int, IntArray>(counts.size)
        val fill = HashMap<Int, Int>(counts.size)
        counts.forEach { (k, n) -> arrays[k] = IntArray(n); fill[k] = 0 }
        for (i in words.indices) {
            if (freqs[i] <= 0) continue
            val k = bucketKey(Words.fold(words[i][0]), words[i].length)
            val pos = fill.getValue(k)
            arrays.getValue(k)[pos] = i
            fill[k] = pos + 1
        }
        return arrays
    }

    /** Les mots « vivants » commençant (sans accent) par `first` et de longueur `length`. */
    fun bucket(first: Char, length: Int): IntArray = buckets[bucketKey(Words.fold(first), length)] ?: EMPTY

    /** Compare le mot n° i à une forme pliée (sans accents), comme le tri du fichier. */
    private fun compareFolded(i: Int, folded: String, prefixOnly: Boolean): Int {
        val w = words[i]
        val n = minOf(w.length, folded.length)
        for (k in 0 until n) {
            val d = Words.fold(w[k]) - folded[k]
            if (d != 0) return d
        }
        if (prefixOnly && w.length >= folded.length) return 0
        return w.length - folded.length
    }

    /** Premier indice dont la forme pliée est ≥ folded (dichotomie). */
    private fun lowerBound(folded: String): Int {
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareFolded(mid, folded, prefixOnly = false) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Tous les mots qui s'écrivent pareil sans accents (« ete » → « été », « êtes »…). */
    fun sameFolded(word: String): List<Int> {
        val folded = Words.fold(word)
        val out = ArrayList<Int>(4)
        var i = lowerBound(folded)
        while (i < words.size && compareFolded(i, folded, prefixOnly = false) == 0) {
            out.add(i)
            i++
        }
        return out
    }

    /** Fréquence du mot (insensible à la casse), ou -1 s'il est inconnu. */
    fun frequencyOf(word: String): Int {
        val lower = word.lowercase().replace('’', '\'')
        for (i in sameFolded(lower)) if (words[i] == lower) return freqs[i]
        return -1
    }

    fun contains(word: String) = frequencyOf(word) >= 0

    /** Les mots les plus fréquents qui commencent par `prefix` (sans tenir compte des accents). */
    fun completions(prefix: String, limit: Int): List<Int> {
        val folded = Words.fold(prefix)
        if (folded.isEmpty()) return emptyList()
        var i = lowerBound(folded)
        // Garde les `limit` meilleurs au fil de l'eau (la plage peut être longue pour un début court).
        val best = ArrayList<Int>(limit + 1)
        var scanned = 0
        while (i < words.size && compareFolded(i, folded, prefixOnly = true) == 0 && scanned < MAX_SCAN) {
            if (freqs[i] > 0) {
                val pos = best.indexOfFirst { freqs[it] < freqs[i] }.let { if (it < 0) best.size else it }
                if (pos < limit) {
                    best.add(pos, i)
                    if (best.size > limit) best.removeAt(best.lastIndex)
                }
            }
            i++
            scanned++
        }
        return best
    }

    companion object {
        private val EMPTY = IntArray(0)
        private const val MAX_SCAN = 40_000

        /** Lit le fichier « mot<TAB>fréquence » (déjà trié), compressé en gzip. */
        fun load(input: InputStream): Dictionary {
            val words = ArrayList<String>(370_000)
            val freqs = ArrayList<Int>(370_000)
            GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { reader: BufferedReader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    words.add(line.substring(0, tab))
                    freqs.add(line.substring(tab + 1).toIntOrNull() ?: 0)
                }
            }
            return Dictionary(words.toTypedArray(), freqs.toIntArray())
        }

        /** Petit dictionnaire pour les tests (trié comme le vrai). */
        fun of(vararg entries: Pair<String, Int>): Dictionary {
            val sorted = entries.sortedWith(compareBy<Pair<String, Int>> { Words.fold(it.first) }.thenBy { it.first })
            return Dictionary(sorted.map { it.first }.toTypedArray(), sorted.map { it.second }.toIntArray())
        }
    }
}
