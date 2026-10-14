package id.web.agentvaedah.strideway.data.repo

import id.web.agentvaedah.strideway.core.records.EffortRow
import id.web.agentvaedah.strideway.core.records.PersonalRecordSet
import id.web.agentvaedah.strideway.core.records.PersonalRecords
import id.web.agentvaedah.strideway.core.records.RecordActivity
import id.web.agentvaedah.strideway.core.records.RecordKey
import id.web.agentvaedah.strideway.data.db.BestEffortDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** Current personal records, recomputed whenever an activity or its efforts change. */
@Singleton
class RecordsRepository @Inject constructor(
    activities: ActivityRepository,
    efforts: BestEffortDao,
) {
    val records: Flow<PersonalRecordSet> = combine(activities.activities(), efforts.observeAll()) { list, rows ->
        PersonalRecords.compute(
            list.map { RecordActivity(it.id, it.sport, it.startMs, it.distanceM, it.elevationGainM, it.movingMs) },
            rows.mapNotNull { r ->
                runCatching { RecordKey.valueOf(r.effortKey) }.getOrNull()?.let { EffortRow(r.activityId, it, r.seconds) }
            },
        )
    }.flowOn(Dispatchers.Default)
}
