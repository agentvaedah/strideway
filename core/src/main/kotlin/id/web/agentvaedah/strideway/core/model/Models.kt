package id.web.agentvaedah.strideway.core.model

enum class Sport { RIDE, RUN }

enum class Gender { MALE, FEMALE }

data class Athlete(
    val gender: Gender = Gender.MALE,
    val heightCm: Double = 170.0,
    val weightKg: Double = 70.0,
    val age: Int = 30,
    val hrMax: Int = 190,
    val hrRest: Int = 60,
    val ftpWatts: Int = 200,
    val bikeWeightKg: Double = 9.0,
)

data class LatLon(val lat: Double, val lon: Double)

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val timeMs: Long? = null,
    val hr: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
) {
    val latLon: LatLon get() = LatLon(lat, lon)
}

data class Track(val name: String?, val sport: Sport?, val points: List<TrackPoint>)

data class ActivitySummary(
    val sport: Sport,
    val startMs: Long,
    val distanceM: Double,
    val movingMs: Long,
    val trimp: Double,
)
