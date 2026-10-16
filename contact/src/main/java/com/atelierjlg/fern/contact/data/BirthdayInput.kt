package com.atelierjlg.fern.contact.data

import java.time.LocalDate

private val MONTH_NAMES = listOf(
    "janv" to 1, "fev" to 2, "fév" to 2, "mars" to 3, "avr" to 4, "mai" to 5, "juin" to 6,
    "juil" to 7, "aou" to 8, "aoû" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12, "déc" to 12,
)

/**
 * Saisie d'un anniversaire, dans à peu près n'importe quel format → format Android
 * « --MM-JJ » (sans l'année) ou « AAAA-MM-JJ ». Null si illisible, "" si vide.
 * Acceptés : 14/05, 14/05/1999, 14-5-99, 14.05.1999, 14 05 1999, 1405, 14051999, 19990514,
 * 1999-05-14, « 14 mai 1999 », « 1er janvier ».
 */
fun parseBirthdayInput(text: String, today: LocalDate = LocalDate.now()): String? {
    val t = text.trim().lowercase()
    if (t.isEmpty()) return ""
    var day: Int
    var month: Int
    var year: Int? = null

    fun fullYear(y: String): Int? = when (y.length) {
        4 -> y.toInt()
        2 -> y.toInt().let { if (it <= today.year % 100) 2000 + it else 1900 + it }
        else -> null
    }

    val withMonthName = Regex("^(\\d{1,2})(?:er)?\\s+([a-zéû]+)\\.?(?:\\s+(\\d{2}|\\d{4}))?$").find(t)
    val iso = Regex("^(\\d{4})[-/. ](\\d{1,2})[-/. ](\\d{1,2})$").find(t)
    val separated = Regex("^(\\d{1,2})[-/. ]+(\\d{1,2})(?:[-/. ]+(\\d{2}|\\d{4}))?$").find(t)
    val digits = Regex("^\\d+$").matches(t)
    when {
        withMonthName != null -> {
            day = withMonthName.groupValues[1].toInt()
            val name = withMonthName.groupValues[2]
            month = MONTH_NAMES.firstOrNull { name.startsWith(it.first) }?.second ?: return null
            withMonthName.groupValues[3].takeIf { it.isNotEmpty() }?.let { year = fullYear(it) }
        }
        iso != null -> {
            year = iso.groupValues[1].toInt()
            month = iso.groupValues[2].toInt()
            day = iso.groupValues[3].toInt()
        }
        separated != null -> {
            day = separated.groupValues[1].toInt()
            month = separated.groupValues[2].toInt()
            separated.groupValues[3].takeIf { it.isNotEmpty() }?.let { year = fullYear(it) }
        }
        digits && t.length == 4 -> {
            day = t.take(2).toInt()
            month = t.drop(2).toInt()
        }
        digits && t.length == 6 -> {
            day = t.take(2).toInt()
            month = t.substring(2, 4).toInt()
            year = fullYear(t.drop(4))
        }
        digits && t.length == 8 -> {
            // AAAAMMJJ si ça commence par 19 ou 20 et que la suite est une date ; sinon JJMMAAAA.
            val y = t.take(4).toInt()
            val m = t.substring(4, 6).toInt()
            val d = t.drop(6).toInt()
            if (y in 1900..2100 && m in 1..12 && d in 1..31) {
                year = y; month = m; day = d
            } else {
                day = t.take(2).toInt(); month = t.substring(2, 4).toInt(); year = t.drop(4).toInt()
            }
        }
        else -> return null
    }
    if (month !in 1..12 || day !in 1..31) return null
    val y = year
    if (y != null) {
        if (y !in 1900..today.year) return null
        if (runCatching { LocalDate.of(y, month, day) }.isFailure) return null
        return "%04d-%02d-%02d".format(y, month, day)
    }
    if (runCatching { LocalDate.of(2000, month, day) }.isFailure) return null
    return "--%02d-%02d".format(month, day)
}
