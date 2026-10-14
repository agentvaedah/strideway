package id.web.agentvaedah.strideway.data.remote.weather

import id.web.agentvaedah.strideway.core.weather.CurrentWeather
import id.web.agentvaedah.strideway.core.weather.DailyWeather
import id.web.agentvaedah.strideway.core.weather.HourlyWeather
import id.web.agentvaedah.strideway.core.weather.WeatherCodes
import id.web.agentvaedah.strideway.core.weather.WeatherReport
import id.web.agentvaedah.strideway.data.remote.StridewayJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface WeatherState {
    data object Idle : WeatherState
    data object Loading : WeatherState
    data class Ready(val report: WeatherReport) : WeatherState
    data class Failed(val message: String) : WeatherState
}

/**
 * Open-Meteo forecast: free, no API key, CC BY 4.0. One cached report is shared by the map badge,
 * the weather screen and the fuel planner; it refreshes when older than [MAX_AGE_MS] or ~5 km away.
 */
@Singleton
class WeatherRepository @Inject constructor(private val http: HttpClient) {
    private val _state = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private val lock = Mutex()

    suspend fun refresh(lat: Double, lon: Double, force: Boolean = false): WeatherState = lock.withLock {
        val cached = (_state.value as? WeatherState.Ready)?.report
        val fresh = cached != null &&
            System.currentTimeMillis() - cached.fetchedMs < MAX_AGE_MS &&
            abs(cached.placeLat - lat) < 0.05 && abs(cached.placeLon - lon) < 0.05
        if (fresh && !force) return@withLock _state.value
        if (cached == null) _state.value = WeatherState.Loading
        val next = try {
            WeatherState.Ready(fetch(lat, lon))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep showing the last report rather than blanking the badge on a dropped connection.
            if (cached != null) WeatherState.Ready(cached) else WeatherState.Failed("Cuaca tidak bisa dimuat. Periksa koneksi internet.")
        }
        _state.value = next
        next
    }

    private suspend fun fetch(lat: Double, lon: Double): WeatherReport {
        val response = http.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", lat)
            parameter("longitude", lon)
            parameter(
                "current",
                "temperature_2m,apparent_temperature,relative_humidity_2m,is_day,precipitation,weather_code," +
                    "surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index",
            )
            parameter("hourly", "temperature_2m,precipitation_probability,weather_code,wind_speed_10m")
            parameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max")
            parameter("timezone", "auto")
            parameter("forecast_days", 7)
            parameter("forecast_hours", 24)
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        val body = StridewayJson.decodeFromString(OpenMeteoResponse.serializer(), response.bodyAsText())
        val zone = runCatching { ZoneId.of(body.timezone) }.getOrDefault(ZoneId.systemDefault())
        fun ms(iso: String) = LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

        val c = body.current
        val h = body.hourly
        val d = body.daily
        return WeatherReport(
            fetchedMs = System.currentTimeMillis(),
            placeLat = lat,
            placeLon = lon,
            current = CurrentWeather(
                tempC = c.temperature_2m,
                feelsLikeC = c.apparent_temperature ?: c.temperature_2m,
                humidityPct = c.relative_humidity_2m?.roundToInt() ?: 0,
                windKmh = c.wind_speed_10m ?: 0.0,
                windGustKmh = c.wind_gusts_10m,
                windDirDeg = c.wind_direction_10m ?: 0.0,
                pressureHpa = c.surface_pressure,
                uvIndex = c.uv_index,
                precipitationMm = c.precipitation ?: 0.0,
                kind = WeatherCodes.kind(c.weather_code ?: -1),
                isDay = (c.is_day ?: 1) == 1,
            ),
            hourly = h?.time.orEmpty().indices.map { i ->
                HourlyWeather(
                    timeMs = ms(h!!.time[i]),
                    tempC = h.temperature_2m.getOrNull(i) ?: 0.0,
                    rainProbPct = h.precipitation_probability.getOrNull(i) ?: 0,
                    kind = WeatherCodes.kind(h.weather_code.getOrNull(i) ?: -1),
                    windKmh = h.wind_speed_10m.getOrNull(i) ?: 0.0,
                )
            },
            daily = d?.time.orEmpty().indices.map { i ->
                DailyWeather(
                    dateEpochDay = LocalDate.parse(d!!.time[i]).toEpochDay(),
                    minC = d.temperature_2m_min.getOrNull(i) ?: 0.0,
                    maxC = d.temperature_2m_max.getOrNull(i) ?: 0.0,
                    rainProbPct = d.precipitation_probability_max.getOrNull(i) ?: 0,
                    kind = WeatherCodes.kind(d.weather_code.getOrNull(i) ?: -1),
                    sunriseMs = d.sunrise.getOrNull(i)?.let(::ms),
                    sunsetMs = d.sunset.getOrNull(i)?.let(::ms),
                    uvMax = d.uv_index_max.getOrNull(i),
                )
            },
        )
    }

    private companion object {
        const val MAX_AGE_MS = 20 * 60_000L
    }
}

@Suppress("PropertyName")
@Serializable
private data class OpenMeteoResponse(
    val timezone: String = "UTC",
    val current: Current,
    val hourly: Hourly? = null,
    val daily: Daily? = null,
) {
    @Serializable
    data class Current(
        val temperature_2m: Double,
        val apparent_temperature: Double? = null,
        val relative_humidity_2m: Double? = null,
        val is_day: Int? = null,
        val precipitation: Double? = null,
        val weather_code: Int? = null,
        val surface_pressure: Double? = null,
        val wind_speed_10m: Double? = null,
        val wind_direction_10m: Double? = null,
        val wind_gusts_10m: Double? = null,
        val uv_index: Double? = null,
    )

    @Serializable
    data class Hourly(
        val time: List<String> = emptyList(),
        val temperature_2m: List<Double?> = emptyList(),
        val precipitation_probability: List<Int?> = emptyList(),
        val weather_code: List<Int?> = emptyList(),
        val wind_speed_10m: List<Double?> = emptyList(),
    )

    @Serializable
    data class Daily(
        val time: List<String> = emptyList(),
        val weather_code: List<Int?> = emptyList(),
        val temperature_2m_max: List<Double?> = emptyList(),
        val temperature_2m_min: List<Double?> = emptyList(),
        val precipitation_probability_max: List<Int?> = emptyList(),
        val sunrise: List<String?> = emptyList(),
        val sunset: List<String?> = emptyList(),
        val uv_index_max: List<Double?> = emptyList(),
    )
}
