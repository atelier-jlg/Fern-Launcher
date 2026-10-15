package com.atelierjlg.fern.keyboard.logic

/**
 * Petits outils sur les mots : accents, apostrophes, majuscules.
 * Le pliage des accents (fold) doit rester identique à celui de tools/build_dictionary.py,
 * qui trie le dictionnaire avec.
 */
object Words {
    private val foldMap: Map<Char, Char> = buildMap {
        "àâäáæ".forEach { put(it, 'a') }
        "éèêë".forEach { put(it, 'e') }
        "îïíì".forEach { put(it, 'i') }
        "ôöóòœ".forEach { put(it, 'o') }
        "ùûüú".forEach { put(it, 'u') }
        put('ç', 'c'); put('ÿ', 'y'); put('ñ', 'n'); put('’', '\'')
    }

    /** Table toute prête pour les caractères latins (bien plus rapide qu'une recherche à chaque lettre). */
    private val table = CharArray(0x180) { i ->
        val lower = i.toChar().lowercaseChar()
        foldMap[lower] ?: lower
    }

    /** Minuscule sans accent : « É » → « e », « œ » → « o ». Un caractère donne toujours un caractère. */
    fun fold(c: Char): Char = when {
        c.code < 0x180 -> table[c.code]
        c == '’' -> '\''
        else -> c.lowercaseChar()
    }

    fun fold(s: CharSequence): String = buildString(s.length) { s.forEach { append(fold(it)) } }

    /** Ce qui fait partie d'un mot pendant la frappe (lettres, chiffres, apostrophe, trait d'union). */
    fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-'

    /** Élisions du français, de la plus longue à la plus courte. */
    val elisions = listOf("jusqu'", "lorsqu'", "puisqu'", "quoiqu'", "quelqu'", "qu'", "c'", "d'", "j'", "l'", "m'", "n'", "s'", "t'")

    /**
     * Sépare l'élision du reste : « m'ennuie » → (« m' », « ennuie »), « qu'est-ce » → (« qu' », « est-ce »).
     * Sans élision : (« », mot). L'apostrophe typographique ’ est acceptée.
     */
    fun splitElision(word: String): Pair<String, String> {
        val lower = word.lowercase().replace('’', '\'')
        for (e in elisions) {
            if (lower.startsWith(e) && lower.length > e.length) {
                return word.substring(0, e.length) to word.substring(e.length)
            }
        }
        return "" to word
    }

    /** Recopie la casse du mot tapé : « Bonjoru » → « Bonjour », « SALUT » → « SALUT ». */
    fun matchCase(typed: String, word: String): String = when {
        typed.length >= 2 && typed.all { !it.isLetter() || it.isUpperCase() } && typed.any { it.isLetter() } -> word.uppercase()
        typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    /** Le dernier « mot » à la fin d'un texte (pour reprendre un mot quand on revient dessus). */
    fun trailingWord(text: CharSequence): String {
        var i = text.length
        while (i > 0 && isWordChar(text[i - 1])) i--
        return text.substring(i)
    }
}
