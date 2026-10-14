package id.web.agentvaedah.strideway.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import id.web.agentvaedah.strideway.core.training.FtpTest
import id.web.agentvaedah.strideway.core.training.Level
import id.web.agentvaedah.strideway.core.training.PlanGenerator
import id.web.agentvaedah.strideway.core.training.Workout
import id.web.agentvaedah.strideway.core.training.WorkoutLibrary
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.remote.llm.LlmClient
import id.web.agentvaedah.strideway.data.remote.llm.LlmMessage
import id.web.agentvaedah.strideway.data.remote.llm.LlmResult
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import id.web.agentvaedah.strideway.data.repo.StoredPlan
import id.web.agentvaedah.strideway.data.repo.StoredSession
import id.web.agentvaedah.strideway.data.repo.TrainingRepository
import id.web.agentvaedah.strideway.service.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface PlanBuild {
    data object Idle : PlanBuild
    data object Working : PlanBuild
    /** Saved; [usedAi] says whether the AI or the built-in rules made it. */
    data class Done(val usedAi: Boolean, val note: String?) : PlanBuild
}

/** An FTP test just ridden, with the new value to offer the rider. */
data class FtpResult(val oldFtp: Int, val newFtp: Int)

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val training: TrainingRepository,
    private val settings: SettingsRepository,
    private val activities: ActivityRepository,
    private val llm: LlmClient,
    private val session: WorkoutSession,
) : ViewModel() {
    val athlete: StateFlow<Athlete> = settings.athlete.stateIn(viewModelScope, SharingStarted.Eagerly, Athlete())
    val plan: StateFlow<StoredPlan?> = training.plan.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val selected: StateFlow<Workout?> = session.active

    private val _level = MutableStateFlow(Level.MENENGAH)
    val level: StateFlow<Level> = _level.asStateFlow()

    val library: StateFlow<List<Workout>> = _level.map { WorkoutLibrary.forLevel(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutLibrary.forLevel(Level.MENENGAH))

    private val _build = MutableStateFlow<PlanBuild>(PlanBuild.Idle)
    val build: StateFlow<PlanBuild> = _build.asStateFlow()

    private val _ftp = MutableStateFlow<FtpResult?>(null)
    val ftpResult: StateFlow<FtpResult?> = _ftp.asStateFlow()

    init {
        // An FTP test that just finished offers its result once.
        viewModelScope.launch {
            session.finished.collect { done ->
                val (w, activityId) = done ?: return@collect
                if (w.id == "ftp20" || w.id == "ramp") evaluateFtp(w, activityId)
                session.consumeFinished()
            }
        }
    }

    fun setLevel(l: Level) { _level.value = l }

    /** Picks the session to ride next; Record then runs it interval by interval. */
    fun ride(w: Workout, sessionId: Long? = null) = session.select(w, sessionId)

    fun cancelRide() = session.select(null)

    fun workoutFor(s: StoredSession, level: Level): Workout? = WorkoutLibrary.byId(s.workoutId)?.scaled(level)

    fun makePlan(goal: String, event: LocalDate, days: Set<DayOfWeek>, level: Level, useAi: Boolean) {
        _build.value = PlanBuild.Working
        viewModelScope.launch {
            val start = LocalDate.now()
            val ai = if (useAi) {
                val prompt = PlanGenerator.aiPrompt(goal, start, event, days, level, fitnessSummary())
                when (val r = llm.complete("Kamu pelatih sepeda. Jawab JSON saja.", listOf(LlmMessage("user", prompt)), maxTokens = 4_000)) {
                    is LlmResult.Ok -> PlanGenerator.parseAi(r.text, goal, start)
                    is LlmResult.Failure -> null
                }
            } else {
                null
            }
            val plan = ai ?: PlanGenerator.generate(goal, start, event, days, level)
            training.save(plan, start, event, level)
            _level.value = level
            _build.value = PlanBuild.Done(
                usedAi = ai != null,
                note = if (useAi && ai == null) "AI tidak bisa dipakai (cek API key di AI Coach). Rencana dibuat dengan aturan bawaan." else null,
            )
        }
    }

    fun deletePlan() {
        viewModelScope.launch { training.clear() }
        _build.value = PlanBuild.Idle
    }

    fun applyFtp(r: FtpResult) {
        viewModelScope.launch { settings.saveAthlete(athlete.value.copy(ftpWatts = r.newFtp)) }
        _ftp.value = null
    }

    fun dismissFtp() { _ftp.value = null }

    private suspend fun evaluateFtp(w: Workout, activityId: Long) {
        val pts = activities.points(activityId)
        if (pts.size < 2) return
        // Resample to one value per second, carrying the last power reading across gaps.
        val start = pts.first().timeMs
        val seconds = ((pts.last().timeMs - start) / 1000).toInt()
        var j = 0
        var last = 0
        val perSecond = List(seconds) { s ->
            val t = start + s * 1000L
            while (j < pts.lastIndex && pts[j + 1].timeMs <= t) j++
            pts[j].power?.let { last = it }
            last
        }
        if (perSecond.all { it == 0 }) return
        val ftp = when (w.id) {
            "ftp20" -> FtpTest.bestAverage(perSecond, 1200)?.let { FtpTest.from20Min(it) }
            else -> FtpTest.bestAverage(perSecond, 60)?.let { FtpTest.fromRamp(it) }
        } ?: return
        _ftp.value = FtpResult(athlete.value.ftpWatts, ftp)
    }

    private suspend fun fitnessSummary(): String {
        val zone = ZoneId.systemDefault()
        val list = activities.activities().first()
        if (list.isEmpty()) return "belum ada aktivitas tercatat"
        val today = LocalDate.now(zone).toEpochDay()
        val loads = list.map { TrainingLoad.DailyLoad(Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().toEpochDay(), it.trimp) }
        val p = TrainingLoad.series(loads, today, today).lastOrNull()
        val weekKm = list.filter { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().toEpochDay() > today - 28 }.sumOf { it.distanceM } / 4000.0
        return "fitness ${p?.fitness?.roundToInt() ?: 0}, fatigue ${p?.fatigue?.roundToInt() ?: 0}, rata-rata ${weekKm.roundToInt()} km per minggu, FTP ${athlete.value.ftpWatts} W"
    }
}
