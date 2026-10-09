package com.atelierjlg.fern.common

import java.text.Normalizer

/*
 * Numéros de téléphone et recherche de texte : fonctions pures (testées dans theme/src/test),
 * partagées par Fern Contact et Fern Messages.
 */
object PhoneNumbers {
    /** Garde les chiffres (et un « + » au début) : « 06 12-34.56 78 » → « 0612345678 ». */
    fun digits(raw: String): String {
        val trimmed = raw.trim()
        val plus = trimmed.startsWith("+")
        val d = trimmed.filter { it.isDigit() }
        return if (plus) "+$d" else d
    }

    /**
     * Clé de comparaison : les 9 derniers chiffres. Ainsi « +33 6 12 34 56 78 »,
     * « 0033612345678 » et « 06 12 34 56 78 » désignent le même numéro.
     */
    fun key(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return if (d.length > 9) d.takeLast(9) else d
    }

    /** Deux écritures du même numéro ? (Les numéros courts, comme 3631, doivent être identiques.) */
    fun same(a: String, b: String): Boolean {
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        if (da.length < 7 || db.length < 7) return da == db
        return key(da) == key(db)
    }

    /**
     * Affichage à la française : « +33612345678 » → « 06 12 34 56 78 ».
     * Numéros étrangers : « +44 20 7946 0958 » reste groupé simplement.
     */
    fun format(raw: String): String {
        val d = digits(raw)
        val national = when {
            d.startsWith("+33") && d.length == 12 -> "0" + d.drop(3)
            d.startsWith("0033") && d.length == 13 -> "0" + d.drop(4)
            else -> d
        }
        if (national.length == 10 && national.startsWith("0")) return national.chunked(2).joinToString(" ")
        if (national.startsWith("+") && national.length > 6) {
            val body = national.drop(1)
            val cc = countryCodeLength(body)
            return "+" + body.take(cc) + " " + body.drop(cc).chunked(3).joinToString(" ")
        }
        return raw.trim().ifEmpty { d }
    }

    /** Longueur de l'indicatif pays (1 = États-Unis, 2 = France 33, 3 = Portugal 351…). */
    private fun countryCodeLength(body: String): Int {
        val two = body.take(2).toIntOrNull() ?: return 2
        return when (body.first()) {
            '1', '7' -> 1
            '2' -> if (two == 20 || two == 27) 2 else 3
            '3' -> if (two in listOf(35, 37, 38)) 3 else 2
            '4' -> if (two == 42) 3 else 2
            '5' -> if (two == 50 || two == 59) 3 else 2
            '6' -> if (two >= 67) 3 else 2
            '8' -> if (two in listOf(81, 82, 84, 86)) 2 else 3
            '9' -> if (two in 90..95 || two == 98) 2 else 3
            else -> 2
        }
    }

    /** Le clavier tape-t-il un morceau de ce numéro ? (« 0612 » trouve « 06 12 34 56 78 » et « +33 6 12… ».) */
    fun containsDigits(number: String, typed: String): Boolean {
        val t = typed.filter { it.isDigit() }
        if (t.isEmpty()) return false
        val d = number.filter { it.isDigit() }
        if (d.contains(t)) return true
        // « 06… » tapé pour un numéro enregistré en « +33 6… »
        val national = when {
            d.startsWith("33") && d.length == 11 -> "0" + d.drop(2)
            d.startsWith("0033") -> "0" + d.drop(4)
            else -> return false
        }
        return national.contains(t)
    }
}

object SearchText {
    /** Minuscules sans accents : « Éloïse » → « eloise ». */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()

    /** Tous les mots de la recherche apparaissent dans le texte (dans n'importe quel ordre). */
    fun matches(haystack: String, query: String): Boolean {
        val h = fold(haystack)
        return fold(query).split(' ').filter { it.isNotBlank() }.all { h.contains(it) }
    }

    /** La lettre de la section A–Z : « Éloïse » → « E », « 3 Suisses » → « # ». */
    fun initial(name: String): String {
        val c = fold(name.trim()).firstOrNull() ?: return "#"
        return if (c in 'a'..'z') c.uppercase() else "#"
    }

    private val T9 = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2', 'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4', 'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6', 'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8', 'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
    )

    /** Les touches du clavier pour un mot : « Jules » → « 58537 ». */
    fun t9(word: String): String = fold(word).mapNotNull { T9[it] ?: it.takeIf { c -> c.isDigit() } }.joinToString("")

    /** « 528 » trouve « Jules Martin » (début d'un des mots du nom, comme sur les vieux téléphones). */
    fun t9Matches(name: String, typed: String): Boolean {
        if (typed.length < 2 || typed.any { !it.isDigit() }) return false
        return fold(name).split(' ', '-', '\'').any { it.isNotEmpty() && t9(it).startsWith(typed) }
    }
}
