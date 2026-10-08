package com.atelierjlg.fern.contact.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.atelierjlg.fern.common.PhoneNumbers
import com.atelierjlg.fern.contact.R

/**
 * Les notifications d'appel :
 * - **entrant** : plein écran (téléphone verrouillé ou écran éteint), sinon bandeau avec Répondre / Refuser ;
 * - **en cours** : reste dans le volet pour revenir à l'appel, avec chrono et « Raccrocher ».
 * La sonnerie et le vibreur sont joués par Android lui-même, pas par ces notifications.
 */
object CallNotifications {
    private const val CHANNEL_INCOMING = "appels_entrants"
    private const val CHANNEL_ONGOING = "appel_en_cours"
    private const val ID_INCOMING = 0xCA11
    private const val ID_ONGOING = 0xCA12

    const val ACTION_ANSWER = "com.atelierjlg.fern.contact.ANSWER"
    const val ACTION_DECLINE = "com.atelierjlg.fern.contact.DECLINE"
    const val ACTION_HANGUP = "com.atelierjlg.fern.contact.HANGUP"

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_INCOMING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_INCOMING, "Appels entrants", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Écran d'appel quand quelqu'un t'appelle"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_ONGOING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ONGOING, "Appel en cours", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Pour revenir à l'appel en cours"
                    setSound(null, null)
                },
            )
        }
    }

    private fun broadcast(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openScreen(context: Context, code: Int) = PendingIntent.getActivity(
        context, code,
        Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Met les notifications en accord avec les appels en cours. */
    fun update(context: Context, calls: List<CallInfo>) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val ringing = calls.firstOrNull { it.isRinging }
        if (ringing != null) nm.notify(ID_INCOMING, incoming(context, ringing)) else nm.cancel(ID_INCOMING)

        val current = calls.firstOrNull { it.isActive } ?: calls.firstOrNull { it.isDialing || it.isHeld || it.isSelectingSim }
        if (current != null && ringing == null) nm.notify(ID_ONGOING, ongoing(context, current, calls.size)) else nm.cancel(ID_ONGOING)
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(ID_INCOMING)
        nm.cancel(ID_ONGOING)
    }

    private fun incoming(context: Context, call: CallInfo): Notification {
        val answer = broadcast(context, ACTION_ANSWER, 1)
        val decline = broadcast(context, ACTION_DECLINE, 2)
        val screen = openScreen(context, 3)
        val subtitle = if (call.name.isNotBlank() && call.number.isNotBlank()) PhoneNumbers.format(call.number) else "Appel entrant"
        val builder = Notification.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setContentTitle(call.title.let { if (call.name.isBlank()) PhoneNumbers.format(it) else it })
            .setContentText(subtitle)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder().setName(call.title).setImportant(true).build()
            builder.setStyle(Notification.CallStyle.forIncomingCall(person, decline, answer))
        } else {
            builder.addAction(Notification.Action.Builder(null, "Refuser", decline).build())
            builder.addAction(Notification.Action.Builder(null, "Répondre", answer).build())
        }
        return builder.build()
    }

    private fun ongoing(context: Context, call: CallInfo, count: Int): Notification {
        val text = when {
            call.isSelectingSim -> "Choisis la SIM"
            call.isDialing -> "Appel en cours…"
            call.isHeld -> "En attente"
            count > 1 -> "$count appels"
            else -> "En communication"
        }
        val builder = Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setContentTitle(if (call.name.isBlank()) PhoneNumbers.format(call.title) else call.title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openScreen(context, 4))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, "Raccrocher", broadcast(context, ACTION_HANGUP, 5)).build())
        if (call.isActive && call.connectTimeMillis > 0) {
            builder.setUsesChronometer(true).setWhen(call.connectTimeMillis).setShowWhen(true)
        }
        return builder.build()
    }
}

/** Reçoit les boutons des notifications (Répondre, Refuser, Raccrocher). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val calls = CallManager.calls.value
        when (intent.action) {
            CallNotifications.ACTION_ANSWER -> calls.firstOrNull { it.isRinging }?.let {
                CallManager.answer(it)
                FernInCallService.openCallScreen(context)
            }
            CallNotifications.ACTION_DECLINE -> calls.firstOrNull { it.isRinging }?.let { CallManager.reject(it) }
            CallNotifications.ACTION_HANGUP ->
                (calls.firstOrNull { it.isActive } ?: calls.firstOrNull { !it.isRinging })?.let { CallManager.hangUp(it) }
        }
    }
}
