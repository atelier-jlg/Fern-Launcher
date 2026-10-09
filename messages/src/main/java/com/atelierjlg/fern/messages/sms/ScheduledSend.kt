package com.atelierjlg.fern.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atelierjlg.fern.messages.data.MessagesRepo
import com.atelierjlg.fern.messages.data.PrefsStore
import com.atelierjlg.fern.messages.data.Scheduled
import kotlin.concurrent.thread

/**
 * Messages programmés : gardés dans les préférences de l'appli, envoyés par une alarme
 * à l'heure exacte (même téléphone en veille), réarmés après un redémarrage.
 */
object ScheduledSend {
    private const val ACTION = "com.atelierjlg.fern.messages.SCHEDULED"
    private const val EXTRA_ID = "id"

    fun canExact(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, addresses: List<String>, body: String, at: Long) {
        val item = Scheduled(System.currentTimeMillis(), addresses, body, at)
        PrefsStore.update(context) { it.copy(scheduled = it.scheduled + item) }
        arm(context, item)
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
        PrefsStore.update(context) { p -> p.copy(scheduled = p.scheduled.filterNot { it.id == id }) }
    }

    fun rearmAll(context: Context) = PrefsStore.read(context).scheduled.forEach { arm(context, it) }

    private fun arm(context: Context, item: Scheduled) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, item.id)
        runCatching {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
        }
    }

    private fun pending(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, (id % Int.MAX_VALUE).toInt(),
        Intent(context, ScheduledReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** L'alarme sonne : on envoie, on retire de la liste, on prévient discrètement. */
    internal fun fire(context: Context, id: Long) {
        val item = PrefsStore.read(context).scheduled.firstOrNull { it.id == id } ?: return
        // Retiré de la liste seulement une fois confié au réseau (sinon il resterait visible, à renvoyer).
        Outgoing.send(context, item.addresses, item.body)
        PrefsStore.update(context) { p -> p.copy(scheduled = p.scheduled.filterNot { it.id == id }) }
        val repo = MessagesRepo(context)
        val threadId = runCatching { repo.threadIdFor(item.addresses) }.getOrDefault(-1L)
        MessageNotifier.info(context, threadId, "Message programmé envoyé", "À ${repo.displayFor(item.addresses).first}")
    }

    internal const val EXTRA = EXTRA_ID
}

class ScheduledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ScheduledSend.EXTRA, -1L)
        if (id < 0) return
        val pending = goAsync()
        thread {
            try {
                ScheduledSend.fire(context, id)
            } catch (e: Exception) {
                android.util.Log.e("FernSms", "Message programmé non envoyé", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Après un redémarrage (ou une mise à jour de l'appli), les alarmes sont perdues : on les remet. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            thread {
                try {
                    ScheduledSend.rearmAll(context)
                } catch (e: Exception) {
                    android.util.Log.e("FernSms", "Alarmes non réarmées", e)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
