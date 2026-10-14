package id.web.agentvaedah.strideway.data.repo

import id.web.agentvaedah.strideway.core.training.Level
import id.web.agentvaedah.strideway.core.training.Phase
import id.web.agentvaedah.strideway.core.training.TrainingPlan
import id.web.agentvaedah.strideway.data.db.PlannedSessionEntity
import id.web.agentvaedah.strideway.data.db.TrainingDao
import id.web.agentvaedah.strideway.data.db.TrainingPlanEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class StoredSession(val id: Long, val date: LocalDate, val workoutId: String, val doneActivityId: Long?)

data class StoredPlan(
    val id: Long,
    val goal: String,
    val start: LocalDate,
    val event: LocalDate,
    val level: Level,
    val phases: List<Pair<LocalDate, Phase>>,
    val sessions: List<StoredSession>,
)

@Serializable
private data class WeekJson(val start: Long, val phase: String)

/** The one active training plan. Creating a new plan replaces the old one. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrainingRepository @Inject constructor(private val dao: TrainingDao) {
    private val json = Json { ignoreUnknownKeys = true }

    val plan: Flow<StoredPlan?> = dao.observeCurrentPlan().flatMapLatest { p ->
        if (p == null) {
            flowOf(null)
        } else {
            dao.observeSessions(p.id).map { rows ->
                val weeks = runCatching { json.decodeFromString(ListSerializer(WeekJson.serializer()), p.weeksJson) }.getOrDefault(emptyList())
                StoredPlan(
                    id = p.id,
                    goal = p.goal,
                    start = LocalDate.ofEpochDay(p.startEpochDay),
                    event = LocalDate.ofEpochDay(p.eventEpochDay),
                    level = runCatching { Level.valueOf(p.level) }.getOrDefault(Level.MENENGAH),
                    phases = weeks.mapNotNull { w -> runCatching { LocalDate.ofEpochDay(w.start) to Phase.valueOf(w.phase) }.getOrNull() },
                    sessions = rows.map { StoredSession(it.id, LocalDate.ofEpochDay(it.epochDay), it.workoutId, it.doneActivityId) },
                )
            }
        }
    }

    suspend fun save(plan: TrainingPlan, start: LocalDate, event: LocalDate, level: Level) {
        val weeks = plan.weeks.map { WeekJson(it.start.toEpochDay(), it.phase.name) }
        dao.replacePlan(
            TrainingPlanEntity(
                goal = plan.goal,
                startEpochDay = start.toEpochDay(),
                eventEpochDay = event.toEpochDay(),
                level = level.name,
                createdMs = System.currentTimeMillis(),
                weeksJson = json.encodeToString(ListSerializer(WeekJson.serializer()), weeks),
            ),
            plan.weeks.flatMap { w -> w.sessions.map { PlannedSessionEntity(planId = 0, epochDay = it.date.toEpochDay(), workoutId = it.workoutId, doneActivityId = null) } },
        )
    }

    suspend fun markDone(sessionId: Long, activityId: Long) = dao.markDone(sessionId, activityId)

    suspend fun clear() = dao.deleteAllPlans()
}
