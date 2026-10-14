package id.web.agentvaedah.strideway.core.route

import id.web.agentvaedah.strideway.core.geo.Geo
import id.web.agentvaedah.strideway.core.model.LatLon
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ManeuverType(private val words: String) {
    STRAIGHT("lurus"),
    SLIGHT_LEFT("serong kiri"),
    LEFT("belok kiri"),
    SHARP_LEFT("belok tajam ke kiri"),
    SLIGHT_RIGHT("serong kanan"),
    RIGHT("belok kanan"),
    SHARP_RIGHT("belok tajam ke kanan"),
    KEEP_LEFT("tetap di kiri"),
    KEEP_RIGHT("tetap di kanan"),
    U_TURN("putar balik"),
    ROUNDABOUT("masuk bundaran"),
    ;

    val isLeft: Boolean get() = this in setOf(SLIGHT_LEFT, LEFT, SHARP_LEFT, KEEP_LEFT)

    fun phrase(exit: Int): String = if (this == ROUNDABOUT && exit > 0) "$words, ambil keluar ke-$exit" else words

    companion object {
        /** BRouter VoiceHint command codes. */
        fun fromBrouter(cmd: Int): ManeuverType? = when (cmd) {
            1 -> STRAIGHT
            2 -> LEFT
            3 -> SLIGHT_LEFT
            4 -> SHARP_LEFT
            5 -> RIGHT
            6 -> SLIGHT_RIGHT
            7 -> SHARP_RIGHT
            8, 17 -> KEEP_LEFT
            9, 18 -> KEEP_RIGHT
            10, 11, 15 -> U_TURN
            13, 14 -> ROUNDABOUT
            else -> null
        }
    }
}

/** A turn at route point [pointIndex]. */
data class Maneuver(val pointIndex: Int, val type: ManeuverType, val exit: Int = 0, val street: String? = null)

data class UpcomingManeuver(val maneuver: Maneuver, val inM: Double)

sealed interface NavEvent {
    fun text(): String

    /** Early warning, about 200 m out. */
    data class Prepare(val maneuver: Maneuver, val inM: Double) : NavEvent {
        override fun text(): String {
            val m = ((inM / 50.0).roundToInt() * 50).coerceAtLeast(50)
            val street = maneuver.street?.let { " ke $it" }.orEmpty()
            return "Dalam $m meter, ${maneuver.type.phrase(maneuver.exit)}$street"
        }
    }

    /** At the turn. */
    data class Now(val maneuver: Maneuver) : NavEvent {
        override fun text(): String = maneuver.type.phrase(maneuver.exit).replaceFirstChar { it.uppercase() } + " sekarang"
    }

    data class OffRoute(val distanceM: Double) : NavEvent {
        override fun text(): String = "Keluar rute, ${((distanceM / 10).roundToInt() * 10).coerceAtLeast(10)} meter dari jalur"
    }

    data object BackOnRoute : NavEvent {
        override fun text(): String = "Kembali di rute"
    }

    data object Arrived : NavEvent {
        override fun text(): String = "Anda sudah sampai di tujuan"
    }
}

data class NavState(
    val next: UpcomingManeuver?,
    val after: UpcomingManeuver?,
    val progressM: Double,
    val remainingM: Double,
    /** Seconds to the end at the current speed; null when standing still. */
    val etaS: Int?,
    val distanceToRouteM: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
    val events: List<NavEvent>,
)

/**
 * Turn-by-turn on top of [RouteFollower]: knows the next two maneuvers, speaks each one twice (early and at
 * the turn), estimates arrival, and reports arrival once. Maneuvers come from BRouter when the route was
 * planned in the app, otherwise from [fromGeometry].
 */
class Navigator(
    private val route: List<LatLon>,
    maneuvers: List<Maneuver>,
    private val prepareM: Double = 200.0,
    private val nowM: Double = 30.0,
    private val arriveM: Double = 30.0,
) {
    private val follower = RouteFollower(route, announceM = 0.0)
    private val cum = DoubleArray(route.size).also { c ->
        for (i in 1 until route.size) c[i] = c[i - 1] + Geo.distanceM(route[i - 1], route[i])
    }
    private val total = if (route.isEmpty()) 0.0 else cum.last()
    private val turns = maneuvers.filter { it.pointIndex in route.indices }.sortedBy { it.pointIndex }
    private val prepared = HashSet<Int>()
    private val announcedNow = HashSet<Int>()
    private var arrived = false

    fun update(pos: LatLon, nowMs: Long, speedMps: Double): NavState {
        val s = follower.update(pos, nowMs)
        val events = ArrayList<NavEvent>()
        s.events.forEach { e ->
            when (e) {
                is RouteEvent.LeftRoute -> events += NavEvent.OffRoute(e.distanceM)
                RouteEvent.BackOnRoute -> events += NavEvent.BackOnRoute
                is RouteEvent.TurnAhead -> Unit
            }
        }
        val ahead = turns.withIndex().filter { cum[it.value.pointIndex] > s.progressM - 1.0 }
        val next = ahead.getOrNull(0)?.let { (i, m) -> Triple(i, m, cum[m.pointIndex] - s.progressM) }
        val after = ahead.getOrNull(1)?.let { (_, m) -> UpcomingManeuver(m, cum[m.pointIndex] - s.progressM) }
        if (!s.offRoute && next != null) {
            val (i, m, inM) = next
            if (inM <= nowM && announcedNow.add(i)) {
                prepared += i
                events += NavEvent.Now(m)
            } else if (inM <= prepareM && inM > nowM && prepared.add(i)) {
                events += NavEvent.Prepare(m, inM)
            }
        }
        if (!arrived && route.isNotEmpty() && total - s.progressM <= arriveM && Geo.distanceM(pos, route.last()) <= arriveM) {
            arrived = true
            events += NavEvent.Arrived
        }
        val remaining = (total - s.progressM).coerceAtLeast(0.0)
        return NavState(
            next = next?.let { UpcomingManeuver(it.second, it.third) },
            after = after,
            progressM = s.progressM,
            remainingM = remaining,
            etaS = if (speedMps > 0.5) (remaining / speedMps).roundToInt() else null,
            distanceToRouteM = s.distanceToRouteM,
            offRoute = s.offRoute,
            arrived = arrived,
            events = events,
        )
    }

    companion object {
        private const val LOOK_M = 20.0

        /** Maneuvers read from the line's shape, for imported or hand-drawn routes. */
        fun fromGeometry(route: List<LatLon>): List<Maneuver> {
            if (route.size < 3) return emptyList()
            val cum = DoubleArray(route.size)
            for (i in 1 until route.size) cum[i] = cum[i - 1] + Geo.distanceM(route[i - 1], route[i])
            val raw = ArrayList<Pair<Int, Double>>()
            for (i in 1 until route.size - 1) {
                var j = i - 1
                while (j > 0 && cum[i] - cum[j] < LOOK_M) j--
                var k = i + 1
                while (k < route.size - 1 && cum[k] - cum[i] < LOOK_M) k++
                val inB = Geo.bearingDeg(route[j], route[i])
                val outB = Geo.bearingDeg(route[i], route[k])
                val delta = ((outB - inB + 540.0) % 360.0) - 180.0
                if (abs(delta) >= 25.0) raw += i to delta
            }
            val out = ArrayList<Pair<Int, Double>>()
            for (c in raw) {
                val last = out.lastOrNull()
                if (last != null && cum[c.first] - cum[last.first] <= LOOK_M * 1.5) {
                    if (abs(c.second) > abs(last.second)) out[out.lastIndex] = c
                } else {
                    out += c
                }
            }
            return out.map { (i, d) -> Maneuver(i, typeFor(d)) }
        }

        private fun typeFor(delta: Double): ManeuverType {
            val a = abs(delta)
            val left = delta < 0
            return when {
                a >= 160 -> ManeuverType.U_TURN
                a >= 120 -> if (left) ManeuverType.SHARP_LEFT else ManeuverType.SHARP_RIGHT
                a >= 50 -> if (left) ManeuverType.LEFT else ManeuverType.RIGHT
                else -> if (left) ManeuverType.SLIGHT_LEFT else ManeuverType.SLIGHT_RIGHT
            }
        }
    }
}
