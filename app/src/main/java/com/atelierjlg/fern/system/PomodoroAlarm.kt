package com.atelierjlg.fern.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import com.atelierjlg.fern.MainActivity
import com.atelierjlg.fern.R

/**
 * Le réveil du Pomodoro : Android nous réveille à la fin du travail puis de la pause,
 * même si Fern est en arrière-plan, et on affiche une notification.
 * (Pas d'alarme « exacte » : Android peut décaler de quelques secondes, c'est voulu, ça ne demande aucune autorisation.)
 */
class PomodoroReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        PomodoroAlarm.notify(context, text)
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}

object PomodoroAlarm {
    private const val CHANNEL = "pomodoro"
    private const val NOTIFICATION_ID = 25

    private fun pending(context: Context, requestCode: Int, text: String?): PendingIntent {
        val intent = Intent(context, PomodoroReceiver::class.java).putExtra(PomodoroReceiver.EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Programme les deux sonneries : fin du travail, fin de la pause. */
    fun schedule(context: Context, workEndsAt: Long, breakEndsAt: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setWindow(AlarmManager.RTC_WAKEUP, workEndsAt, 15_000L, pending(context, 1, "Pause ! Lève-toi, bois un verre d'eau ✿"))
        alarms.setWindow(AlarmManager.RTC_WAKEUP, breakEndsAt, 15_000L, pending(context, 2, "Fin de la pause · on s'y remet ?"))
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pending(context, 1, null))
        alarms.cancel(pending(context, 2, null))
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, text: String) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pomodoro", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Pomodoro")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
