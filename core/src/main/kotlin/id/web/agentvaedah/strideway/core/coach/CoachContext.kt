package id.web.agentvaedah.strideway.core.coach

import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import java.time.Instant
import java.time.ZoneId

data class RecentActivity(
    val sport: Sport,
    val startMs: Long,
    val distanceM: Double,
    val movingMs: Long,
    val elevationGainM: Double,
    val avgHr: Int?,
)

object CoachContext {
    fun systemPrompt(
        athlete: Athlete,
        today: TrainingLoad.FitnessPoint?,
        recent: List<RecentActivity>,
        zone: ZoneId,
    ): String = buildString {
        appendLine("Kamu pelatih bersepeda dan lari untuk atlet endurance di Indonesia. Jawab dalam Bahasa Indonesia yang lugas.")
        appendLine("Topik: biomekanika, kram, bike fitting, pacing lari, pemakaian brifter dan gigi, nutrisi, hidrasi di iklim tropis.")
        appendLine("Beri angka konkret bila bisa. Untuk nyeri dada, pingsan, atau cedera berat, sarankan berhenti dan periksa ke dokter.")
        appendLine()
        appendLine("Profil atlet:")
        appendLine("- ${if (athlete.gender == Gender.MALE) "Pria" else "Wanita"}, ${athlete.age} tahun, TB ${athlete.heightCm.toInt()} cm, BB ${Format.oneDecimal(athlete.weightKg)} kg")
        appendLine("- HR max ${athlete.hrMax} bpm, HR istirahat ${athlete.hrRest} bpm, FTP ${athlete.ftpWatts} W")
        if (today != null) {
            appendLine("- Fitness ${today.fitness.toInt()}, Fatigue ${today.fatigue.toInt()}, Form ${today.form.toInt()} (model TRIMP 42/7 hari)")
        }
        if (recent.isNotEmpty()) {
            appendLine()
            appendLine("Aktivitas terakhir:")
            for (a in recent.take(5)) {
                val date = Instant.ofEpochMilli(a.startMs).atZone(zone).toLocalDate()
                val kind = if (a.sport == Sport.RIDE) "Ride" else "Run"
                val hr = a.avgHr?.let { ", HR rata-rata $it" } ?: ""
                appendLine("- $date $kind ${Format.km(a.distanceM)} km, ${Format.duration(a.movingMs)}, naik ${a.elevationGainM.toInt()} m$hr")
            }
        }
    }.trimEnd()
}
