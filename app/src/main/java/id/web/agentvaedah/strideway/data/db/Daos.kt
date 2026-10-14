package id.web.agentvaedah.strideway.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import id.web.agentvaedah.strideway.core.model.Sport
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE elapsedMs > 0 ORDER BY startMs DESC")
    fun observeFinished(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    fun observe(id: Long): Flow<ActivityEntity?>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun get(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE elapsedMs = 0 AND source = 'record' ORDER BY startMs DESC LIMIT 1")
    suspend fun interrupted(): ActivityEntity?

    @Query("SELECT * FROM activities ORDER BY startMs")
    suspend fun all(): List<ActivityEntity>

    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(activities: List<ActivityEntity>)

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("UPDATE activities SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE activities SET name = :name, sport = :sport, description = :description WHERE id = :id")
    suspend fun updateMeta(id: Long, name: String, sport: Sport, description: String)

    @Query("UPDATE activities SET hideStartM = :hideStartM, hideEndM = :hideEndM, hideMap = :hideMap WHERE id = :id")
    suspend fun updateVisibility(id: Long, hideStartM: Int, hideEndM: Int, hideMap: Boolean)

    @Query("UPDATE activities SET effortsVersion = :version WHERE id = :id")
    suspend fun setEffortsVersion(id: Long, version: Int)

    @Query("UPDATE activities SET effortsVersion = 0")
    suspend fun resetEfforts()

    @Query("SELECT id FROM activities WHERE elapsedMs > 0 AND effortsVersion < :version ORDER BY startMs LIMIT :limit")
    suspend fun needingEfforts(version: Int, limit: Int): List<Long>

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM activities")
    suspend fun deleteAll()
}

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE activityId = :activityId ORDER BY timeMs, id")
    suspend fun forActivity(activityId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points ORDER BY id")
    suspend fun all(): List<TrackPointEntity>

    @Query("SELECT lat, lon FROM track_points WHERE interpolated = 0")
    suspend fun allPositions(): List<LatLonRow>

    @Query("DELETE FROM track_points WHERE activityId = :activityId AND (timeMs < :fromMs OR timeMs > :toMs)")
    suspend fun deleteOutside(activityId: Long, fromMs: Long, toMs: Long)
}

data class LatLonRow(val lat: Double, val lon: Double)

@Dao
interface BestEffortDao {
    @Query("SELECT * FROM best_efforts")
    fun observeAll(): Flow<List<BestEffortEntity>>

    @Query("DELETE FROM best_efforts WHERE activityId = :activityId")
    suspend fun deleteFor(activityId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<BestEffortEntity>)

    @Transaction
    suspend fun replaceFor(activityId: Long, rows: List<BestEffortEntity>) {
        deleteFor(activityId)
        insertAll(rows)
    }
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM activity_media WHERE activityId = :activityId ORDER BY addedMs")
    fun observeFor(activityId: Long): Flow<List<MediaEntity>>

    @Query("SELECT * FROM activity_media WHERE id = :id")
    suspend fun get(id: Long): MediaEntity?

    @Insert
    suspend fun insert(m: MediaEntity): Long

    @Query("DELETE FROM activity_media WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY name")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM saved_places")
    suspend fun all(): List<PlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PlaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<PlaceEntity>)

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface GearDao {
    @Query("SELECT * FROM bike_components ORDER BY id")
    fun observeComponents(): Flow<List<ComponentEntity>>

    @Query("SELECT * FROM component_events ORDER BY atMs")
    fun observeEvents(): Flow<List<ComponentEventEntity>>

    @Query("SELECT * FROM bike_components")
    suspend fun components(): List<ComponentEntity>

    @Query("SELECT * FROM component_events")
    suspend fun events(): List<ComponentEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: ComponentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<ComponentEntity>)

    @Insert
    suspend fun insertEvent(e: ComponentEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(list: List<ComponentEventEntity>)

    @Query("DELETE FROM bike_components WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bike_components")
    suspend fun deleteAll()
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY createdMs DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun get(id: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE id = :id")
    fun observe(id: Long): Flow<RouteEntity?>

    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY seq")
    suspend fun points(routeId: Long): List<RoutePointEntity>

    @Query("SELECT * FROM route_maneuvers WHERE routeId = :routeId ORDER BY seq")
    suspend fun maneuvers(routeId: Long): List<ManeuverEntity>

    @Insert
    suspend fun insertManeuvers(list: List<ManeuverEntity>)

    @Insert
    suspend fun insert(route: RouteEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RoutePointEntity>)

    @Transaction
    suspend fun insertWithPoints(route: RouteEntity, points: List<RoutePointEntity>): Long {
        val id = insert(route)
        insertPoints(points.map { it.copy(routeId = id) })
        return id
    }

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM routes")
    suspend fun all(): List<RouteEntity>

    @Query("SELECT * FROM route_points")
    suspend fun allPoints(): List<RoutePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(routes: List<RouteEntity>)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdMs, id")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdMs, id")
    suspend fun all(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface SensorDao {
    @Query("SELECT * FROM sensors ORDER BY name")
    fun observeAll(): Flow<List<SensorEntity>>

    @Query("SELECT * FROM sensors")
    suspend fun all(): List<SensorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sensor: SensorEntity)

    @Query("DELETE FROM sensors WHERE address = :address")
    suspend fun delete(address: String)
}

@Dao
interface TrainingDao {
    @Query("SELECT * FROM training_plans ORDER BY createdMs DESC LIMIT 1")
    fun observeCurrentPlan(): Flow<TrainingPlanEntity?>

    @Query("SELECT * FROM planned_sessions WHERE planId = :planId ORDER BY epochDay")
    fun observeSessions(planId: Long): Flow<List<PlannedSessionEntity>>

    @Insert
    suspend fun insertPlan(plan: TrainingPlanEntity): Long

    @Insert
    suspend fun insertSessions(list: List<PlannedSessionEntity>)

    @Transaction
    suspend fun replacePlan(plan: TrainingPlanEntity, sessions: List<PlannedSessionEntity>): Long {
        deleteAllPlans()
        val id = insertPlan(plan)
        insertSessions(sessions.map { it.copy(planId = id) })
        return id
    }

    @Query("UPDATE planned_sessions SET doneActivityId = :activityId WHERE id = :sessionId")
    suspend fun markDone(sessionId: Long, activityId: Long)

    @Query("DELETE FROM training_plans")
    suspend fun deleteAllPlans()
}

