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

internal data class LoftLocation(val city: String, val zone: ZoneId, val lat: Double, val lon: Double)
internal data class SolarDay(val sunrise: Long, val sunset: Long)
internal data class Forecast(val weather: Weather, val sun: List<SolarDay>, val daylight: Boolean)
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

private fun lookupCity(): LoftLocation {
    val j = readJson("https://ipwho.is/")
    require(j.optBoolean("success"))
    val city = j.getString("city").trim()
    require(city.isNotEmpty())
    return LoftLocation(city,ZoneId.of(j.getJSONObject("timezone").getString("id")),j.getDouble("latitude"),j.getDouble("longitude"))
}

private fun forecastFor(place: LoftLocation): Forecast {
    // Round to city scale before sending the weather request.
    val coords = String.format(Locale.ROOT,"latitude=%.2f&longitude=%.2f",place.lat,place.lon)
    val result = readJson("https://api.open-meteo.com/v1/forecast?$coords&current=weather_code,is_day&daily=sunrise,sunset&timezone=auto&timeformat=unixtime&forecast_days=2")
    return parseForecast(result)
}

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
    return Forecast(weatherFrom(current.getInt("weather_code")),days,current.getInt("is_day")==1)
}

// The PICO emulator can resume with a stale clock and no DNS. A scoped preview file lets
// the desktop supply the same current city forecast without changing Android system settings.
private data class DesktopSample(val offsetMs: Long, val city: String?, val zone: ZoneId?, val forecast: Forecast?)
private fun desktopSample(context: Context): DesktopSample? {
    if (Build.HARDWARE != "ranchu" && Build.HARDWARE != "goldfish") return null
    return runCatching {
        val data = JSONObject(File(context.filesDir,"loft-preview.json").readText())
        require(data.getInt("boot") == Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT))
        val since = SystemClock.elapsedRealtime()-data.getLong("elapsed")
        require(since in 0..86_400_000L)
        val localNow = data.getLong("epoch")+since
        val cityFresh = localNow/1000-data.optLong("located") in 0..21_600L
        val observed = data.optJSONObject("current")?.optLong("time") ?: 0L
        val forecast = if (cityFresh && localNow/1000-observed in 0..3600L) parseForecast(data) else null
        DesktopSample(localNow-System.currentTimeMillis(),data.optString("city").takeIf { cityFresh && it.isNotBlank() },
            data.optString("zone").takeIf { cityFresh && it.isNotBlank() }?.let(ZoneId::of),forecast)
    }.getOrNull()
}

@Composable
internal fun rememberLoftSky(active: Boolean): LoftSky? {
    var place by remember { mutableStateOf<LoftLocation?>(null) }
    var weather by remember { mutableStateOf<Forecast?>(null) }
    var lastCity by remember { mutableLongStateOf(-1L) }
    var lastWeather by remember { mutableLongStateOf(-1L) }
    val context = LocalContext.current.applicationContext
    var desktop by remember { mutableStateOf<DesktopSample?>(null) }
    var now by remember { mutableStateOf<Instant?>(null) }
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
            val at = SystemClock.elapsedRealtime()
            if (lastCity < 0 || at-lastCity >= 30*60_000L) {
                val next = withContext(Dispatchers.IO) { runCatching { lookupCity() }.getOrNull() }
                if (next != place) { weather = null; lastWeather = -1 }
                place = next
                if (next != null) lastCity = at
            }
            val city = place
            if (city != null && (lastWeather < 0 || at-lastWeather >= 20*60_000L)) {
                val next = withContext(Dispatchers.IO) { runCatching { forecastFor(city) }.getOrNull() }
                if (next != null || lastWeather < 0 || at-lastWeather >= 60*60_000L) weather = next
                if (next != null) lastWeather = at
            }
            delay(5*60_000L)
        }
    }
    val preview = desktop
    val zone = place?.zone ?: preview?.zone ?: ZoneId.systemDefault()
    val localForecast = weather ?: preview?.forecast?.takeIf { place == null || place?.city == preview.city }
    return now?.let { loftSky(it.atZone(zone),place?.city ?: preview?.city ?: "San Francisco",localForecast) }
}
