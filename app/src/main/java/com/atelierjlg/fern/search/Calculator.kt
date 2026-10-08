package com.atelierjlg.fern.search

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

/**
 * La calculatrice de la recherche : « 12*3+4 », « (2,5+1)/2 », « 2^10 », « 15% »,
 * « 32*23+45√2 », « 2π », « √(9+16) », « 3² ».
 *
 * C'est un petit « analyseur à descente récursive » : chaque fonction lit un niveau
 * de priorité (addition < multiplication < puissance < nombre/parenthèses).
 */
object Calculator {

    /** Le résultat, ou null si le texte n'est pas un calcul (ex. « whatsapp »). */
    fun evaluate(input: String): Double? {
        val text = normalize(input)
        // Il faut un opérateur (ou √ / π), et au moins un chiffre ou π : « 42 » seul n'est pas un calcul.
        if (text.isEmpty() || text.none { it in "+-*/^%√π" } || text.none { it.isDigit() || it == 'π' }) return null
        if (text.any { it !in "0123456789.+-*/^%()√π" }) return null
        return try {
            val parser = Parser(text)
            val value = parser.expression()
            if (!parser.atEnd() || value.isNaN() || value.isInfinite()) null else value
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Met le texte au format de l'analyseur : « 45 √2 × π² » → « 45√2*π^2 ».
     * Mots acceptés : sqrt / racine (→ √), pi (→ π). Exposants ² et ³.
     */
    fun normalize(input: String): String = input
        .lowercase()
        .replace(" ", "")
        .replace("sqrt", "√").replace("racine", "√")
        .replace("pi", "π")
        .replace("²", "^2").replace("³", "^3")
        .replace(',', '.')
        .replace('×', '*').replace('x', '*').replace('÷', '/').replace(':', '/')
        .replace('−', '-')

    /** Affiche un résultat proprement : « 42 », « 3,14159 », jamais « 42.0 ». */
    fun format(value: Double): String {
        val rounded = BigDecimal(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = rounded.toPlainString()
        return (if (plain == "-0") "0" else plain).replace('.', ',')
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun atEnd() = pos >= s.length

        private fun peek(): Char? = s.getOrNull(pos)

        private fun eat(c: Char): Boolean {
            if (peek() == c) {
                pos++
                return true
            }
            return false
        }

        // expression := terme (('+' | '-') terme)*
        fun expression(): Double {
            var value = term()
            while (true) {
                value = when {
                    eat('+') -> value + term()
                    eat('-') -> value - term()
                    else -> return value
                }
            }
        }

        // terme := puissance (('*' | '/' | rien) puissance)*
        // « rien » = multiplication implicite : 45√2, 2π, 3(1+2), (1+2)(3+4).
        private fun term(): Double {
            var value = power()
            while (true) {
                value = when {
                    eat('*') -> value * power()
                    eat('/') -> value / power()
                    peek()?.let { it in "(√π0123456789." } == true -> value * power()
                    else -> return value
                }
            }
        }

        // puissance := unaire ('^' puissance)?
        private fun power(): Double {
            val base = unary()
            return if (eat('^')) base.pow(power()) else base
        }

        // unaire := ('-' | '+' | '√') unaire | facteur '%'?
        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            eat('√') -> kotlin.math.sqrt(unary())
            else -> {
                val v = factor()
                if (eat('%')) v / 100.0 else v
            }
        }

        // facteur := nombre | π | '(' expression ')'
        private fun factor(): Double {
            if (eat('π')) return Math.PI
            if (eat('(')) {
                val v = expression()
                require(eat(')')) { "parenthèse manquante" }
                return v
            }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            require(pos > start) { "nombre attendu" }
            return s.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("nombre invalide")
        }
    }
}
