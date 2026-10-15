package com.atelierjlg.fern.keyboard.logic

/**
 * Les dispositions du clavier, écrites comme des données (un peu comme un tableau HTML) :
 * des rangées de touches, chaque touche avec sa largeur et ses variantes à l'appui long.
 * Aucun code Android ici : tout est testable hors du téléphone.
 */

/** Les « pages » du clavier. */
enum class Page { Lettres, Symboles, Symboles2, Numerique }

/** Ce que fait une touche quand on la touche. */
sealed interface KeyAction {
    /** Écrit un texte (une lettre, un signe…). */
    data class Text(val text: String) : KeyAction
    data object Shift : KeyAction
    data object Delete : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    /** Change de page (lettres ↔ symboles). */
    data class GoTo(val page: Page) : KeyAction
}

data class Key(
    /** Ce qui est affiché (vide pour les touches à picto : majuscule, effacer, entrée). */
    val label: String,
    val action: KeyAction,
    /** Largeur, en « largeurs de lettre » : chaque rangée fait 10 (4 pour le pavé numérique). */
    val width: Float = 1f,
    /** Variantes proposées à l'appui long (la première est choisie par défaut). */
    val popup: List<String> = emptyList(),
    /** Petit signe affiché dans le coin (le chiffre des lettres du haut, sans rangée de chiffres). */
    val hint: String? = null,
    /** Touche « de fonction » (dessinée en couleur secondaire). */
    val special: Boolean = false,
)

object Layouts {
    /** Les accents du français à l'appui long, du plus courant au plus rare. */
    val accents: Map<Char, List<String>> = mapOf(
        'e' to listOf("é", "è", "ê", "ë", "€"),
        'a' to listOf("à", "â", "æ", "ä"),
        'c' to listOf("ç", "©"),
        'i' to listOf("î", "ï", "ì"),
        'o' to listOf("ô", "œ", "ö", "ò", "ó"),
        'u' to listOf("ù", "û", "ü"),
        'y' to listOf("ÿ"),
        'n' to listOf("ñ"),
        's' to listOf("ß"),
    )

    private val digits = "1234567890"

    private fun letter(c: Char, digit: Char? = null): Key {
        val variants = accents[c].orEmpty()
        return Key(
            label = c.toString(),
            action = KeyAction.Text(c.toString()),
            popup = if (digit != null) variants + digit.toString() else variants,
            hint = digit?.toString(),
        )
    }

    private fun text(s: String, popup: List<String> = emptyList(), width: Float = 1f, special: Boolean = false) =
        Key(s, KeyAction.Text(s), width = width, popup = popup, special = special)

    private val shift = Key("", KeyAction.Shift, width = 1.5f, special = true)
    private val delete = Key("", KeyAction.Delete, width = 1.5f, special = true)
    private fun enter(width: Float = 1.5f) = Key("", KeyAction.Enter, width = width, special = true)
    private val space = Key(" ", KeyAction.Space, width = 5f)
    private val comma = text(",", popup = listOf(";", ":", "!", "?"), special = true)
    private val period = text(".", popup = listOf("…", "!", "?", "-", "'", "\""), special = true)

    /** Rangée de chiffres (option « rangée de chiffres »). */
    private val numberRow = digits.map { d ->
        Key(d.toString(), KeyAction.Text(d.toString()), popup = digitPopup(d))
    }

    private fun digitPopup(d: Char): List<String> = when (d) {
        '1' -> listOf("¹", "½", "¼")
        '2' -> listOf("²")
        '3' -> listOf("³", "¾")
        '0' -> listOf("°", "∅")
        else -> emptyList()
    }

    /**
     * Les rangées d'une page.
     * @param numberRow ajoute une rangée de chiffres au-dessus des lettres
     *   (sinon, les chiffres sont à l'appui long sur la rangée du haut).
     */
    fun rows(page: Page, numberRow: Boolean = false): List<List<Key>> = when (page) {
        Page.Lettres -> buildList {
            if (numberRow) add(this@Layouts.numberRow)
            add("azertyuiop".mapIndexed { i, c -> letter(c, if (numberRow) null else digits[i]) })
            add("qsdfghjklm".map { letter(it) })
            add(listOf(shift) + "wxcvbn".map { letter(it) } + text("'", popup = listOf("’", "\"", "«", "»")) + delete)
            add(listOf(Key("?123", KeyAction.GoTo(Page.Symboles), 1.5f, special = true), comma, space, period, enter()))
        }
        Page.Symboles -> listOf(
            digits.map { d -> Key(d.toString(), KeyAction.Text(d.toString()), popup = digitPopup(d)) },
            listOf(
                text("@"), text("#"), text("€", listOf("$", "£", "¥", "¢")), text("%", listOf("‰")), text("&"),
                text("-", listOf("–", "—", "·", "_")), text("+", listOf("±")), text("(", listOf("[", "{", "<")),
                text(")", listOf("]", "}", ">")), text("/", listOf("\\", "|")),
            ),
            listOf(Key("=\\<", KeyAction.GoTo(Page.Symboles2), 1.5f, special = true)) +
                listOf(
                    text("*", listOf("×", "•")), text("\"", listOf("«", "»", "“", "”")), text("'", listOf("’", "‘")),
                    text(":"), text(";"), text("!", listOf("¡")), text("?", listOf("¿")),
                ) + delete,
            listOf(Key("ABC", KeyAction.GoTo(Page.Lettres), 1.5f, special = true), comma, space, period, enter()),
        )
        Page.Symboles2 -> listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { text(it) },
            listOf("£", "¥", "$", "¢", "^", "°", "=", "{", "}", "\\").map { text(it) },
            listOf(Key("?123", KeyAction.GoTo(Page.Symboles), 1.5f, special = true)) +
                listOf("_", "©", "®", "™", "✓", "[", "]").map { text(it) } + delete,
            listOf(Key("ABC", KeyAction.GoTo(Page.Lettres), 1.5f, special = true), comma, space, period, enter()),
        )
        // Pavé pour les numéros de téléphone et les nombres : 4 colonnes.
        Page.Numerique -> listOf(
            listOf(text("1"), text("2"), text("3"), text("-", listOf("+", "*", "#", "(", ")", "/"), special = true)),
            listOf(text("4"), text("5"), text("6"), text(".", listOf(",", ":"), special = true)),
            listOf(text("7"), text("8"), text("9"), Key("", KeyAction.Delete, special = true)),
            listOf(
                Key("ABC", KeyAction.GoTo(Page.Lettres), special = true),
                text("0", listOf("+")),
                Key(" ", KeyAction.Space),
                enter(width = 1f),
            ),
        )
    }
}
