package com.atelierjlg.fern.messages.data

/**
 * Repère un code de vérification dans un SMS (« Votre code : 482913 », « G-123456 est votre code Google »…).
 * Fonction pure, testée : on cherche un mot-clé (code, vérification, OTP…) ET un nombre de 4 à 8 chiffres,
 * pour ne pas confondre avec une date, un montant ou un numéro de téléphone.
 */
object Otp {
    private val KEYWORDS = Regex(
        "(code|verification|vérification|verif|otp|mot de passe|password|passcode|pin|confirm|authenti|connexion|login|sécurité|securite|2fa|token)",
        RegexOption.IGNORE_CASE,
    )

    // 4 à 8 chiffres (éventuellement « 123 456 » ou « 123-456 »), pas collés à d'autres chiffres,
    // ni à un montant (« 1250,00 € »), une heure (« 18h30 ») ou une date (« 12/05 »).
    private val CANDIDATE = Regex("(?<![\\d€$£%/+:])(?<!\\d[.,])(\\d{3}[ -]\\d{3}|\\d{4,8})(?![\\d€$£%/:]|[.,]\\d|h\\d|\\s?€)")
    private val MONTHS = Regex("janv|févr|fevr|mars|avr|mai|juin|juil|août|aout|sept|oct|nov|déc|dec", RegexOption.IGNORE_CASE)

    fun find(text: String): String? {
        if (!KEYWORDS.containsMatchIn(text)) return null
        val candidates = CANDIDATE.findAll(text).map { it.value.filter(Char::isDigit) }
            .filter { it.length in 4..8 }
            // Pas une année (« le 3 mars 2026 ») quand le message parle d'une date.
            .filterNot { it.length == 4 && it.toInt() in 1950..2099 && MONTHS.containsMatchIn(text) }
            .toList()
        return candidates.firstOrNull()
    }
}
