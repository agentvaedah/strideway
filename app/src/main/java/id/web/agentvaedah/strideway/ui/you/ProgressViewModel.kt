package id.web.agentvaedah.strideway.ui.you

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.web.agentvaedah.strideway.core.challenge.Achievements
import id.web.agentvaedah.strideway.core.challenge.ChallengeId
import id.web.agentvaedah.strideway.core.history.HistoryFilter
import id.web.agentvaedah.strideway.core.history.HistoryItem
import id.web.agentvaedah.strideway.core.history.MonthGroup
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.core.records.RecordHolder
import id.web.agentvaedah.strideway.core.records.RecordKey
import id.web.agentvaedah.strideway.core.records.RecordRank
import id.web.agentvaedah.strideway.core.stats.ProgressStats
import id.web.agentvaedah.strideway.core.stats.StatBucket
import id.web.agentvaedah.strideway.core.stats.StatMetric
import id.web.agentvaedah.strideway.core.stats.StatRange
import id.web.agentvaedah.strideway.core.stats.StatSeries
import id.web.agentvaedah.strideway.core.stats.StreakInfo
import id.web.agentvaedah.strideway.core.stats.Streaks
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.files.ProfilePhoto
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.data.prefs.UserProfile
import id.web.agentvaedah.strideway.data.repo.ActivityRepository
import id.web.agentvaedah.strideway.data.repo.RecordsRepository
import id.web.agentvaedah.strideway.data.repo.toStat
import id.web.agentvaedah.strideway.ui.home.privatePath
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrophyItem(val id: ChallengeId, val month: YearMonth)

data class ProgressState(
    val profile: UserProfile,
    val series: StatSeries,
    val metric: StatMetric,
    val sport: Sport?,
    val streak: StreakInfo,
    val allTime: StatBucket,
    val trophies: List<TrophyItem>,
    val activities: List<Pair<ActivityEntity, List<LatLon>>>,
    val records: List<RecordHolder>,
    val recordTop: Map<RecordKey, List<RecordRank>>,
    val recordPrevious: Map<RecordKey, Double>,
    val activityById: Map<Long, ActivityEntity>,
    val badges: Map<Long, List<String>>,
    val history: List<MonthGroup>,
    val filter: HistoryFilter,
    val totalActivities: Int,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    activities: ActivityRepository,
    private val settings: SettingsRepository,
    records: RecordsRepository,
    private val photos: ProfilePhoto,
) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter())
    private val zone = ZoneId.systemDefault()
    private val range = MutableStateFlow(StatRange.M3)
    private val metric = MutableStateFlow(StatMetric.DISTANCE)
    private val sport = MutableStateFlow<Sport?>(null)

    val state: StateFlow<ProgressState?> = combine(
        activities.activities(),
        settings.profile,
        settings.awards,
        combine(settings.privacyZone, records.records, filter) { p, rec, f -> Triple(p, rec, f) },
        combine(range, metric, sport) { r, m, s -> Triple(r, m, s) },
    ) { list, profile, awards, (privacy, rec, f), (r, m, s) ->
        val stats = list.map { it.toStat() }
        val today = LocalDate.now(zone)
        ProgressState(
            profile = profile,
            series = ProgressStats.series(stats, r, today, zone, s),
            metric = m,
            sport = s,
            streak = Streaks.compute(stats, today, zone),
            allTime = stats.filter { s == null || it.sport == s }.fold(StatBucket(today)) { b, a -> b + a },
            trophies = awards.mapNotNull { Achievements.parse(it) }.sortedByDescending { it.second }.map { TrophyItem(it.first, it.second) },
            activities = list.map { it to privatePath(it, privacy) },
            records = rec.holders.values.sortedBy { it.key.ordinal },
            recordTop = rec.top,
            recordPrevious = rec.previous,
            activityById = list.associateBy { it.id },
            badges = list.associate { a -> a.id to rec.badgesFor(a.id).map { it.label } },
            history = HistoryFilter.groupByMonth(f.apply(list.map { it.toHistory() }, zone), zone),
            filter = f,
            totalActivities = list.size,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPhoto(uri: Uri) {
        viewModelScope.launch { photos.save(uri)?.let { settings.setProfilePhoto(it.absolutePath) } }
    }

    fun removePhoto() {
        viewModelScope.launch {
            photos.delete()
            settings.setProfilePhoto(null)
        }
    }

    fun setRange(r: StatRange) { range.value = r }
    fun setMetric(m: StatMetric) { metric.value = m }
    fun setSport(s: Sport?) { sport.value = s }
    fun setFilter(f: HistoryFilter) { filter.value = f }
}

private fun ActivityEntity.toHistory() = HistoryItem(id, name, sport, startMs, distanceM, movingMs, elevationGainM)
