package id.web.agentvaedah.strideway.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import id.web.agentvaedah.strideway.core.model.Sport

class Converters {
    @TypeConverter
    fun sportToString(s: Sport): String = s.name

    @TypeConverter
    fun stringToSport(s: String): Sport = Sport.valueOf(s)

    @TypeConverter
    fun kindToString(k: SensorKind): String = k.name

    @TypeConverter
    fun stringToKind(s: String): SensorKind = SensorKind.valueOf(s)
}

@Database(
    entities = [
        ActivityEntity::class,
        TrackPointEntity::class,
        RouteEntity::class,
        RoutePointEntity::class,
        ChatMessageEntity::class,
        SensorEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StridewayDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun routeDao(): RouteDao
    abstract fun chatDao(): ChatDao
    abstract fun sensorDao(): SensorDao
}
