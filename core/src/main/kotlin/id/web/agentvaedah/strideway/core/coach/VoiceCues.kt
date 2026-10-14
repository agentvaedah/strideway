package id.web.agentvaedah.strideway.core.coach

import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.physio.Zone
import id.web.agentvaedah.strideway.core.physio.Zones
import id.web.agentvaedah.strideway.core.route.RouteEvent
import id.web.agentvaedah.strideway.core.route.TurnDirection
import id.web.agentvaedah.strideway.core.telemetry.LiveMetrics
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class CueKind { SPLIT, PAUSE, HEART_RATE, ROUTE_ALERT }

data class Cue(val kind: CueKind, val text: String)

data class CueConfig(
    val split: Boolean = true,
    val pause: Boolean = true,
    val heartRate: Boolean = true,
    val route: Boolean = true,
)

/** Turns successive live metrics into spoken Indonesian sentences. Stateful: one instance per recording. */
class VoiceCues(
    private val sport: Sport,
    private val hrZones: List<Zone>,
    private val config: CueConfig = CueConfig(),
    private val hrCooldownMs: Long = 120_000,
) {
    private var lastKm = -1
    private var lastSplitMovingMs = 0L
    private var wasAutoPaused: Boolean? = null
    private var wasManualPaused = false
    private var wasHighZone = false
    private var lastHrCueMs: Long? = null

    fun update(m: LiveMetrics, nowMs: Long): List<Cue> {
        val out = ArrayList<Cue>()

        val km = (m.distanceM / 1000.0).toInt()
        if (lastKm < 0) {
            lastKm = km
            lastSplitMovingMs = m.movingMs
        } else if (km > lastKm) {
            if (config.split) out += Cue(CueKind.SPLIT, splitText(km, m))
            lastKm = km
            lastSplitMovingMs = m.movingMs
        }

        val prevAuto = wasAutoPaused
        if (prevAuto != null && config.pause) {
            when {
                m.manualPaused && !wasManualPaused -> out += Cue(CueKind.PAUSE, "Rekaman dijeda")
                m.autoPaused && !prevAuto && !m.manualPaused -> out += Cue(CueKind.PAUSE, "Jeda otomatis")
                !m.paused && (prevAuto || wasManualPaused) -> out += Cue(CueKind.PAUSE, "Lanjut merekam")
            }
        }
        wasAutoPaused = m.autoPaused
        wasManualPaused = m.manualPaused

        val hr = m.heartRate
        val zone = hr?.let { Zones.zoneOf(it, hrZones) } ?: 0
        val high = zone >= 4
        if (high && !wasHighZone && config.heartRate) {
            val last = lastHrCueMs
            if (last == null || nowMs - last >= hrCooldownMs) {
                out += Cue(CueKind.HEART_RATE, "Detak jantung $hr, zona $zone. Atur napas")
                lastHrCueMs = nowMs
            }
        }
        wasHighZone = high

        return out
    }

    fun route(e: RouteEvent): Cue? {
        if (!config.route) return null
        val text = when (e) {
            is RouteEvent.LeftRoute -> "Keluar rute, ${roundTen(e.distanceM)} meter dari jalur"
            RouteEvent.BackOnRoute -> "Kembali di rute"
            is RouteEvent.TurnAhead -> {
                val dir = if (e.direction == TurnDirection.LEFT) "kiri" else "kanan"
                "Belok $dir dalam ${roundTen(e.inM)} meter"
            }
        }
        return Cue(CueKind.ROUTE_ALERT, text)
    }

    private fun splitText(km: Int, m: LiveMetrics): String {
        val head = "$km kilometer, waktu ${spokenDuration(m.movingMs)}"
        return if (sport == Sport.RUN) {
            val splitMs = m.movingMs - lastSplitMovingMs
            "$head. Pace kilometer terakhir ${spokenDuration(splitMs)} per kilometer"
        } else {
            val avg = String.format(Locale.US, "%.1f", m.avgSpeedKmh).replace('.', ',')
            "$head. Rata-rata $avg kilometer per jam"
        }
    }

    private fun spokenDuration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000.0).roundToLong()
        val h = total / 3600
        val min = (total % 3600) / 60
        val s = total % 60
        return buildList {
            if (h > 0) add("$h jam")
            if (min > 0) add("$min menit")
            if (s > 0 || (h == 0L && min == 0L)) add("$s detik")
        }.joinToString(" ")
    }

    private fun roundTen(m: Double): Int = ((m / 10.0).roundToInt() * 10).coerceAtLeast(10)

    companion object {
        /** Spoken when Start is pressed, before the first metric cue. */
        fun startLine(sport: Sport): String =
            if (sport == Sport.RUN) "Sudah siap lari? Gas, hati-hati di jalan!" else "Sudah siap gowes? Gas, hati-hati di jalan!"

        /** Spoken when the rider stops and saves or discards. */
        const val STOP_LINE = "Rekaman berhenti. Selamat istirahat."
    }
}
