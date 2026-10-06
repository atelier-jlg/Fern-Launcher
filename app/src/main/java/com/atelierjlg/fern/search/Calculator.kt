package com.atelierjlg.fern.search

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

/**
 * La calculatrice de la recherche : « 12*3+4 », « (2,5+1)/2 », « 2^10 », « 15% ».
 *
 * C'est un petit « analyseur à descente récursive » : chaque fonction lit un niveau
 * de priorité (addition < multiplication < puissance < nombre/parenthèses).
 */
object Calculator {

    /** Le résultat, ou null si le texte n'est pas un calcul (ex. « whatsapp »). */
    fun evaluate(input: String): Double? {
        val text = input
            .replace(" ", "")
            .replace(',', '.')
            .replace('×', '*').replace('x', '*').replace('÷', '/').replace(':', '/')
            .replace('−', '-')
        // Il faut au moins un opérateur entre deux nombres, sinon ce n'est pas un calcul.
        if (text.isEmpty() || text.none { it in "+-*/^%" } || text.none { it.isDigit() }) return null
        if (text.any { it !in "0123456789.+-*/^%()" }) return null
        return try {
            val parser = Parser(text)
            val value = parser.expression()
            if (!parser.atEnd() || value.isNaN() || value.isInfinite()) null else value
        } catch (e: IllegalArgumentException) {
            null
        }
    }

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

        // terme := puissance (('*' | '/') puissance)*
        private fun term(): Double {
            var value = power()
            while (true) {
                value = when {
                    eat('*') -> value * power()
                    eat('/') -> value / power()
                    else -> return value
                }
            }
        }

        // puissance := unaire ('^' puissance)?
        private fun power(): Double {
            val base = unary()
            return if (eat('^')) base.pow(power()) else base
        }

        // unaire := ('-' | '+') unaire | facteur '%'?
        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> {
                val v = factor()
                if (eat('%')) v / 100.0 else v
            }
        }

        // facteur := nombre | '(' expression ')'
        private fun factor(): Double {
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
