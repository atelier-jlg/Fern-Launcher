package com.atelierjlg.fern.messages.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager

/**
 * Envoi des SMS : le message est d'abord écrit « en cours d'envoi » (OUTBOX), puis passe à
 * « envoyé » ou « échec » quand le réseau répond ([SendStatusReceiver]).
 * Un message long est découpé en plusieurs SMS puis recollé par le téléphone d'en face.
 */
object SmsSender {
    const val ACTION_SENT = "com.atelierjlg.fern.messages.SMS_SENT"
    const val ACTION_DELIVERED = "com.atelierjlg.fern.messages.SMS_DELIVERED"
    internal const val EXTRA_ID = "id"
    internal const val EXTRA_LAST = "last"

    /** Envoie à une ou plusieurs personnes (un SMS chacune). */
    fun send(context: Context, addresses: List<String>, body: String) {
        addresses.filter { it.isNotBlank() }.forEach { sendOne(context, it, body) }
    }

    private fun sendOne(context: Context, address: String, body: String) {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, address)
        val uri = context.contentResolver.insert(
            Telephony.Sms.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.THREAD_ID, threadId)
            },
        )
        val id = uri?.let { ContentUris.parseId(it) } ?: -1L
        try {
            val sms = com.atelierjlg.fern.messages.mms.MmsTransport.smsManager(context, -1)
            val parts = sms.divideMessage(body)
            val sent = ArrayList<PendingIntent>(parts.size)
            val delivered = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { i ->
                val intent = Intent(context, SendStatusReceiver::class.java).setAction(ACTION_SENT)
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_LAST, i == parts.size - 1)
                // Code unique par message et par morceau : les accusés ne se mélangent pas.
                val code = ((id % 1_000_000L) * 16 + i).toInt()
                sent += PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                // Accusé de remise (« Remis »). Modifiable : Android y joint l'accusé du réseau.
                val report = Intent(context, DeliveryReceiver::class.java).setAction(ACTION_DELIVERED).putExtra(EXTRA_ID, id)
                delivered += PendingIntent.getBroadcast(
                    context, code + 8, report,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            }
            val reports = com.atelierjlg.fern.messages.data.PrefsStore.read(context).deliveryReports
            sms.sendMultipartTextMessage(address, null, parts, sent, if (reports) delivered else null)
        } catch (e: Exception) {
            setType(context, id, Telephony.Sms.MESSAGE_TYPE_FAILED)
        }
    }

    /** Renvoyer un SMS en échec : l'ancien est remplacé (pas de doublon). */
    fun resend(context: Context, smsId: Long, address: String, body: String) {
        context.contentResolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, smsId), null, null)
        sendOne(context, address, body)
    }

    /** [onlyIfSending] : ne pas écraser un « échec » (un morceau d'un long SMS a pu échouer avant). */
    internal fun setType(context: Context, id: Long, type: Int, onlyIfSending: Boolean = false) {
        if (id < 0) return
        runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
                ContentValues().apply { put(Telephony.Sms.TYPE, type) },
                if (onlyIfSending) "${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_OUTBOX}" else null, null,
            )
        }
    }
}

/** Réponse du réseau pour chaque morceau envoyé : « échec » dès qu'un morceau échoue, « envoyé » au dernier. */
class SendStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(SmsSender.EXTRA_ID, -1L)
        when {
            resultCode != Activity.RESULT_OK -> SmsSender.setType(context, id, Telephony.Sms.MESSAGE_TYPE_FAILED)
            intent.getBooleanExtra(SmsSender.EXTRA_LAST, true) -> SmsSender.setType(context, id, Telephony.Sms.MESSAGE_TYPE_SENT, onlyIfSending = true)
        }
    }
}

/** Accusé de remise : le téléphone d'en face a bien reçu le SMS. */
class DeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(SmsSender.EXTRA_ID, -1L)
        if (id < 0) return
        val pdu = intent.getByteArrayExtra("pdu") ?: return
        val format = intent.getStringExtra("format")
        val status = runCatching { android.telephony.SmsMessage.createFromPdu(pdu, format).status }.getOrNull() ?: return
        // Norme GSM : 0x00–0x1F = remis, 0x20–0x3F = en attente, au-delà = échec.
        val value = when {
            status < 0x20 -> Telephony.Sms.STATUS_COMPLETE
            status < 0x40 -> Telephony.Sms.STATUS_PENDING
            else -> Telephony.Sms.STATUS_FAILED
        }
        runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
                ContentValues().apply { put(Telephony.Sms.STATUS, value) }, null, null,
            )
        }
    }
}
