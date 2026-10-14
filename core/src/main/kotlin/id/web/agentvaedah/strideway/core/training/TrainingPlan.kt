package id.web.agentvaedah.strideway.core.training

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Phase(val label: String) { BASE("Dasar"), BUILD("Membangun"), PEAK("Puncak"), TAPER("Tapering") }

data class PlannedSession(val date: LocalDate, val workoutId: String)

data class PlanWeek(val start: LocalDate, val phase: Phase, val sessions: List<PlannedSession>)

data class TrainingPlan(val goal: String, val weeks: List<PlanWeek>)

object PlanGenerator {
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * Rule-based periodisation, used without an AI provider and as the shape the AI must follow: about
     * 40% base, 35% build, the rest peak, and a taper in the event week. The longest ride sits on the weekend;
     * an FTP test opens the build phase so its targets match the rider's current form.
     */
    fun generate(goal: String, start: LocalDate, event: LocalDate, days: Set<DayOfWeek>, level: Level): TrainingPlan {
        val monday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = (ChronoUnit.WEEKS.between(monday, event.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) + 1).toInt().coerceAtLeast(1)
        val ordered = days.sortedBy { it.value }
        val longDay = ordered.lastOrNull { it in weekend } ?: ordered.lastOrNull()
        val baseEnd = (weeks * 0.4).toInt().coerceAtLeast(1)
        val buildEnd = (weeks * 0.75).toInt().coerceAtLeast(baseEnd)
        return TrainingPlan(goal, (0 until weeks).map { w ->
            val phase = when {
                w == weeks - 1 && weeks > 1 -> Phase.TAPER
                w < baseEnd -> Phase.BASE
                w < buildEnd -> Phase.BUILD
                else -> Phase.PEAK
            }
            val wkStart = monday.plusWeeks(w.toLong())
            val pool = sessionsFor(phase, level)
            var k = 0
            val sessions = ordered.map { d ->
                val date = wkStart.with(TemporalAdjusters.nextOrSame(d))
                val id = when {
                    d == longDay && phase != Phase.TAPER -> "longride"
                    w == baseEnd && k == 0 -> "ftp20".also { k++ }
                    else -> pool[k++ % pool.size]
                }
                PlannedSession(date, id)
            }.filter { !it.date.isBefore(start) && !it.date.isAfter(event) }
            PlanWeek(wkStart, phase, sessions)
        })
    }

    private fun sessionsFor(phase: Phase, level: Level): List<String> {
        val easy = level == Level.PEMULA
        return when (phase) {
            Phase.BASE -> listOf("endurance", "cadence", if (easy) "bike_handling" else "tempo")
            Phase.BUILD -> listOf("sweetspot", "endurance", if (easy) "tempo" else "threshold")
            Phase.PEAK -> listOf(if (easy) "sweetspot" else "vo2max", "endurance", if (level == Level.ELITE) "race_sim" else "overunder")
            Phase.TAPER -> listOf("recovery", "sprint", "endurance")
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Reads an AI plan of {"weeks":[{"phase":"BUILD","sessions":[{"day":"TUESDAY","workout":"sweetspot"}]}]}; unknown workouts are dropped. */
    fun parseAi(text: String, goal: String, start: LocalDate): TrainingPlan? = try {
        val body = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
        val root = json.parseToJsonElement(body).jsonObject
        val monday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = (root["weeks"] as JsonArray).mapIndexed { i, w ->
            val o = w.jsonObject
            val phase = runCatching { Phase.valueOf(o["phase"]!!.jsonPrimitive.content) }.getOrDefault(Phase.BASE)
            val wkStart = monday.plusWeeks(i.toLong())
            val sessions = (o["sessions"] as? JsonArray).orEmpty().mapNotNull { s ->
                val so = s as? JsonObject ?: return@mapNotNull null
                val day = runCatching { DayOfWeek.valueOf(so["day"]!!.jsonPrimitive.content.uppercase()) }.getOrNull() ?: return@mapNotNull null
                val id = so["workout"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (WorkoutLibrary.byId(id) == null) return@mapNotNull null
                PlannedSession(wkStart.with(TemporalAdjusters.nextOrSame(day)), id)
            }
            PlanWeek(wkStart, phase, sessions)
        }
        if (weeks.isEmpty()) null else TrainingPlan(goal, weeks)
    } catch (e: Exception) {
        null
    }

    /** Prompt telling the AI which workout ids exist, so its plan maps onto real sessions. */
    fun aiPrompt(goal: String, start: LocalDate, event: LocalDate, days: Set<DayOfWeek>, level: Level, fitness: String): String = buildString {
        append("Buat rencana latihan sepeda mingguan dalam JSON saja, tanpa teks lain. ")
        append("Format: {\"weeks\":[{\"phase\":\"BASE|BUILD|PEAK|TAPER\",\"sessions\":[{\"day\":\"MONDAY\",\"workout\":\"id\"}]}]}. ")
        append("Target: $goal. Mulai $start, event $event. Hari latihan: ${days.sortedBy { it.value }.joinToString()}. Level: ${level.label}. ")
        append("Kondisi sekarang: $fitness. ")
        append("Pakai hanya id workout ini: ${WorkoutLibrary.all.joinToString { "${it.id} (${it.name})" }}. ")
        append("Minggu terakhir tapering, long ride di akhir pekan, satu tes FTP di awal fase BUILD.")
    }
}
