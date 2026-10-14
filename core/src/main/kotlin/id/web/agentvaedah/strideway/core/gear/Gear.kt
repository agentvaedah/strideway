package id.web.agentvaedah.strideway.core.gear

import id.web.agentvaedah.strideway.core.model.Sport

/**
 * Trackable bike parts. Default intervals are common workshop rules of thumb for road riding in a wet,
 * dusty climate; riders adjust them per part.
 */
enum class ComponentType(val label: String, val defaultIntervalKm: Double) {
    SHIFTER("Shifter", 10_000.0),
    CRANKSET("Crankset", 15_000.0),
    BOTTOM_BRACKET("Bottom bracket", 8_000.0),
    PEDAL("Pedal", 10_000.0),
    CHAIN("Rantai", 3_000.0),
    CASSETTE("Cassette / sprocket", 9_000.0),
    REAR_DERAILLEUR("Rear derailleur (RD)", 10_000.0),
    FRONT_DERAILLEUR("Front derailleur (FD)", 10_000.0),
    PULLEY_WHEELS("Pulley wheels", 6_000.0),
    HUB("Hub & freehub", 8_000.0),
    RIMS("Velg (rims)", 15_000.0),
    SPOKES("Jari-jari (spokes)", 10_000.0),
    TIRES("Ban luar & ban dalam", 4_000.0),
    BRAKES("Kaliper rem / disc rotor", 2_000.0),
    BRAKE_LEVERS("Tuas rem (brake levers)", 15_000.0),
}

data class GearComponent(
    val id: Long,
    val type: ComponentType,
    val installMs: Long,
    /** Kilometres already on the part when it was fitted, e.g. a used wheel. */
    val kmAtInstall: Double,
    val serviceIntervalKm: Double,
)

enum class GearEventKind(val label: String) { SERVICED("Servis"), REPLACED("Ganti baru") }

data class GearEvent(val kind: GearEventKind, val atMs: Long)

data class GearRide(val sport: Sport, val startMs: Long, val distanceM: Double)

enum class GearLevel { OK, DUE_SOON, OVERDUE }

data class GearStatus(
    val component: GearComponent,
    val totalKm: Double,
    val kmSinceService: Double,
    val remainingKm: Double,
    val fraction: Double,
    val level: GearLevel,
)

object Gear {
    private const val DUE_SOON = 0.85

    fun status(c: GearComponent, events: List<GearEvent>, rides: List<GearRide>): GearStatus {
        val lastReplaced = events.filter { it.kind == GearEventKind.REPLACED }.maxOfOrNull { it.atMs }
        val lastService = events.maxOfOrNull { it.atMs }
        val lifeStart = lastReplaced ?: c.installMs
        val counterStart = maxOf(c.installMs, lastService ?: c.installMs)
        fun km(since: Long) = rides.filter { it.sport == Sport.RIDE && it.startMs >= since }.sumOf { it.distanceM } / 1000.0
        val base = if (lastReplaced == null) c.kmAtInstall else 0.0
        val total = base + km(lifeStart)
        // A used part's earlier km count toward its life but not toward the next service, whose history is unknown.
        val since = km(counterStart)
        val fraction = if (c.serviceIntervalKm > 0) since / c.serviceIntervalKm else 0.0
        return GearStatus(
            component = c,
            totalKm = total,
            kmSinceService = since,
            remainingKm = (c.serviceIntervalKm - since).coerceAtLeast(0.0),
            fraction = fraction,
            level = when {
                fraction >= 1.0 -> GearLevel.OVERDUE
                fraction >= DUE_SOON -> GearLevel.DUE_SOON
                else -> GearLevel.OK
            },
        )
    }

    /** Most worn first. */
    fun byUrgency(list: List<GearStatus>): List<GearStatus> = list.sortedByDescending { it.fraction }
}
