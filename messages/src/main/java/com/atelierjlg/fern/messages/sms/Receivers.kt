package com.atelierjlg.fern.messages.sms

import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PersistableBundle
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import com.atelierjlg.fern.messages.data.MessagesRepo
import com.atelierjlg.fern.messages.data.PrefsStore
import kotlin.concurrent.thread

/**
 * Un SMS arrive (Fern Messages est l'appli SMS par défaut : c'est à elle de l'enregistrer).
 * Les SMS longs arrivent en morceaux : on les recolle.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val address = parts.first().displayOriginatingAddress ?: parts.first().originatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody.orEmpty() }
        val sentAt = parts.first().timestampMillis
        val subId = intent.getIntExtra("subscription", -1)
        val pending = goAsync()
        thread {
            try {
                val threadId = MessagesRepo(context).writeIncoming(address, body, sentAt, subId)
                // Un message dans une conversation archivée la fait revenir dans la liste.
                PrefsStore.update(context) { it.copy(archived = it.archived - threadId) }
                MessageNotifier.notifyThread(context, threadId)
            } catch (e: Exception) {
                Log.e("FernSms", "SMS reçu non enregistré", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Un MMS est annoncé : on le télécharge (voir MmsTransport). */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pdu = intent.getByteArrayExtra("data") ?: return
        val subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        val pending = goAsync()
        thread {
            try {
                com.atelierjlg.fern.messages.mms.MmsTransport.onNotification(context, pdu, subId)
            } catch (e: Exception) {
                Log.e("FernMms", "Annonce de MMS non traitée", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Boutons des notifications : Répondre, Lu, Copier le code. */
class NotificationActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MessageNotifier.EXTRA_THREAD, -1L)
        if (threadId < 0) return
        when (intent.action) {
            ACTION_COPY -> {
                val code = intent.getStringExtra(MessageNotifier.EXTRA_CODE) ?: return
                val clip = ClipData.newPlainText("Code", code)
                // Marqué « sensible » : Android ne l'affiche pas en clair dans l'aperçu du presse-papiers.
                if (Build.VERSION.SDK_INT >= 33) {
                    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Code $code copié", Toast.LENGTH_SHORT).show()
                markRead(context, threadId)
            }
            ACTION_READ -> markRead(context, threadId)
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(MessageNotifier.KEY_REPLY)?.toString()
                if (text.isNullOrBlank()) return
                val pending = goAsync()
                thread {
                    try {
                        val repo = MessagesRepo(context)
                        Outgoing.send(context, repo.addressesOf(threadId), text)
                        repo.markRead(threadId)
                        MessageNotifier.cancel(context, threadId)
                    } catch (e: Exception) {
                        Log.e("FernSms", "Réponse depuis la notification non envoyée", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun markRead(context: Context, threadId: Long) {
        val pending = goAsync()
        thread {
            try {
                MessagesRepo(context).markRead(threadId)
                MessageNotifier.cancel(context, threadId)
            } catch (e: Exception) {
                Log.e("FernSms", "Marquer comme lu impossible", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.atelierjlg.fern.messages.REPLY"
        const val ACTION_READ = "com.atelierjlg.fern.messages.READ"
        const val ACTION_COPY = "com.atelierjlg.fern.messages.COPY"
    }
}

/**
 * « Répondre par SMS » quand on refuse un appel (exigé pour être l'appli SMS par défaut).
 * Fern Contact s'en servira pour « Refuser avec un message ».
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val recipients = intent.data?.schemeSpecificPart?.substringBefore('?')?.split(',', ';')?.map { it.trim() }.orEmpty()
            if (!text.isNullOrBlank() && recipients.isNotEmpty()) {
                thread {
                    runCatching { SmsSender.send(this, recipients, text) }
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
