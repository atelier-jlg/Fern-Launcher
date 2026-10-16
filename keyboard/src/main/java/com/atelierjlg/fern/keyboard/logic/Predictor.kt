package com.atelierjlg.fern.keyboard.logic

/**
 * Le mot suivant, proposé après un espace.
 * D'abord ce que Jules écrit souvent (appris), puis une petite liste d'enchaînements courants du
 * français parlé, pour démarrer.
 */
object Predictor {
    /** Mot précédent → suites fréquentes. Écrit à la main, à compléter au fil de l'eau. */
    private val seed: Map<String, List<String>> = mapOf(
        "je" to listOf("suis", "vais", "pense"),
        "j'" to listOf("ai", "arrive", "espère"),
        "tu" to listOf("es", "as", "fais"),
        "t'" to listOf("es", "as", "inquiète"),
        "il" to listOf("y", "est", "faut"),
        "elle" to listOf("est", "a", "va"),
        "on" to listOf("se", "va", "peut"),
        "se" to listOf("voit", "fait", "retrouve"),
        "nous" to listOf("sommes", "avons", "allons"),
        "vous" to listOf("êtes", "avez", "pouvez"),
        "ils" to listOf("sont", "ont", "vont"),
        "c'" to listOf("est", "était"),
        "est" to listOf("ce", "que", "pas"),
        "ça" to listOf("va", "marche", "me"),
        "ca" to listOf("va", "marche"),
        "va" to listOf("bien", "être", "y"),
        "bien" to listOf("sûr", "joué", "reçu"),
        "à" to listOf("demain", "plus", "tout"),
        "a" to listOf("demain", "plus"),
        "de" to listOf("rien", "la", "toute"),
        "pas" to listOf("de", "mal", "grave"),
        "merci" to listOf("beaucoup", "pour", "à"),
        "bonne" to listOf("journée", "soirée", "nuit"),
        "bon" to listOf("courage", "appétit", "week-end"),
        "tout" to listOf("de", "va", "le"),
        "qu'" to listOf("est-ce", "il", "on"),
        "est-ce" to listOf("que", "qu'"),
        "il y" to listOf("a"),
        "y" to listOf("a", "aller"),
        "suis" to listOf("en", "à", "pas"),
        "vais" to listOf("bien", "voir", "être"),
        "peux" to listOf("tu", "pas", "venir"),
        "on se" to listOf("voit", "fait", "capte"),
        "ce" to listOf("soir", "matin", "week-end"),
        "à plus" to listOf("tard"),
        "en" to listOf("train", "fait", "retard"),
        "train" to listOf("de"),
        "s'il" to listOf("te", "vous"),
        "te" to listOf("plaît", "dis", "rappelle"),
        "plus" to listOf("tard", "tôt", "que"),
        "d'" to listOf("accord", "ailleurs"),
        "trop" to listOf("bien", "hâte", "cool"),
        "hâte" to listOf("de"),
        "désolé" to listOf("pour", "du"),
        "désolée" to listOf("pour", "du"),
        "comment" to listOf("ça", "tu", "vas"),
        "quoi" to listOf("de", "?"),
        "t'es" to listOf("où", "là", "dispo"),
        "dis" to listOf("moi", "lui"),
        "je t'" to listOf("aime", "appelle", "envoie"),
        "rien" to listOf("de"),
        "bisous" to listOf("à"),
    )

    /**
     * Les mots proposés après `previous` (et éventuellement `beforePrevious` pour « on se », « il y »).
     */
    fun next(beforePrevious: String?, previous: String?, user: UserDictionary?, limit: Int = 3): List<String> {
        if (previous.isNullOrEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        user?.next(previous, limit)?.forEach { out.add(it) }
        val prev = previous.lowercase().replace('’', '\'')
        if (beforePrevious != null) seed["${beforePrevious.lowercase()} $prev"]?.forEach { out.add(it) }
        seed[prev]?.forEach { out.add(it) }
        return out.take(limit)
    }
}
