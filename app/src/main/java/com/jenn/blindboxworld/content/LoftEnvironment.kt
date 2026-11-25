package com.jenn.blindboxworld.content

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

internal enum class SkyPhase(val title: String) { SUNRISE("Sunrise"), DAY("Daytime"), SUNSET("Sunset"), NIGHT("Night") }
internal enum class Weather(val title: String, val overcast: Boolean = false) {
    UNKNOWN(""), CLEAR("Clear"), PARTLY_CLOUDY("Partly cloudy"), CLOUDY("Overcast",true),
    FOG("Fog",true), RAIN("Rain",true), SNOW("Snow",true), THUNDER("Thunderstorms",true)
}

internal fun weatherFrom(code: Int) = when (code) {
    0, 1 -> Weather.CLEAR
    2 -> Weather.PARTLY_CLOUDY
    3 -> Weather.CLOUDY
    45, 48 -> Weather.FOG
    71, 73, 75, 77, 85, 86 -> Weather.SNOW
    in 51..67, in 80..82 -> Weather.RAIN
    in 95..99 -> Weather.THUNDER
    else -> Weather.UNKNOWN
}

private val sanFranciscoZone = ZoneId.of("America/Los_Angeles")
internal data class SolarDay(val sunrise: Long, val sunset: Long)
internal data class Forecast(val weather: Weather, val sun: List<SolarDay>, val daylight: Boolean, val observedAt: Long)
internal data class LoftSky(val phase: SkyPhase, val weather: Weather, val sunProgress: Float, val city: String, val clock: String) {
    val look get() = phase.name.lowercase(Locale.ROOT) + if (weather.overcast) "-cloud" else ""
}
private val clockFormat = DateTimeFormatter.ofPattern("h:mm a z",Locale.US)

internal fun loftSky(now: ZonedDateTime, city: String, forecast: Forecast?): LoftSky {
    val epoch = now.toEpochSecond()
    val today = now.toLocalDate()
    val events = forecast?.sun?.firstOrNull { Instant.ofEpochSecond(it.sunrise).atZone(now.zone).toLocalDate() == today }
    val rise = events?.sunrise ?: today.atTime(6,30).atZone(now.zone).toEpochSecond()
    val set = events?.sunset ?: today.atTime(18,0).atZone(now.zone).toEpochSecond()
    val twilight = 35L * 60
    val phase = when {
        forecast != null && forecast.sun.isEmpty() -> if (forecast.daylight) SkyPhase.DAY else SkyPhase.NIGHT
        epoch in (rise-twilight)..(rise+twilight) -> SkyPhase.SUNRISE
        epoch in (set-twilight)..(set+twilight) -> SkyPhase.SUNSET
        epoch in rise..set -> SkyPhase.DAY
        else -> SkyPhase.NIGHT
    }
    val progress = ((epoch-rise).toFloat()/(set-rise).coerceAtLeast(1L)).coerceIn(0f,1f)
    return LoftSky(phase,forecast?.weather ?: Weather.UNKNOWN,progress,city,now.format(clockFormat))
}

private fun readJson(address: String): JSONObject {
    val connection = URL(address).openConnection() as HttpsURLConnection
    return try {
        connection.connectTimeout = 6000
        connection.readTimeout = 7000
        connection.setRequestProperty("Accept","application/json")
        connection.setRequestProperty("User-Agent","BlindboxWorld/1.0")
        connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } finally { connection.disconnect() }
}

// The window overlooks Rincon Hill regardless of where the headset connects.
private fun forecastForSkyline(): Forecast = parseForecast(readJson(
    "https://api.open-meteo.com/v1/forecast?latitude=37.79&longitude=-122.39" +
        "&current=weather_code,is_day,rain,showers&daily=sunrise,sunset" +
        "&timezone=America%2FLos_Angeles&timeformat=unixtime&forecast_days=2"
))

private fun parseForecast(result: JSONObject): Forecast {
    val current = result.getJSONObject("current")
    val daily = result.getJSONObject("daily")
    val rises = daily.getJSONArray("sunrise")
    val sets = daily.getJSONArray("sunset")
    val days = (0 until rises.length()).mapNotNull {
        val rise = rises.optLong(it,0)
        val set = sets.optLong(it,0)
        if (rise > 0 && set > rise) SolarDay(rise,set) else null
    }
    val coded = weatherFrom(current.getInt("weather_code"))
    val raining = current.optDouble("rain", 0.0) + current.optDouble("showers", 0.0) > 0
    val weather = if (raining && coded != Weather.SNOW && coded != Weather.THUNDER) Weather.RAIN else coded
    return Forecast(weather, days, current.getInt("is_day") == 1, current.getLong("time"))
}

// The PICO emulator can resume with a stale clock and no DNS. A scoped preview file lets
// the desktop supply the same current city forecast without changing Android system settings.
private data class DesktopSample(val offsetMs: Long, val forecast: Forecast?)
private fun desktopSample(context: Context): DesktopSample? {
    if (Build.HARDWARE != "ranchu" && Build.HARDWARE != "goldfish") return null
    return runCatching {
        val data = JSONObject(File(context.filesDir,"loft-preview.json").readText())
        require(data.getInt("boot") == Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT))
        val since = SystemClock.elapsedRealtime()-data.getLong("elapsed")
        require(since in 0..86_400_000L)
        val localNow = data.getLong("epoch")+since
        val forecast = if (data.optString("city") == "San Francisco" && data.has("current"))
            parseForecast(data) else null
        DesktopSample(localNow - System.currentTimeMillis(), forecast)
    }.getOrNull()
}

@Composable
internal fun rememberLoftSky(active: Boolean): LoftSky {
    var weather by remember { mutableStateOf<Forecast?>(null) }
    val context = LocalContext.current.applicationContext
    var desktop by remember { mutableStateOf<DesktopSample?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            desktop = withContext(Dispatchers.IO) { desktopSample(context) }
            val localNow = Instant.ofEpochMilli(System.currentTimeMillis()+(desktop?.offsetMs ?: 0L))
            now = localNow
            delay(60_000L - localNow.toEpochMilli().mod(60_000L))
        }
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            val next = withContext(Dispatchers.IO) { runCatching { forecastForSkyline() }.getOrNull() }
            if (next != null) weather = next
            delay(if (next == null) 5 * 60_000L else 20 * 60_000L)
        }
    }
    val forecast = listOfNotNull(weather, desktop?.forecast)
        .filter { now.epochSecond - it.observedAt in -60L..3600L }.maxByOrNull { it.observedAt }
    LaunchedEffect(forecast) {
        if (forecast != null) android.util.Log.i("LoftWeather", "Rincon Hill: ${forecast.weather} at ${forecast.observedAt}")
    }
    return loftSky(now.atZone(sanFranciscoZone), "San Francisco", forecast)
}
