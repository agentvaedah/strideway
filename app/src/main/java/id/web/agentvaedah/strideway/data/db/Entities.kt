package id.web.agentvaedah.strideway.data.db

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
}

@Serializable
@Entity(tableName = "sensors")
data class SensorEntity(
    @PrimaryKey val address: String,
    val name: String,
    val kind: SensorKind,
    val autoConnect: Boolean = true,
)
