package com.atelierjlg.fern.messages.sms

import android.content.Context
import com.atelierjlg.fern.messages.mms.MmsTransport

/**
 * La règle d'envoi, au même endroit pour toute l'appli (écran, notification, messages programmés) :
 * plusieurs destinataires = conversation de groupe (MMS) ; une seule personne = SMS.
 */
object Outgoing {
    fun send(context: Context, addresses: List<String>, body: String) {
        val to = addresses.filter { it.isNotBlank() }
        if (to.isEmpty() || body.isBlank()) return
        if (to.size > 1) MmsTransport.send(context, to, body.trim(), emptyList()) else SmsSender.send(context, to, body.trim())
    }
}

/** La conversation affichée à l'écran (appli au premier plan) : pas de notification pour elle. */
object ActiveThread {
    @Volatile
    var id: Long = -1L
}
