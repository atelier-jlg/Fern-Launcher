package com.atelierjlg.fern.data

import kotlinx.serialization.Serializable

/**
 * Les familles d'applis du guide des icônes (couleur de plaque + couleur du trait).
 * L'ordre est celui du tiroir : « Admin & sécurité » reste discret, en dernier.
 */
@Serializable
enum class Family(val label: String, val plate: String, val trait: String) {
    Communication("Communication & mail", "#186348", "#F6EEDC"),
    Social("Social", "#8E2733", "#F6EADB"),
    Urgence("Urgence", "#EC9AA0", "#5A1520"),
    Organisation("Organisation, travail & fichiers", "#EFE5CF", "#14241B"),
    Argent("Argent & courses", "#BFE3A3", "#14241B"),
    Loisirs("Photo, musique & loisirs", "#9DB384", "#14241B"),
    Dehors("Dehors, maison & web", "#56633C", "#F6EEDC"),
    Admin("Admin & sécurité", "#35553F", "#F6EEDC"),
}

/** Les couleurs d'une famille dans un thème (plaque + trait du picto). */
@Serializable
data class FamilyColors(val plate: String, val trait: String)

/** Comment dessiner les icônes. */
@Serializable
enum class IconMode(val label: String) {
    Origine("Icônes d'origine"),
    Pack("Pack d'icônes installé"),
    Plaques("Plaques Fern (couleur par famille)"),
}

@Serializable
data class IconSettings(
    val mode: IconMode = IconMode.Plaques,
    /** Le pack utilisé en mode « Pack » (ex. ton pack Renkin). */
    val packPackage: String? = null,
    /** D'où viennent les pictos des plaques (null = Arcticons s'il est installé). */
    val glyphPackage: String? = null,
)
