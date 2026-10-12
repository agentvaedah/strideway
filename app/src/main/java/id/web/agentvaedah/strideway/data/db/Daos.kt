package id.web.agentvaedah.strideway.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
