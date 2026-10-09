package com.atelierjlg.fern.messages.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import com.atelierjlg.fern.messages.MainActivity
import com.atelierjlg.fern.messages.R
import com.atelierjlg.fern.messages.data.MessagesRepo
import com.atelierjlg.fern.messages.data.NotifyMode
import com.atelierjlg.fern.messages.data.Otp
import com.atelierjlg.fern.messages.data.PrefsStore

/**
 * Notifications des messages reçus, une par conversation, avec :
 * **Répondre** (sans ouvrir l'appli), **Marquer comme lu**, et **Copier 123456** quand le SMS
 * contient un code de vérification.
 */
object MessageNotifier {
    private const val CHANNEL_SOUND = "messages"
    private const val CHANNEL_SILENT = "messages_silencieux"
    const val KEY_REPLY = "reponse"
    const val EXTRA_THREAD = "thread_id"
    const val EXTRA_CODE = "code"

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_SOUND) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_SOUND, "Messages", NotificationManager.IMPORTANCE_HIGH))
        }
        if (nm.getNotificationChannel(CHANNEL_SILENT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SILENT, "Messages silencieux", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) },
            )
        }
    }

    private fun idFor(threadId: Long) = (threadId % Int.MAX_VALUE).toInt()

    /** Montre (ou met à jour) la notification d'une conversation avec ses messages non lus. */
    fun notifyThread(context: Context, threadId: Long) {
        val mode = PrefsStore.read(context).notify[threadId] ?: NotifyMode.Son
        if (mode == NotifyMode.Aucune) return
        // Conversation ouverte à l'écran : on la lit déjà.
        if (ActiveThread.id == threadId) return
        channels(context)
        val repo = MessagesRepo(context)
        val unread = repo.unreadIn(threadId)
        if (unread.isEmpty()) {
            cancel(context, threadId)
            return
        }
        val addresses = repo.addressesOf(threadId).ifEmpty { listOf(unread.last().address) }
        val (title, _) = repo.displayFor(addresses)

        val me = Person.Builder().setName("Moi").build()
        val style = Notification.MessagingStyle(me).setConversationTitle(if (addresses.size > 1) title else null)
            .setGroupConversation(addresses.size > 1)
        unread.forEach { m ->
            val sender = Person.Builder().setName(repo.contact(m.address)?.first ?: com.atelierjlg.fern.common.PhoneNumbers.format(m.address)).build()
            val text = m.body.ifBlank {
                when {
                    m.images.isNotEmpty() -> "Photo"
                    m.vcards.isNotEmpty() -> "Contact : ${m.vcards.first().second}"
                    m.files.any { it.isVideo } -> "Vidéo"
                    m.files.any { it.isAudio } -> "Message vocal"
                    m.files.isNotEmpty() -> "Fichier : ${m.files.first().name}"
                    else -> "MMS"
                }
            }
            style.addMessage(Notification.MessagingStyle.Message(text, m.date, sender))
        }

        val code = Otp.find(unread.last().body)
        val builder = Notification.Builder(context, if (mode == NotifyMode.Son) CHANNEL_SOUND else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setStyle(style)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setShowWhen(true)
            .setWhen(unread.last().date)
            .setAutoCancel(true)
            .setNumber(unread.size)
            .setContentIntent(openThread(context, threadId))
            // Écran verrouillé : on dit qu'il y a un message, sans montrer le texte (codes, etc.).
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CHANNEL_SILENT)
                    .setSmallIcon(R.drawable.ic_notification_message)
                    .setContentTitle(if (unread.size > 1) "${unread.size} nouveaux messages" else "Nouveau message")
                    .build(),
            )

        if (code != null) {
            builder.addAction(Notification.Action.Builder(null, "Copier $code", action(context, NotificationActions.ACTION_COPY, threadId, code)).build())
        }
        val replyInput = RemoteInput.Builder(KEY_REPLY).setLabel("Ta réponse").build()
        builder.addAction(
            Notification.Action.Builder(null, "Répondre", action(context, NotificationActions.ACTION_REPLY, threadId, mutable = true))
                .addRemoteInput(replyInput)
                .setAllowGeneratedReplies(false)
                .build(),
        )
        builder.addAction(Notification.Action.Builder(null, "Lu", action(context, NotificationActions.ACTION_READ, threadId)).build())

        context.getSystemService(NotificationManager::class.java).notify(idFor(threadId), builder.build())
    }

    fun cancel(context: Context, threadId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(idFor(threadId))
    }

    /** Petite notification discrète (message programmé envoyé…). */
    fun info(context: Context, threadId: Long, title: String, text: String) {
        channels(context)
        val n = Notification.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openThread(context, threadId))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(idFor(threadId) xor 0x5000000, n)
    }

    fun openThread(context: Context, threadId: Long): PendingIntent = PendingIntent.getActivity(
        context, idFor(threadId),
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_THREAD, threadId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, action: String, threadId: Long, code: String? = null, mutable: Boolean = false): PendingIntent {
        val intent = Intent(context, NotificationActions::class.java).setAction(action)
            .putExtra(EXTRA_THREAD, threadId)
            .putExtra(EXTRA_CODE, code)
        // « Répondre » doit pouvoir recevoir le texte tapé : PendingIntent modifiable (et explicite).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
        val request = idFor(threadId) * 4 + action.hashCode() % 4
        return PendingIntent.getBroadcast(context, request, intent, flags)
    }
}
