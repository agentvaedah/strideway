package id.web.agentvaedah.strideway.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.challenge.Achievements
import id.web.agentvaedah.strideway.core.challenge.Award
import id.web.agentvaedah.strideway.core.challenge.ChallengeId
import id.web.agentvaedah.strideway.core.challenge.ChallengeProgress
import id.web.agentvaedah.strideway.core.challenge.Challenges
import id.web.agentvaedah.strideway.core.challenge.TrainingCalendar
import id.web.agentvaedah.strideway.core.gear.GearLevel
import id.web.agentvaedah.strideway.core.geo.Polyline
import id.web.agentvaedah.strideway.core.model.ActivitySummary
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import id.web.agentvaedah.strideway.core.physio.Zone
import id.web.agentvaedah.strideway.core.physio.Zones
import id.web.agentvaedah.strideway.core.privacy.PrivacyZone
import id.web.agentvaedah.strideway.core.stats.HrSummary
import id.web.agentvaedah.strideway.core.stats.ProgressStats
import id.web.agentvaedah.strideway.core.stats.StatMetric
import id.web.agentvaedah.strideway.core.stats.StatRange
import id.web.agentvaedah.strideway.core.stats.StatSeries
import id.web.agentvaedah.strideway.core.stats.StreakInfo
import id.web.agentvaedah.strideway.core.stats.Streaks
import id.web.agentvaedah.strideway.core.stats.WeeklySnapshot
import id.web.agentvaedah.strideway.core.track.TrackEdit
import id.web.agentvaedah.strideway.core.training.Phase
import id.web.agentvaedah.strideway.core.training.Workout
import id.web.agentvaedah.strideway.core.training.WorkoutLibrary
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.prefs.BikeType
import id.web.agentvaedah.strideway.data.prefs.PrivacyZoneSetting
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.prefs.UserProfile
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import id.web.agentvaedah.strideway.data.repo.GarageItem
import id.web.agentvaedah.strideway.data.repo.GearRepository
import id.web.agentvaedah.strideway.data.repo.StoredPlan
import id.web.agentvaedah.strideway.data.repo.TrainingRepository
import id.web.agentvaedah.strideway.data.repo.toStat
import id.web.agentvaedah.strideway.service.WorkoutSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedItem(val activity: ActivityEntity, val path: List<LatLon>)

data class CalendarData(val month: YearMonth, val activeDays: Set<Int>, val weeklyHours: List<Pair<LocalDate, Double>>)

/** Bike wear at a glance for the Home carousel; [next] is the most worn tracked part. */
data class BikeInsight(
    val bikeName: String,
    val rideKm: Double,
    val tracked: Int,
    val overdue: Int,
    val dueSoon: Int,
    val next: GarageItem?,
    val worst: List<GarageItem>,
)

data class ChallengeCard(val progress: ChallengeProgress, val joined: Boolean, val earned: Boolean)

/** Monday-to-Sunday training load (TRIMP) of the current week, with last week's total to compare. */
data class WeekLoad(val days: List<Double>, val todayIndex: Int, val total: Double, val lastWeek: Double)

/** Today's session from the Latihan plan, ready to ride. */
data class TodaySession(val workout: Workout, val sessionId: Long, val phase: Phase?, val week: Int, val done: Boolean)

sealed interface HomeState {
    data object Loading : HomeState
    data class Error(val message: String) : HomeState
    data class Ready(
        val feed: List<FeedItem>,
        val fitness: List<TrainingLoad.FitnessPoint>,
        val anyEstimatedLoad: Boolean,
        val challenges: List<ChallengeCard>,
        val calendar: CalendarData,
        val series: StatSeries,
        val metric: StatMetric,
        val weekly: WeeklySnapshot,
        val streak: StreakInfo,
        val hr: HrSummary,
        val hrZones: List<Zone>,
        val newAwards: List<Award>,
        val bike: BikeInsight? = null,
        val weekLoad: WeekLoad? = null,
        val today: TodaySession? = null,
        val profile: UserProfile = UserProfile(),
    ) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    activities: ActivityRepository,
    private val settings: SettingsRepository,
    gear: GearRepository,
    training: TrainingRepository,
    private val workouts: WorkoutSession,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val month = MutableStateFlow(YearMonth.now(zone))
    private val range = MutableStateFlow(StatRange.D7)
    private val metric = MutableStateFlow(StatMetric.DISTANCE)

    private val prefs = combine(settings.privacyZone, settings.joinedChallenges, settings.awards, settings.athlete) { p, j, a, ath ->
        HomePrefs(p, j, a, ath)
    }

    val state: StateFlow<HomeState> = combine(
        activities.activities(),
        prefs,
        month,
        combine(range, metric) { r, m -> r to m },
    ) { list, p, m, (r, met) ->
        build(list, p, m, r, met)
    }.combine(combine(gear.garage, settings.profile, training.plan) { g, u, plan -> Triple(g, u, plan) }) { state, (g, u, plan) ->
        val active = g.active.sortedByDescending { it.status.fraction }
        state.copy(
            profile = u,
            today = plan?.let { todaySession(it) },
            bike = BikeInsight(
                bikeName = u.bikeName.ifBlank { u.bikeType.label },
                rideKm = g.totalRideKm,
                tracked = active.size,
                overdue = active.count { it.status.level == GearLevel.OVERDUE },
                dueSoon = active.count { it.status.level == GearLevel.DUE_SOON },
                next = active.firstOrNull(),
                worst = active.take(3),
            ).takeIf { u.bikeType != BikeType.NONE },
        )
    }
        .flowOn(Dispatchers.Default)
        .catch<HomeState> { emit(HomeState.Error("Data aktivitas tidak bisa dibaca: ${it.message}")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading)

    val currentMonth: StateFlow<YearMonth> = month

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (month.value < YearMonth.now(zone)) month.value = month.value.plusMonths(1)
    }

    fun ride(t: TodaySession) = workouts.select(t.workout, t.sessionId)

    private fun todaySession(plan: StoredPlan): TodaySession? {
        val day = LocalDate.now(zone)
        val s = plan.sessions.firstOrNull { it.date == day } ?: return null
        val w = WorkoutLibrary.byId(s.workoutId)?.scaled(plan.level) ?: return null
        val phase = plan.phases.lastOrNull { !it.first.isAfter(day) }?.second
        val week = (ChronoUnit.DAYS.between(plan.start, day) / 7 + 1).toInt().coerceAtLeast(1)
        return TodaySession(w, s.id, phase, week, s.doneActivityId != null)
    }

    fun setRange(r: StatRange) { range.value = r }

    fun setMetric(m: StatMetric) { metric.value = m }

    fun setJoined(id: ChallengeId, joined: Boolean) {
        viewModelScope.launch { settings.setChallengeJoined(id.name, joined) }
    }

    /** Stores awards the UI has just celebrated, so the dialog shows once per award. */
    fun acknowledgeAwards(awards: List<Award>) {
        viewModelScope.launch { settings.addAwards(awards.map { it.key }) }
    }

    private data class HomePrefs(val privacy: PrivacyZoneSetting, val joined: Set<String>, val awards: Set<String>, val athlete: Athlete)

    private fun build(list: List<ActivityEntity>, p: HomePrefs, m: YearMonth, r: StatRange, met: StatMetric): HomeState.Ready {
        val privacy = p.privacy
        val summaries = list.map { ActivitySummary(it.sport, it.startMs, it.distanceM, it.movingMs, it.trimp) }
        val stats = list.map { it.toStat() }
        val localToday = LocalDate.now(zone)
        val thisMonth = YearMonth.now(zone)
        val joined = p.joined.mapNotNull { runCatching { ChallengeId.valueOf(it) }.getOrNull() }.toSet()
        val hrZones = Zones.heartRate(p.athlete.hrMax, p.athlete.hrRest)
        val today = localToday.toEpochDay()
        val loads = list.map {
            TrainingLoad.DailyLoad(Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().toEpochDay(), it.trimp)
        }
        return HomeState.Ready(
            feed = list.take(FEED_LIMIT).map { FeedItem(it, privatePath(it, privacy)) },
            fitness = TrainingLoad.series(loads, today - FITNESS_WINDOW_DAYS + 1, today),
            anyEstimatedLoad = list.any { it.trimpEstimated },
            challenges = Challenges.monthly(summaries, thisMonth, zone).map {
                ChallengeCard(it, it.id in joined, Achievements.key(it.id, thisMonth) in p.awards)
            },
            calendar = CalendarData(m, TrainingCalendar.activeDays(summaries, m, zone), TrainingCalendar.weeklyHours(summaries, m, zone)),
            series = ProgressStats.series(stats, r, localToday, zone),
            metric = met,
            weekly = ProgressStats.weeklySnapshot(stats, localToday, zone),
            streak = Streaks.compute(stats, localToday, zone),
            hr = ProgressStats.hrSummary(stats, StatRange.M1, localToday, zone, hrZones),
            hrZones = hrZones,
            newAwards = Achievements.newlyEarned(summaries, thisMonth, zone, joined, p.awards),
            weekLoad = weekLoad(list, localToday),
        )
    }

    private fun weekLoad(list: List<ActivityEntity>, today: LocalDate): WeekLoad {
        val monday = today.with(DayOfWeek.MONDAY)
        val days = DoubleArray(7)
        var last = 0.0
        for (a in list) {
            val d = Instant.ofEpochMilli(a.startMs).atZone(zone).toLocalDate()
            val i = ChronoUnit.DAYS.between(monday, d).toInt()
            if (i in 0..6) days[i] += a.trimp else if (i in -7..-1) last += a.trimp
        }
        return WeekLoad(days.toList(), ChronoUnit.DAYS.between(monday, today).toInt(), days.sum(), last)
    }

    private companion object {
        const val FEED_LIMIT = 50
        const val FITNESS_WINDOW_DAYS = 42L
    }
}

/** The route sketch others may see: privacy zone trimmed, then the activity's own visibility setting. */
fun privatePath(a: ActivityEntity, privacy: PrivacyZoneSetting): List<LatLon> {
    if (a.hideMap) return emptyList()
    val path = runCatching { Polyline.decode(a.polyline) }.getOrDefault(emptyList())
    val center = privacy.center
    val zoned = if (privacy.enabled && center != null) PrivacyZone.trim(path, center, privacy.radiusM) { it } else path
    return TrackEdit.visiblePath(zoned, a.hideStartM, a.hideEndM)
}
