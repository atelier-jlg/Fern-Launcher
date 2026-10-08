package com.atelierjlg.fern.contact.data

import android.content.Context
import android.provider.CallLog.Calls
import com.atelierjlg.fern.common.PhoneNumbers
import java.time.Instant
import java.time.ZoneId

/** Une ligne du journal d'appels. */
data class CallEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    /** Calls.INCOMING_TYPE, OUTGOING_TYPE, MISSED_TYPE… */
    val type: Int,
    val date: Long,
    val durationSec: Long,
)

/** Des appels consécutifs du même numéro, le même jour, du même genre (manqués ou non) : une seule ligne « (3) ». */
data class RecentGroup(val entries: List<CallEntry>) {
    val latest: CallEntry get() = entries.first()
    val number: String get() = latest.number
    val count: Int get() = entries.size
    val isMissed: Boolean get() = latest.type == Calls.MISSED_TYPE
}

/** Regroupe le journal (du plus récent au plus ancien). Fonction pure, testée. */
fun groupRecents(entries: List<CallEntry>, zone: ZoneId = ZoneId.systemDefault()): List<RecentGroup> {
    val groups = mutableListOf<MutableList<CallEntry>>()
    fun day(e: CallEntry) = Instant.ofEpochMilli(e.date).atZone(zone).toLocalDate()
    fun missed(e: CallEntry) = e.type == Calls.MISSED_TYPE
    entries.forEach { e ->
        val last = groups.lastOrNull()?.first()
        if (last != null && PhoneNumbers.same(last.number, e.number) && day(last) == day(e) && missed(last) == missed(e)) {
            groups.last() += e
        } else {
            groups += mutableListOf(e)
        }
    }
    return groups.map { RecentGroup(it) }
}

class RecentsRepo(private val context: Context) {
    /** Les 500 derniers appels. */
    fun load(): List<CallEntry> {
        val uri = Calls.CONTENT_URI.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY, "500").build()
        val out = mutableListOf<CallEntry>()
        context.contentResolver.query(
            uri,
            arrayOf(Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.TYPE, Calls.DATE, Calls.DURATION),
            null, null, "${Calls.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += CallEntry(c.getLong(0), c.getString(1).orEmpty(), c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5))
            }
        }
        return out
    }

    fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        context.contentResolver.delete(Calls.CONTENT_URI, "${Calls._ID} IN (${ids.joinToString(",")})", null)
    }

    /** Les appels manqués sont « vus » : la pastille du téléphone disparaît. */
    fun markMissedSeen() {
        runCatching {
            context.contentResolver.update(
                Calls.CONTENT_URI,
                android.content.ContentValues().apply {
                    put(Calls.NEW, 0)
                    put(Calls.IS_READ, 1)
                },
                "${Calls.TYPE} = ? AND ${Calls.NEW} = 1", arrayOf(Calls.MISSED_TYPE.toString()),
            )
        }
    }
}

/** « Aujourd'hui · 14:02 », « Hier · 09:10 », « lun. 6 oct. · 18:30 ». */
fun formatCallTime(date: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(date).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val hour = "%02d:%02d".format(t.hour, t.minute)
    val d = t.toLocalDate()
    val days = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
    val months = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    val dayText = when (d) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> "${days[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${months[d.monthValue - 1]}" +
            if (d.year != today.year) " ${d.year}" else ""
    }
    return "$dayText · $hour"
}

/** « 1 min 05 », « 42 s ». */
fun formatDuration(sec: Long): String = when {
    sec <= 0 -> ""
    sec < 60 -> "$sec s"
    sec < 3600 -> "${sec / 60} min %02d".format(sec % 60)
    else -> "${sec / 3600} h %02d".format((sec % 3600) / 60)
}
