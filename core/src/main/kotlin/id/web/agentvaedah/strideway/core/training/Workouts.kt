package id.web.agentvaedah.strideway.core.training

import kotlin.math.roundToInt

/** Rider level. Sessions scale their main work by level; warm-up and cool-down stay the same. */
enum class Level(val label: String, val workScale: Double) {
    PEMULA("Pemula", 0.6),
    MENENGAH("Menengah", 1.0),
    LANJUT("Lanjut", 1.3),
    ELITE("Elite", 1.6),
}

sealed interface Target {
    /** Power as a fraction of FTP, e.g. 0.88..0.94 for sweet spot. */
    data class PowerPct(val low: Double, val high: Double) : Target
    data class HrZone(val zone: Int) : Target
    data object Free : Target

    fun watts(ftp: Int): IntRange? = when (this) {
        is PowerPct -> (low * ftp).roundToInt()..(high * ftp).roundToInt()
        else -> null
    }
}

data class Step(val durationS: Int, val target: Target, val cadence: IntRange? = null, val label: String, val work: Boolean = false)

data class Workout(val id: String, val name: String, val level: Level, val focus: String, val steps: List<Step>) {
    val durationS: Int get() = steps.sumOf { it.durationS }

    /** Same session for another level: work steps are lengthened or shortened, recoveries stay. */
    fun scaled(to: Level): Workout {
        val f = to.workScale / level.workScale
        return copy(
            level = to,
            steps = steps.map { if (it.work) it.copy(durationS = ((it.durationS * f) / 30.0).roundToInt().coerceAtLeast(1) * 30) else it },
        )
    }
}

/**
 * Structured sessions based on the classic Coggan power zones, usable from a first ride to national level:
 * the same session runs at a beginner's FTP and length or an elite's. Targets fall back to heart-rate zones
 * on the screen when there is no power meter.
 */
object WorkoutLibrary {
    private const val MIN = 60

    private fun wu(min: Int = 10) = Step(min * MIN, Target.PowerPct(0.50, 0.65), label = "Pemanasan")
    private fun cd(min: Int = 10) = Step(min * MIN, Target.PowerPct(0.40, 0.55), label = "Pendinginan")
    private fun work(min: Double, lo: Double, hi: Double, label: String, cad: IntRange? = null) =
        Step((min * MIN).roundToInt(), Target.PowerPct(lo, hi), cad, label, work = true)
    private fun rest(min: Double) = Step((min * MIN).roundToInt(), Target.PowerPct(0.45, 0.55), label = "Pulih")
    private fun repeat(n: Int, vararg block: Step) = List(n) { block.toList() }.flatten()

    val all: List<Workout> = listOf(
        Workout("recovery", "Pemulihan aktif", Level.PEMULA, "Pulih setelah sesi berat", listOf(work(40.0, 0.40, 0.55, "Santai, kayuh ringan", 85..95))),
        Workout("endurance", "Endurance Z2", Level.PEMULA, "Dasar aerobik", listOf(wu(), work(60.0, 0.56, 0.75, "Zona 2 stabil"), cd())),
        Workout("longride", "Long ride", Level.MENENGAH, "Daya tahan untuk event jarak jauh", listOf(wu(15), work(150.0, 0.56, 0.75, "Zona 2, makan dan minum teratur"), cd())),
        Workout("tempo", "Tempo 3x12", Level.MENENGAH, "Tenaga menanjak panjang", listOf(wu()) + repeat(3, work(12.0, 0.76, 0.87, "Tempo"), rest(4.0)) + cd()),
        Workout("sweetspot", "Sweet spot 2x20", Level.MENENGAH, "Menaikkan FTP dengan beban terkendali", listOf(wu(15)) + repeat(2, work(20.0, 0.88, 0.94, "Sweet spot"), rest(5.0)) + cd()),
        Workout("threshold", "Threshold 3x10", Level.LANJUT, "Ambang laktat", listOf(wu(15)) + repeat(3, work(10.0, 0.95, 1.05, "Threshold"), rest(5.0)) + cd()),
        Workout("overunder", "Over-under", Level.LANJUT, "Tahan surge di atas ambang", listOf(wu(15)) + repeat(3, *(repeat(3, work(2.0, 0.92, 0.96, "Under"), work(1.0, 1.05, 1.10, "Over")).toTypedArray()), rest(5.0)) + cd()),
        Workout("vo2max", "VO2max 5x4", Level.LANJUT, "Kapasitas aerobik maksimal", listOf(wu(15)) + repeat(5, work(4.0, 1.06, 1.20, "VO2max"), rest(4.0)) + cd()),
        Workout("anaerobic", "Anaerobik 8x30/30", Level.ELITE, "Serangan dan sprint pendek berulang", listOf(wu(15)) + repeat(8, work(0.5, 1.21, 1.50, "Keras"), rest(0.5)) + cd()),
        Workout("sprint", "Sprint 6x15 detik", Level.MENENGAH, "Tenaga puncak", listOf(wu(15)) + repeat(6, work(0.25, 1.50, 2.50, "Sprint penuh", 100..130), rest(4.75)) + cd()),
        Workout("climb", "Climbing repeats 4x8", Level.LANJUT, "Tanjakan dengan cadence rendah", listOf(wu(15)) + repeat(4, work(8.0, 0.90, 1.00, "Menanjak", 60..75), rest(5.0)) + cd()),
        Workout("cadence", "Cadence drills", Level.PEMULA, "Kayuhan halus dan efisien", listOf(wu()) + repeat(6, work(2.0, 0.60, 0.70, "Cadence tinggi", 100..115), rest(2.0)) + cd()),
        Workout("ftp20", "Tes FTP 20 menit", Level.MENENGAH, "Mengukur FTP untuk zona latihan", listOf(wu(15), work(5.0, 1.05, 1.20, "Pembuka 5 menit"), rest(10.0), Step(20 * MIN, Target.Free, label = "Tes 20 menit, rata-rata setinggi mungkin"), cd(15))),
        Workout("ramp", "Ramp test", Level.PEMULA, "Tes FTP singkat tanpa tempo pacing", listOf(wu(5)) + (0 until 20).map { i -> Step(MIN, Target.PowerPct(0.45 + i * 0.06, 0.45 + i * 0.06), label = "Ramp ${i + 1}") } + cd()),
        Workout("bike_handling", "Endurance + teknik", Level.PEMULA, "Ride santai sambil latihan mengerem dan menikung", listOf(wu(), work(45.0, 0.56, 0.70, "Zona 2 dengan latihan teknik"), cd())),
        Workout("race_sim", "Simulasi lomba", Level.ELITE, "Ritme lomba dengan serangan", listOf(wu(20)) + repeat(4, work(15.0, 0.85, 0.95, "Ritme peloton"), work(1.0, 1.30, 1.60, "Serangan"), rest(4.0)) + cd()),
    )

    fun byId(id: String): Workout? = all.firstOrNull { it.id == id }

    /** Sessions that suit [level], each scaled to it. Lower-level sessions stay available to everyone. */
    fun forLevel(level: Level): List<Workout> = all.filter { it.level.ordinal <= level.ordinal + 1 }.map { it.scaled(level) }
}
