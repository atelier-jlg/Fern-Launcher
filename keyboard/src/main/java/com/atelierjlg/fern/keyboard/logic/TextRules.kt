package com.atelierjlg.fern.keyboard.logic

/** État de la touche majuscule. */
enum class ShiftMode {
    /** Minuscules. */
    Off,
    /** Une seule majuscule (la prochaine lettre), puis retour en minuscules. */
    Once,
    /** Verrouillé en majuscules (double toucher). */
    Locked,
}

/**
 * Les petites règles d'écriture du clavier : majuscule auto, point auto, touche majuscule.
 * Fonctions pures : on leur donne du texte, elles répondent oui/non. Testées dans TextRulesTest.
 */
object TextRules {
    /** Délai max entre deux touchers de la majuscule pour la verrouiller. */
    const val DOUBLE_TAP_MS = 400L

    private const val SENTENCE_END = ".!?…"

    /**
     * Faut-il une majuscule à la prochaine lettre ?
     * Oui en début de champ, après un retour à la ligne, ou après « . », « ! », « ? », « … » suivi d'un espace.
     * @param before le texte juste avant le curseur (quelques caractères suffisent).
     */
    fun shouldCapitalize(before: CharSequence): Boolean {
        if (before.isEmpty()) return true
        val last = before.last()
        if (last == '\n') return true
        if (!last.isWhitespace()) return false
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return true
        // Un retour à la ligne suivi d'espaces compte aussi comme un début.
        if (before.substring(trimmed.length).contains('\n')) return true
        return trimmed.last() in SENTENCE_END
    }

    /**
     * Deux espaces de suite → « . » : renvoie true si l'espace qu'on vient de taper doit remplacer
     * l'espace précédent par « . ».
     * @param before le texte avant le curseur (l'espace précédent compris).
     * @param lastWasSpace la touche précédente était l'espace (et rien d'autre entre-temps).
     */
    fun doubleSpaceToPeriod(before: CharSequence, lastWasSpace: Boolean): Boolean {
        if (!lastWasSpace || before.length < 2) return false
        if (before.last() != ' ') return false
        val prev = before[before.length - 2]
        return prev.isLetterOrDigit() || prev in ")\"'»’"
    }

    /** Toucher de la majuscule. */
    fun onShiftTap(current: ShiftMode, msSinceLastTap: Long): ShiftMode = when (current) {
        ShiftMode.Off -> ShiftMode.Once
        ShiftMode.Once -> if (msSinceLastTap <= DOUBLE_TAP_MS) ShiftMode.Locked else ShiftMode.Off
        ShiftMode.Locked -> ShiftMode.Off
    }

    /** Après une lettre tapée : la majuscule « une fois » retombe. */
    fun afterLetter(current: ShiftMode): ShiftMode = if (current == ShiftMode.Once) ShiftMode.Off else current

    /** Applique la majuscule à un texte de touche (« é » → « É », « 3 » reste « 3 »). */
    fun applyShift(text: String, shift: ShiftMode): String = if (shift == ShiftMode.Off) text else text.uppercase()
}
