package com.atelierjlg.fern.keyboard

import android.content.Context

/** Hauteur des touches (réglable dans l'écran de réglages). */
enum class KeyHeight(val label: String, val dp: Int) {
    Petite("Petite", 46),
    Normale("Normale", 52),
    Grande("Grande", 58),
}

/** Les réglages du clavier. Chaque champ a une valeur par défaut, comme LauncherConfig dans Fern. */
data class KeyboardSettings(
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val autoCaps: Boolean = true,
    val autoPeriod: Boolean = true,
    val numberRow: Boolean = false,
    val cursorOnSpace: Boolean = true,
    val foamAnimation: Boolean = true,
    val keyHeight: KeyHeight = KeyHeight.Normale,
    val autoCorrect: Boolean = true,
    val suggestions: Boolean = true,
    val learnWords: Boolean = true,
    val contactNames: Boolean = false,
)

/** Rangement des réglages dans le téléphone (SharedPreferences : un petit fichier clé → valeur). */
object KeyboardPrefs {
    private const val FILE = "clavier"

    fun load(context: Context): KeyboardSettings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = KeyboardSettings()
        return KeyboardSettings(
            haptics = p.getBoolean("haptics", d.haptics),
            sound = p.getBoolean("sound", d.sound),
            autoCaps = p.getBoolean("autoCaps", d.autoCaps),
            autoPeriod = p.getBoolean("autoPeriod", d.autoPeriod),
            numberRow = p.getBoolean("numberRow", d.numberRow),
            cursorOnSpace = p.getBoolean("cursorOnSpace", d.cursorOnSpace),
            foamAnimation = p.getBoolean("foamAnimation", d.foamAnimation),
            keyHeight = p.getString("keyHeight", null)
                ?.let { name -> KeyHeight.entries.firstOrNull { it.name == name } } ?: d.keyHeight,
            autoCorrect = p.getBoolean("autoCorrect", d.autoCorrect),
            suggestions = p.getBoolean("suggestions", d.suggestions),
            learnWords = p.getBoolean("learnWords", d.learnWords),
            contactNames = p.getBoolean("contactNames", d.contactNames),
        )
    }

    fun save(context: Context, s: KeyboardSettings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("haptics", s.haptics)
            .putBoolean("sound", s.sound)
            .putBoolean("autoCaps", s.autoCaps)
            .putBoolean("autoPeriod", s.autoPeriod)
            .putBoolean("numberRow", s.numberRow)
            .putBoolean("cursorOnSpace", s.cursorOnSpace)
            .putBoolean("foamAnimation", s.foamAnimation)
            .putString("keyHeight", s.keyHeight.name)
            .putBoolean("autoCorrect", s.autoCorrect)
            .putBoolean("suggestions", s.suggestions)
            .putBoolean("learnWords", s.learnWords)
            .putBoolean("contactNames", s.contactNames)
            .apply()
    }
}
