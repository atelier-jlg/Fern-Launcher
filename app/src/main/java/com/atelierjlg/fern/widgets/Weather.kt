package com.atelierjlg.fern.widgets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Les grandes familles de temps, chacune avec son petit dessin en pixel art. */
enum class WeatherKind(val label: String) {
    Soleil("Soleil"),
    Eclaircies("Éclaircies"),
    Nuages("Nuageux"),
    Brouillard("Brouillard"),
    Bruine("Bruine"),
    Pluie("Pluie"),
    Neige("Neige"),
    Orage("Orage"),
}

/** La météo du moment et du jour (ce qu'affiche le widget). */
@Serializable
data class Weather(
    val temperature: Int,
    val code: Int,
    val isDay: Boolean,
    val min: Int,
    val max: Int,
    /** Code du temps de demain (pour « Demain : pluie »). */
    val tomorrowCode: Int? = null,
    val tomorrowMax: Int? = null,
    /** Quand on l'a récupérée (millisecondes). */
    val fetchedAt: Long = 0,
) {
    val kind: WeatherKind get() = WeatherCodes.kindOf(code)
}

/**
 * Météo via Open-Meteo : gratuit, sans compte ni clé, sans pistage.
 * Les « codes WMO » sont une norme internationale (0 = ciel clair, 61 = pluie faible…).
 */
object WeatherCodes {

    fun kindOf(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.Soleil
        1, 2 -> WeatherKind.Eclaircies
        3 -> WeatherKind.Nuages
        45, 48 -> WeatherKind.Brouillard
        51, 53, 55, 56, 57 -> WeatherKind.Bruine
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.Pluie
        71, 73, 75, 77, 85, 86 -> WeatherKind.Neige
        95, 96, 99 -> WeatherKind.Orage
        else -> WeatherKind.Nuages
    }

    fun url(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2"

    private val json = Json { ignoreUnknownKeys = true }

    /** Lit la réponse JSON d'Open-Meteo (null si elle est incomplète). */
    fun parse(body: String, now: Long = System.currentTimeMillis()): Weather? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val current = root["current"]!!.jsonObject
        val daily = root["daily"]!!.jsonObject
        fun JsonObject.ints(key: String) = this[key]!!.jsonArray.map { it.jsonPrimitive.double.roundToInt() }
        val codes = daily.ints("weather_code")
        val maxs = daily.ints("temperature_2m_max")
        val mins = daily.ints("temperature_2m_min")
        Weather(
            temperature = current["temperature_2m"]!!.jsonPrimitive.double.roundToInt(),
            code = current["weather_code"]!!.jsonPrimitive.int,
            isDay = current["is_day"]!!.jsonPrimitive.int == 1,
            min = mins[0],
            max = maxs[0],
            tomorrowCode = codes.getOrNull(1),
            tomorrowMax = maxs.getOrNull(1),
            fetchedAt = now,
        )
    }.getOrNull()

    /** Le dernier problème de téléchargement (null = tout va bien), affiché dans Paramètres → Lieu. */
    @Volatile
    var lastError: String? = null
        private set

    /** Télécharge la météo (à appeler hors de l'écran : Dispatchers.IO). */
    fun fetch(latitude: Double, longitude: Double): Weather? = runCatching {
        val connection = URL(url(latitude, longitude)).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "FernLauncher")
        try {
            val code = connection.responseCode
            if (code != 200) {
                lastError = "Open-Meteo a répondu HTTP $code"
                return null
            }
            val weather = parse(connection.inputStream.bufferedReader().use { it.readText() })
            lastError = if (weather == null) "Réponse d'Open-Meteo illisible" else null
            weather
        } finally {
            connection.disconnect()
        }
    }.getOrElse { e ->
        lastError = "${e.javaClass.simpleName} : ${e.message ?: "?"}"
        null
    }
}
