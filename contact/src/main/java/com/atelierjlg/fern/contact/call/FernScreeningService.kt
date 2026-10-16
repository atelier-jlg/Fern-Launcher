package com.atelierjlg.fern.contact.call

import android.telecom.Call
import android.telecom.CallScreeningService
import com.atelierjlg.fern.contact.data.ContactPrefs
import com.atelierjlg.fern.contact.data.Telemarketing

/**
 * Filtre des appels entrants (Android le demande à l'appli Téléphone par défaut avant de faire sonner).
 * Si « Bloquer le démarchage » est activé : les numéros réservés au démarchage sont refusés sans sonner,
 * et apparaissent quand même dans le journal (« bloqué »).
 */
class FernScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val block = incoming && ContactPrefs.blockTelemarketing(this) && Telemarketing.matches(number)
        val response = CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .setSkipCallLog(false)
            .setSkipNotification(block)
            .build()
        respondToCall(details, response)
    }
}
