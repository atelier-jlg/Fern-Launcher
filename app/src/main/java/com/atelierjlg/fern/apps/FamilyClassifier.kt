package com.atelierjlg.fern.apps

import com.atelierjlg.fern.data.Family

/**
 * Premier classement automatique des applis en familles.
 * Jules corrige ensuite à la main (appui long → Famille) : ses choix passent toujours avant.
 *
 * On regarde, dans l'ordre : des mots-clés dans le nom de paquet et le nom de l'appli,
 * puis la catégorie déclarée par l'appli à Android, puis « appli système ».
 */
object FamilyClassifier {

    // Valeurs de ApplicationInfo.CATEGORY_* (recopiées pour que ce fichier reste testable sans Android).
    private const val CATEGORY_GAME = 0
    private const val CATEGORY_AUDIO = 1
    private const val CATEGORY_VIDEO = 2
    private const val CATEGORY_IMAGE = 3
    private const val CATEGORY_SOCIAL = 4
    private const val CATEGORY_NEWS = 5
    private const val CATEGORY_MAPS = 6
    private const val CATEGORY_PRODUCTIVITY = 7

    /** Mots-clés par famille, du plus spécifique au plus général. */
    private val keywords: List<Pair<Family, List<String>>> = listOf(
        Family.Urgence to listOf("app-elles", "appelles", "stayingalive", "urgence", "emergency", "sauv", "secours"),
        Family.Communication to listOf(
            "whatsapp", "signal", "thoughtcrime", "telegram", "messag", "sms", "dialer", "telephon", "phone",
            "contacts", "mail", "k9", "thunderbird", "element", "matrix", "threema", "olvid",
        ),
        Family.Social to listOf(
            "instagram", "facebook", "twitter", "mastodon", "tusky", "reddit", "snapchat", "tiktok", "bereal",
            "pinterest", "linkedin", "discord", "threads", "bluesky", "pixelfed",
        ),
        Family.Argent to listOf(
            "bank", "banque", "n26", "revolut", "paypal", "lydia", "tricount", "curve", "wallet", "boursorama",
            "creditcoop", "credit", "caisse", "lcl", "bnp", "titres", "magasin", "leclerc", "carrefour", "lidl",
            "auchan", "courses", "jow", "leboncoin", "vinted", "amazon", "toogoodtogo", "shop",
        ),
        Family.Admin to listOf(
            "settings", "parametre", "security", "securite", "vpn", "password", "bitwarden", "keepass", "proton.android.pass",
            "authenticator", "aegis", "2fa", "applounge", "fdroid", "droidify", "neostore", "store",
        ),
        Family.Loisirs to listOf(
            "camera", "photo", "gallery", "galerie", "music", "musique", "spotify", "deezer", "podcast", "antennapod",
            "vlc", "video", "youtube", "newpipe", "netflix", "game", "stellarium", "radio", "lecteur",
        ),
        Family.Dehors to listOf(
            "maps", "osmand", "organicmaps", "magicearth", "naolib", "sncf", "transit", "meteo", "weather",
            "firefox", "fennec", "browser", "chrome", "navigat", "maison", "home", "velo", "komoot", "strava",
        ),
        Family.Organisation to listOf(
            "calendar", "agenda", "etar", "task", "tache", "note", "obsidian", "docs", "office", "drive", "files",
            "fichier", "pdf", "anki", "numworks", "claude", "miro", "canva", "teams", "slack", "notion", "clock",
            "horloge", "calcul", "aiesb", "scan",
        ),
    )

    fun classify(packageName: String, label: String, category: Int, isSystem: Boolean): Family {
        val haystack = (packageName + " " + label).normalizeForSearch().replace(" ", "")
        for ((family, words) in keywords) {
            if (words.any { haystack.contains(it) }) return family
        }
        return when (category) {
            CATEGORY_SOCIAL -> Family.Social
            CATEGORY_AUDIO, CATEGORY_VIDEO, CATEGORY_IMAGE, CATEGORY_GAME -> Family.Loisirs
            CATEGORY_MAPS, CATEGORY_NEWS -> Family.Dehors
            CATEGORY_PRODUCTIVITY -> Family.Organisation
            else -> if (isSystem) Family.Admin else Family.Organisation
        }
    }
}
