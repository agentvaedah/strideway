package id.web.agentvaedah.strideway.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.challenge.ChallengeProgress
import id.web.agentvaedah.strideway.core.challenge.Challenges
import id.web.agentvaedah.strideway.core.challenge.TrainingCalendar
import id.web.agentvaedah.strideway.core.geo.Polyline
import id.web.agentvaedah.strideway.core.model.ActivitySummary
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.physio.TrainingLoad
import id.web.agentvaedah.strideway.core.privacy.PrivacyZone
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.prefs.PrivacyZoneSetting
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class FeedItem(val activity: ActivityEntity, val path: List<LatLon>)

data class CalendarData(val month: YearMonth, val activeDays: Set<Int>, val weeklyHours: List<Pair<LocalDate, Double>>)

sealed interface HomeState {
    data object Loading : HomeState
    data class Error(val message: String) : HomeState
    data class Ready(
        val feed: List<FeedItem>,
        val fitness: List<TrainingLoad.FitnessPoint>,
        val anyEstimatedLoad: Boolean,
        val challenges: List<ChallengeProgress>,
        val calendar: CalendarData,
    ) : HomeState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    activities: ActivityRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val month = MutableStateFlow(YearMonth.now(zone))

    val state: StateFlow<HomeState> = combine(activities.activities(), settings.privacyZone, month) { list, privacy, m ->
        build(list, privacy, m)
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

    private fun build(list: List<ActivityEntity>, privacy: PrivacyZoneSetting, m: YearMonth): HomeState.Ready {
        val summaries = list.map { ActivitySummary(it.sport, it.startMs, it.distanceM, it.movingMs, it.trimp) }
        val today = LocalDate.now(zone).toEpochDay()
        val loads = list.map {
            TrainingLoad.DailyLoad(Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().toEpochDay(), it.trimp)
        }
        return HomeState.Ready(
            feed = list.take(FEED_LIMIT).map { FeedItem(it, privatePath(it.polyline, privacy)) },
            fitness = TrainingLoad.series(loads, today - FITNESS_WINDOW_DAYS + 1, today),
            anyEstimatedLoad = list.any { it.trimpEstimated },
            challenges = Challenges.monthly(summaries, YearMonth.now(zone), zone),
            calendar = CalendarData(m, TrainingCalendar.activeDays(summaries, m, zone), TrainingCalendar.weeklyHours(summaries, m, zone)),
        )
    }

    private companion object {
        const val FEED_LIMIT = 50
        const val FITNESS_WINDOW_DAYS = 42L
    }
}

fun privatePath(polyline: String, privacy: PrivacyZoneSetting): List<LatLon> {
    val path = runCatching { Polyline.decode(polyline) }.getOrDefault(emptyList())
    val center = privacy.center
    return if (privacy.enabled && center != null) PrivacyZone.trim(path, center, privacy.radiusM) { it } else path
}
