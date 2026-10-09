package com.atelierjlg.fern.contact.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import com.atelierjlg.fern.contact.MainActivity
import com.atelierjlg.fern.contact.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.concurrent.thread

/** Un anniversaire enregistré dans un contact. */
data class Birthday(val contactId: Long, val name: String, val raw: String, val number: String?)

object Birthdays {
    /** « --05-14 » ou « 1990-05-14 » tombe-t-il ce jour-là ? (Le 29 février fêté le 28 les autres années.) */
    fun isOn(raw: String, day: LocalDate): Boolean {
        val m = Regex("^(\\d{4}|-)-?(\\d{2})-(\\d{2})").find(raw.trim()) ?: return false
        val month = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (month == 2 && d == 29 && !day.isLeapYear) return day.monthValue == 2 && day.dayOfMonth == 28
        return day.monthValue == month && day.dayOfMonth == d
    }

    /** L'âge atteint ce jour-là, si l'année de naissance est connue. */
    fun age(raw: String, day: LocalDate): Int? {
        val year = Regex("^(\\d{4})-").find(raw.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return (day.year - year).takeIf { it in 1..120 }
    }

    fun all(context: Context): List<Birthday> {
        val out = mutableListOf<Birthday>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME_PRIMARY, Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
                arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) out += Birthday(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), null)
            }
        }
        return out.distinctBy { it.contactId }
    }

    private fun firstNumber(context: Context, contactId: Long): String? = runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(contactId.toString()), null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Rappel chaque matin vers 9 h (à quelques minutes près, pour économiser la batterie). */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 9, Intent(context, BirthdayReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!ContactPrefs.birthdayReminders(context)) {
            am.cancel(pi)
            return
        }
        val zone = ZoneId.systemDefault()
        var next = LocalDate.now().atTime(LocalTime.of(9, 0)).atZone(zone)
        if (next.toInstant().toEpochMilli() <= System.currentTimeMillis()) next = next.plusDays(1)
        am.setInexactRepeating(AlarmManager.RTC, next.toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY, pi)
    }

    /** Les notifications du jour : « Anniversaire de Léa (25 ans) », avec Appeler et Écrire. */
    fun notifyToday(context: Context) {
        if (!ContactPrefs.birthdayReminders(context)) return
        val today = LocalDate.now()
        val todays = all(context).filter { isOn(it.raw, today) }
        if (todays.isEmpty()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel("anniversaires") == null) {
            nm.createNotificationChannel(NotificationChannel("anniversaires", "Anniversaires", NotificationManager.IMPORTANCE_DEFAULT))
        }
        todays.forEach { b ->
            val age = age(b.raw, today)
            val number = firstNumber(context, b.contactId)
            val open = PendingIntent.getActivity(
                context, b.contactId.toInt(),
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_CONTACT, b.contactId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = Notification.Builder(context, "anniversaires")
                .setSmallIcon(R.drawable.ic_notification_call)
                .setContentTitle("Anniversaire de ${b.name}")
                .setContentText(if (age != null) "$age ans aujourd'hui" else "C'est aujourd'hui")
                .setContentIntent(open)
                .setAutoCancel(true)
            if (number != null) {
                builder.addAction(
                    Notification.Action.Builder(
                        null, "Appeler",
                        PendingIntent.getActivity(
                            context, b.contactId.toInt() + 1, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)),
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
                )
                builder.addAction(
                    Notification.Action.Builder(
                        null, "Écrire",
                        PendingIntent.getActivity(
                            context, b.contactId.toInt() + 2, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)),
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
                )
            }
            nm.notify(("anniv" + b.contactId).hashCode(), builder.build())
        }
    }
}

class BirthdayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    Birthdays.schedule(context)
                } else {
                    Birthdays.notifyToday(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
