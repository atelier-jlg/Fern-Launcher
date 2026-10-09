package com.atelierjlg.fern.widgets

import com.atelierjlg.fern.data.CarnetDay
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * La plante qui pousse : elle suit le Carnet du jour sur les 7 derniers jours.
 * Chaque habitude cochée la fait grandir ; deux jours sans rien et elle fane un peu.
 */
object Plant {

    data class State(
        /** Nombre de frondes de fougère, de 1 à 7. */
        val fronds: Int,
        /** Jours de la semaine où au moins une habitude a été cochée. */
        val activeDays: Int,
        /** Ni hier ni aujourd'hui : elle a soif. */
        val thirsty: Boolean,
        /** Part des habitudes cochées sur 7 jours (0 à 1). */
        val score: Float,
    )

    fun state(days: Map<String, CarnetDay>, today: LocalDate, habitCount: Int): State {
        val week = (0L..6L).map { days[today.minusDays(it).toString()] }
        val checked = week.sumOf { it?.habits?.size ?: 0 }
        val possible = (habitCount.coerceAtLeast(1)) * 7
        val score = (checked.toFloat() / possible).coerceIn(0f, 1f)
        val activeDays = week.count { (it?.habits?.size ?: 0) > 0 }
        val thirsty = week.take(2).all { (it?.habits?.size ?: 0) == 0 }
        return State(
            fronds = 1 + (score * 6).roundToInt(),
            activeDays = activeDays,
            thirsty = thirsty,
            score = score,
        )
    }
}
