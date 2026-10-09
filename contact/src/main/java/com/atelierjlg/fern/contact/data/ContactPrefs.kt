package com.atelierjlg.fern.contact.data

import android.content.Context

/** Les réglages de Fern Contact (petites préférences). */
object ContactPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("fern-contact", Context.MODE_PRIVATE)

    fun blockTelemarketing(context: Context) = prefs(context).getBoolean("demarchage", true)
    fun setBlockTelemarketing(context: Context, on: Boolean) = prefs(context).edit().putBoolean("demarchage", on).apply()

    fun birthdayReminders(context: Context) = prefs(context).getBoolean("anniversaires", true)
    fun setBirthdayReminders(context: Context, on: Boolean) = prefs(context).edit().putBoolean("anniversaires", on).apply()

    /** Réponses rapides quand on refuse un appel. */
    val DEFAULT_REPLIES = listOf("Je ne peux pas répondre, je te rappelle.", "Je suis occupé·e, écris-moi.", "J'arrive, je te rappelle dans 5 min.")

    fun quickReplies(context: Context): List<String> =
        prefs(context).getString("reponses", null)?.split('\n')?.filter { it.isNotBlank() }?.ifEmpty { null } ?: DEFAULT_REPLIES

    fun setQuickReplies(context: Context, replies: List<String>) =
        prefs(context).edit().putString("reponses", replies.filter { it.isNotBlank() }.joinToString("\n")).apply()
}
