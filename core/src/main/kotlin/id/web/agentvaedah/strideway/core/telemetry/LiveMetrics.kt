package id.web.agentvaedah.strideway.core.telemetry

data class LiveMetrics(
    val elapsedMs: Long = 0,
    val movingMs: Long = 0,
    val distanceM: Double = 0.0,
    val speedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val altitudeM: Double? = null,
    val elevationGainM: Double = 0.0,
    val gradePercent: Double? = null,
    val heartRate: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val cadence: Int? = null,
    val powerWatts: Int? = null,
    val avgPowerWatts: Int? = null,
    val estimatedPowerWatts: Int? = null,
    val calories: Double = 0.0,
    val autoPaused: Boolean = false,
    val manualPaused: Boolean = false,
    val deadReckoning: Boolean = false,
) {
    val paused: Boolean get() = autoPaused || manualPaused
}
