package com.atelierjlg.fern.keyboard.logic

/**
 * Le texte rendu par la dictée (Whisper), avant de l'écrire dans le champ :
 * - on retire les annotations de sous-titres que Whisper ajoute parfois (« [Musique] », « (rires) »,
 *   « Sous-titres réalisés par… ») ;
 * - on recolle proprement avec ce qui est déjà écrit (espace, majuscule).
 */
object SpeechText {
    private val ANNOTATIONS = Regex("""\[[^\]]*]|\([^)]*\)|\*[^*]*\*|♪+""")
    /** Les « génériques » inventés par Whisper sur un silence : tout ce qui suit est retiré. */
    private val CREDITS = Regex("""(?is)\s*(sous-titr(es|age)|merci d'avoir regardé|abonnez-vous).*$""")
    private val SPACES = Regex("""\s+""")

    fun clean(raw: String): String {
        var t = raw.replace(ANNOTATIONS, " ").replace(CREDITS, " ")
        t = t.replace(SPACES, " ").trim()
        // Pas d'espace avant la ponctuation simple ; une espace avant « ? ! : ; » serait à la française,
        // mais le reste du clavier écrit sans : on reste cohérent.
        t = t.replace(Regex("""\s+([,.])"""), "$1")
        return t
    }

    /**
     * Le texte à insérer, selon ce qui précède le curseur : une espace si on colle à un mot,
     * une majuscule en début de phrase, une minuscule au milieu d'une phrase.
     */
    fun forInsertion(text: String, before: CharSequence): String {
        if (text.isEmpty()) return text
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace()
        val sentenceStart = TextRules.shouldCapitalize(if (needsSpace) "$before " else before)
        val first = text[0]
        val adjusted = when {
            sentenceStart -> text.replaceFirstChar { it.uppercaseChar() }
            // Au milieu d'une phrase, Whisper met souvent une majuscule : on la retire (sauf « I », sigles…).
            first.isUpperCase() && text.length > 1 && text[1].isLowerCase() -> text.replaceFirstChar { it.lowercaseChar() }
            else -> text
        }
        return if (needsSpace) " $adjusted" else adjusted
    }

    /** PCM 16 bits → nombres entre -1 et 1 (ce que Whisper attend). */
    fun toFloat(samples: ShortArray, count: Int = samples.size): FloatArray =
        FloatArray(count) { samples[it] / 32768f }

    /** Volume (0 à 1) d'un morceau d'audio, pour l'onde à l'écran. */
    fun level(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        val rms = kotlin.math.sqrt(sum / count) / 32768.0
        return (rms * 6).coerceIn(0.0, 1.0).toFloat()
    }
}
