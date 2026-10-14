package id.web.agentvaedah.strideway.core.weather

enum class WeatherKind(val label: String) {
    CLEAR("Cerah"),
    PARTLY_CLOUDY("Berawan sebagian"),
    CLOUDY("Berawan"),
    FOG("Berkabut"),
    DRIZZLE("Gerimis"),
    RAIN("Hujan"),
    THUNDERSTORM("Badai petir"),
    UNKNOWN("Tidak diketahui"),
}

data class CurrentWeather(
    val tempC: Double,
    val feelsLikeC: Double,
    val humidityPct: Int,
    val windKmh: Double,
    val windGustKmh: Double?,
    val windDirDeg: Double,
    val pressureHpa: Double?,
    val uvIndex: Double?,
    val precipitationMm: Double,
    val kind: WeatherKind,
    val isDay: Boolean,
)

data class HourlyWeather(val timeMs: Long, val tempC: Double, val rainProbPct: Int, val kind: WeatherKind, val windKmh: Double)

data class DailyWeather(
    val dateEpochDay: Long,
    val minC: Double,
    val maxC: Double,
    val rainProbPct: Int,
    val kind: WeatherKind,
    val sunriseMs: Long?,
    val sunsetMs: Long?,
    val uvMax: Double?,
)

data class WeatherReport(
    val fetchedMs: Long,
    val placeLat: Double,
    val placeLon: Double,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
)

object WeatherCodes {
    /** WMO weather interpretation codes as used by Open-Meteo. */
    fun kind(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82, 71, 73, 75, 77, 85, 86 -> WeatherKind.RAIN
        95, 96, 99 -> WeatherKind.THUNDERSTORM
        else -> WeatherKind.UNKNOWN
    }

    private val directions = listOf("Utara", "Timur Laut", "Timur", "Tenggara", "Selatan", "Barat Daya", "Barat", "Barat Laut")

    /** Meteorological wind direction is where the wind blows from. */
    fun windFrom(deg: Double): String = "dari " + directions[((((deg % 360) + 360) % 360 + 22.5) / 45.0).toInt() % 8]

    /** Short ride-planning notes, most serious first. Always returns at least one line. */
    fun rideAdvice(tempC: Double, rainProbPct: Int, uvIndex: Double?, windKmh: Double, kind: WeatherKind): List<String> {
        val out = ArrayList<String>()
        if (kind == WeatherKind.THUNDERSTORM) out += "Ada potensi petir. Tunda ride atau pilih rute dekat tempat berteduh."
        if (tempC >= 33) out += "Sangat panas. Bawa cairan lebih banyak dan elektrolit, hindari tengah hari."
        else if (tempC >= 30) out += "Cuaca panas. Minum lebih sering dari biasanya."
        if (rainProbPct >= 60 || kind == WeatherKind.RAIN) out += "Kemungkinan hujan tinggi. Bawa jas hujan dan nyalakan lampu."
        else if (rainProbPct >= 30) out += "Mungkin hujan. Siapkan jas hujan tipis."
        if ((uvIndex ?: 0.0) >= 8) out += "Indeks UV sangat tinggi. Pakai tabir surya dan kacamata."
        if (windKmh >= 30) out += "Angin kencang. Kurangi kecepatan di turunan dan jalan terbuka."
        if (out.isEmpty()) out += "Kondisi bagus untuk latihan."
        return out
    }
}
