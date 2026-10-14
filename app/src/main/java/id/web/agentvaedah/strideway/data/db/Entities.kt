package id.web.agentvaedah.strideway.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.web.agentvaedah.strideway.core.model.Sport
import kotlinx.serialization.Serializable

object ActivitySource {
    const val RECORD = "record"
    const val IMPORT = "import"
}

object SyncState {
    const val LOCAL_ONLY = 0
    const val PENDING = 1
    const val SYNCED = 2
}

@Serializable
@Entity(tableName = "activities", indices = [Index("startMs")])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sport: Sport,
    val name: String,
    val startMs: Long,
    /** 0 while recording; a recorded row still at 0 after a restart is an interrupted session. */
    val elapsedMs: Long = 0,
    val movingMs: Long = 0,
    val distanceM: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val calories: Double = 0.0,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val avgPower: Int? = null,
    val trimp: Double = 0.0,
    val trimpEstimated: Boolean = false,
    val polyline: String = "",
    val source: String = ActivitySource.RECORD,
    val syncState: Int = SyncState.LOCAL_ONLY,
    // Added in schema 2; defaults must match MIGRATION_1_2 exactly.
    @ColumnInfo(defaultValue = "") val description: String = "",
    /** Metres hidden at the start and end of the shown map, on top of the privacy zone. */
    @ColumnInfo(defaultValue = "0") val hideStartM: Int = 0,
    @ColumnInfo(defaultValue = "0") val hideEndM: Int = 0,
    @ColumnInfo(defaultValue = "0") val hideMap: Boolean = false,
    /** BestEfforts.VERSION the stored efforts were computed with; 0 means not yet. */
    @ColumnInfo(defaultValue = "0") val effortsVersion: Int = 0,
)

@Serializable
@Entity(
    tableName = "track_points",
    indices = [Index("activityId")],
    foreignKeys = [
        ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val speedMps: Double? = null,
    val hr: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val interpolated: Boolean = false,
)

object RouteSource {
    const val IMPORT = "import"
    const val AI = "ai"
    const val DRAWN = "drawn"
    const val PLANNED = "planned"
    const val ACTIVITY = "activity"
}

@Serializable
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,
    val createdMs: Long,
    val distanceM: Double,
    val elevationGainM: Double,
    val polyline: String,
    val prompt: String? = null,
)

@Serializable
@Entity(
    tableName = "route_points",
    indices = [Index("routeId")],
    foreignKeys = [
        ForeignKey(entity = RouteEntity::class, parentColumns = ["id"], childColumns = ["routeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val seq: Int,
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
)

object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val ERROR = "error"
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdMs: Long,
    val provider: String? = null,
)

enum class SensorKind(val label: String) {
    HR("Detak jantung"),
    CSC("Kecepatan & cadence"),
    POWER("Power meter"),
    ENV("Suhu & tekanan"),
}

@Serializable
@Entity(tableName = "sensors")
data class SensorEntity(
    @PrimaryKey val address: String,
    val name: String,
    val kind: SensorKind,
    val autoConnect: Boolean = true,
)

/** Fastest time per distance in one activity; [effortKey] is a RecordKey name. */
@Entity(
    tableName = "best_efforts",
    primaryKeys = ["activityId", "effortKey"],
    foreignKeys = [
        ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BestEffortEntity(
    val activityId: Long,
    val effortKey: String,
    val seconds: Double,
    val startIdx: Int,
    val endIdx: Int,
)

/** A photo copied into app storage; [path] is relative to filesDir. */
@Entity(
    tableName = "activity_media",
    indices = [Index("activityId")],
    foreignKeys = [
        ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val path: String,
    val addedMs: Long,
)

@Serializable
@Entity(tableName = "saved_places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val createdMs: Long,
)

/** A tracked bike part; [type] is a ComponentType name. */
@Serializable
@Entity(tableName = "bike_components")
data class ComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val label: String,
    val installMs: Long,
    val kmAtInstall: Double,
    val serviceIntervalKm: Double,
    val active: Boolean = true,
)

@Serializable
@Entity(
    tableName = "component_events",
    indices = [Index("componentId")],
    foreignKeys = [
        ForeignKey(entity = ComponentEntity::class, parentColumns = ["id"], childColumns = ["componentId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ComponentEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val componentId: Long,
    /** GearEventKind name. */
    val kind: String,
    val atMs: Long,
    val note: String? = null,
)

/** A turn instruction on a saved route; [type] is a ManeuverType name. Added in schema 3. */
@Entity(
    tableName = "route_maneuvers",
    primaryKeys = ["routeId", "seq"],
    foreignKeys = [
        ForeignKey(entity = RouteEntity::class, parentColumns = ["id"], childColumns = ["routeId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ManeuverEntity(
    val routeId: Long,
    val seq: Int,
    val pointIndex: Int,
    val type: String,
    val exitNumber: Int,
    val street: String?,
)

/** A training plan; [weeksJson] holds weeks and phases as written by the generator. Added in schema 3. */
@Serializable
@Entity(tableName = "training_plans")
data class TrainingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goal: String,
    val startEpochDay: Long,
    val eventEpochDay: Long,
    val level: String,
    val createdMs: Long,
    val weeksJson: String,
)

@Serializable
@Entity(
    tableName = "planned_sessions",
    indices = [Index("planId")],
    foreignKeys = [
        ForeignKey(entity = TrainingPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlannedSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val epochDay: Long,
    val workoutId: String,
    /** Activity that completed this session, if any. */
    val doneActivityId: Long?,
)

