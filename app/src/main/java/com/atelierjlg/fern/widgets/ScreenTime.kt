package com.atelierjlg.fern.widgets

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/**
 * Temps d'écran doux : combien de temps aujourd'hui sur certaines applis.
 * Android le sait grâce à « l'accès aux données d'utilisation », à autoriser une fois à la main.
 */
object ScreenTime {

    /** Un passage au premier plan (true) ou en arrière-plan (false) d'une appli. */
    data class Event(val packageName: String, val foreground: Boolean, val time: Long)

    /**
     * Additionne le temps passé au premier plan par appli entre `start` et `end`.
     * Une appli encore ouverte à `end` compte jusqu'à `end` ; une appli déjà ouverte avant `start`
     * (pas d'événement d'ouverture) compte depuis `start`.
     */
    fun foregroundTimes(events: List<Event>, start: Long, end: Long): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val openSince = mutableMapOf<String, Long>()
        for (e in events.sortedBy { it.time }) {
            if (e.foreground) {
                openSince.putIfAbsent(e.packageName, e.time)
            } else {
                val since = openSince.remove(e.packageName)
                    ?: if (totals[e.packageName] == null) start else continue
                totals[e.packageName] = (totals[e.packageName] ?: 0L) + (e.time - since).coerceAtLeast(0L)
            }
        }
        for ((pkg, since) in openSince) {
            totals[pkg] = (totals[pkg] ?: 0L) + (end - since).coerceAtLeast(0L)
        }
        return totals
    }

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Le temps passé aujourd'hui (depuis minuit) sur chaque appli, en millisecondes. */
    fun today(context: Context): Map<String, Long> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val events = mutableListOf<Event>()
        val raw = manager.queryEvents(start, end)
        val e = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> events += Event(e.packageName, true, e.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED -> events += Event(e.packageName, false, e.timeStamp)
            }
        }
        return foregroundTimes(events, start, end) - context.packageName
    }

    /** « 1 h 05 », « 42 min ». */
    fun format(millis: Long): String {
        val minutes = millis / 60_000
        return if (minutes >= 60) "${minutes / 60} h %02d".format(minutes % 60) else "$minutes min"
    }
}

/** Ce qu'affiche le widget « Temps d'écran ». */
data class ScreenTimeState(
    val hasAccess: Boolean = false,
    val totalMs: Long = 0,
    /** Les 3 applis les plus utilisées : (nom, durée). */
    val top: List<Pair<String, Long>> = emptyList(),
    /** true = on compte les applis du mode Focus ; false = toutes. */
    val focusOnly: Boolean = false,
)
