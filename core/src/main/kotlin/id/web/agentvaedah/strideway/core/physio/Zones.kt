package id.web.agentvaedah.strideway.core.physio

import kotlin.math.roundToInt

data class Zone(val index: Int, val name: String, val low: Int, val high: Int)

object Zones {
    private val hrNames = listOf("Pemulihan", "Aerobik dasar", "Tempo", "Ambang", "Maksimal")
    private val hrBounds = listOf(0.5, 0.6, 0.7, 0.8, 0.9)

    private val powerNames = listOf(
        "Pemulihan aktif", "Endurance", "Tempo", "Ambang laktat", "VO2max", "Anaerobik", "Neuromuskular",
    )
    private val powerUpper = listOf(0.55, 0.75, 0.90, 1.05, 1.20, 1.50)

    /** Karvonen (heart rate reserve) when [hrRest] is known, otherwise percent of HRmax. */
    fun heartRate(hrMax: Int, hrRest: Int?): List<Zone> {
        fun at(pct: Double): Int =
            if (hrRest != null) (hrRest + pct * (hrMax - hrRest)).roundToInt() else (pct * hrMax).roundToInt()

        val lows = hrBounds.map(::at)
        return lows.mapIndexed { i, low ->
            val high = if (i == lows.lastIndex) hrMax else lows[i + 1] - 1
            Zone(i + 1, hrNames[i], low, high)
        }
    }

    fun power(ftp: Int): List<Zone> {
        val highs = powerUpper.map { (it * ftp).roundToInt() } + Int.MAX_VALUE
        return highs.mapIndexed { i, high ->
            val low = if (i == 0) 0 else highs[i - 1] + 1
            Zone(i + 1, powerNames[i], low, high)
        }
    }

    /** Returns 1-based zone index, or 0 when below the first zone. */
    fun zoneOf(value: Int, zones: List<Zone>): Int =
        zones.lastOrNull { value >= it.low }?.index ?: 0
}
