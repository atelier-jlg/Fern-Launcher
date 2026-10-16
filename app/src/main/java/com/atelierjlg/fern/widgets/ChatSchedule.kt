package com.atelierjlg.fern.widgets

import com.atelierjlg.fern.data.ChatSettings
import com.atelierjlg.fern.data.ChoreFreq
import com.atelierjlg.fern.data.ChoreRule
import com.atelierjlg.fern.data.ruleFor
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Les rythmes des tâches du chat : à quelle « période » on est, pour savoir si la case est cochée.
 * Quand on change de période (midi, minuit, samedi soir…), la case se décoche toute seule.
 */
object ChatSchedule {

    /** La période en cours pour ce rythme, ex. « 2026-10-06-soir » ou « 2026-10-03-semaine ». */
    fun periodKey(rule: ChoreRule, now: LocalDateTime): String = when (rule.freq) {
        ChoreFreq.Quotidien -> now.toLocalDate().toString()
        ChoreFreq.DeuxParJour -> now.toLocalDate().toString() + if (now.hour < 12) "-matin" else "-soir"
        ChoreFreq.Hebdomadaire -> lastReset(rule, now).toLocalDate().toString() + "-semaine"
    }

    /** La dernière remise à zéro d'une tâche hebdomadaire (ex. samedi dernier à 18 h, ou aujourd'hui). */
    fun lastReset(rule: ChoreRule, now: LocalDateTime): LocalDateTime {
        val day = DayOfWeek.of(rule.day.coerceIn(1, 7))
        val candidate = now.toLocalDate().with(TemporalAdjusters.previousOrSame(day)).atTime(rule.hour.coerceIn(0, 23), 0)
        return if (candidate.isAfter(now)) candidate.minusWeeks(1) else candidate
    }

    /** La tâche n° `index` est-elle faite pour la période en cours ? */
    fun isDone(settings: ChatSettings, index: Int, now: LocalDateTime): Boolean =
        index in settings.done[periodKey(settings.ruleFor(index), now)].orEmpty()

    /** Les tâches faites en ce moment (numéros). */
    fun doneNow(settings: ChatSettings, now: LocalDateTime): Set<Int> =
        settings.chores.indices.filter { isDone(settings, it, now) }.toSet()

    private val days = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")

    fun dayName(day: Int): String = days[(day.coerceIn(1, 7)) - 1]

    /** « 1 fois par semaine · samedi 18 h » etc. */
    fun describe(rule: ChoreRule): String = when (rule.freq) {
        ChoreFreq.Hebdomadaire -> "1 fois par semaine · ${dayName(rule.day)} ${rule.hour} h"
        else -> rule.freq.label
    }
}
