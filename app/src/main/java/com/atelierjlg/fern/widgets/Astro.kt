package com.atelierjlg.fern.widgets

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Calculs du ciel, faits sur le téléphone (pas besoin d'internet).
 * Formules de la NOAA (lever / coucher du soleil) et âge de la lune.
 */
object Astro {

    data class SunTimes(val sunrise: LocalTime?, val sunset: LocalTime?)

    /** Lever et coucher du soleil à une date et un lieu (null en nuit/jour polaire). */
    fun sunTimes(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes {
        val g = 2 * PI / 365 * (date.dayOfYear - 1)
        val eqTime = 229.18 * (
            0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
                0.014615 * cos(2 * g) - 0.040849 * sin(2 * g)
            )
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) -
            0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val lat = Math.toRadians(latitude)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
        if (cosHa < -1 || cosHa > 1) return SunTimes(null, null)
        val ha = Math.toDegrees(acos(cosHa))
        fun toLocal(utcMinutes: Double): LocalTime =
            date.atStartOfDay().atOffset(ZoneOffset.UTC)
                .plusSeconds((utcMinutes * 60).toLong())
                .atZoneSameInstant(zone)
                .toLocalTime()
        return SunTimes(
            sunrise = toLocal(720 - 4 * (longitude + ha) - eqTime),
            sunset = toLocal(720 - 4 * (longitude - ha) - eqTime),
        )
    }

    /** L'âge de la lune entre 0 et 1 : 0 = nouvelle lune, 0,5 = pleine lune. */
    fun moonPhase(epochMillis: Long): Double {
        val julianDay = epochMillis / 86_400_000.0 + 2_440_587.5
        val cycles = (julianDay - 2_451_550.1) / 29.530588853
        return cycles - floor(cycles)
    }

    /** La part éclairée du disque lunaire (0 à 1). */
    fun moonIllumination(phase: Double): Double = (1 - cos(2 * PI * phase)) / 2

    fun moonPhaseName(phase: Double): String = when {
        phase < 0.03 || phase >= 0.97 -> "Nouvelle lune"
        phase < 0.22 -> "Premier croissant"
        phase < 0.28 -> "Premier quartier"
        phase < 0.47 -> "Gibbeuse croissante"
        phase < 0.53 -> "Pleine lune"
        phase < 0.72 -> "Gibbeuse décroissante"
        phase < 0.78 -> "Dernier quartier"
        else -> "Dernier croissant"
    }
}
