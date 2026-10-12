package id.web.agentvaedah.strideway.data.files

import androidx.room.withTransaction
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.data.db.ActivityEntity
import id.web.agentvaedah.strideway.data.db.RouteEntity
import id.web.agentvaedah.strideway.data.db.RoutePointEntity
import id.web.agentvaedah.strideway.data.db.SensorEntity
import id.web.agentvaedah.strideway.data.db.StridewayDatabase
import id.web.agentvaedah.strideway.data.db.TrackPointEntity
import id.web.agentvaedah.strideway.data.prefs.PrivacyZoneSetting
import id.web.agentvaedah.strideway.data.prefs.SettingsRepository
import id.web.agentvaedah.strideway.core.model.LatLon
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupProfile(
    val gender: String,
    val heightCm: Double,
    val weightKg: Double,
    val age: Int,
    val hrMax: Int,
    val hrRest: Int,
    val ftpWatts: Int,
    val bikeWeightKg: Double,
)

@Serializable
data class BackupPrivacy(val enabled: Boolean, val lat: Double?, val lon: Double?, val radiusM: Double)

/** API keys are deliberately absent: they stay bound to this device's Keystore. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdMs: Long,
    val profile: BackupProfile,
    val privacy: BackupPrivacy,
    val wheelCircumferenceM: Double,
    val activities: List<ActivityEntity>,
    val points: List<TrackPointEntity>,
    val routes: List<RouteEntity>,
    val routePoints: List<RoutePointEntity>,
    val sensors: List<SensorEntity>,
) {
    companion object {
        const val FORMAT = "strideway-backup"
    }
}

data class BackupSummary(val activities: Int, val routes: Int)

@Singleton
class BackupManager @Inject constructor(
    private val db: StridewayDatabase,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(): Pair<String, BackupSummary> {
        val a = settings.athlete.first()
        val p = settings.privacyZone.first()
        val file = BackupFile(
            createdMs = System.currentTimeMillis(),
            profile = BackupProfile(a.gender.name, a.heightCm, a.weightKg, a.age, a.hrMax, a.hrRest, a.ftpWatts, a.bikeWeightKg),
            privacy = BackupPrivacy(p.enabled, p.center?.lat, p.center?.lon, p.radiusM),
            wheelCircumferenceM = settings.wheelCircumferenceM.first(),
            activities = db.activityDao().all().filter { it.elapsedMs > 0 },
            points = db.trackPointDao().all(),
            routes = db.routeDao().all(),
            routePoints = db.routeDao().allPoints(),
            sensors = db.sensorDao().all(),
        )
        val ids = file.activities.map { it.id }.toSet()
        val trimmed = file.copy(points = file.points.filter { it.activityId in ids })
        return json.encodeToString(BackupFile.serializer(), trimmed) to BackupSummary(trimmed.activities.size, trimmed.routes.size)
    }

    /** Replaces all local activities, routes and the profile with the backup contents. */
    suspend fun restore(text: String): Result<BackupSummary> {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            return Result.failure(IllegalArgumentException("File bukan backup Strideway yang valid"))
        } catch (e: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("File bukan backup Strideway yang valid"))
        }
        if (file.format != BackupFile.FORMAT) return Result.failure(IllegalArgumentException("File bukan backup Strideway"))

        db.withTransaction {
            db.activityDao().deleteAll()
            db.routeDao().deleteAll()
            db.activityDao().upsertAll(file.activities)
            file.points.chunked(CHUNK).forEach { db.trackPointDao().insertAll(it) }
            db.routeDao().upsertAll(file.routes)
            file.routePoints.chunked(CHUNK).forEach { db.routeDao().insertPoints(it) }
            file.sensors.forEach { db.sensorDao().upsert(it) }
        }
        with(file.profile) {
            settings.saveAthlete(
                Athlete(
                    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.MALE),
                    heightCm = heightCm, weightKg = weightKg, age = age, hrMax = hrMax, hrRest = hrRest,
                    ftpWatts = ftpWatts, bikeWeightKg = bikeWeightKg,
                ),
            )
        }
        with(file.privacy) {
            settings.savePrivacyZone(
                PrivacyZoneSetting(enabled, if (lat != null && lon != null) LatLon(lat, lon) else null, radiusM),
            )
        }
        settings.setWheelCircumference(file.wheelCircumferenceM)
        settings.setActiveRouteId(null)
        return Result.success(BackupSummary(file.activities.size, file.routes.size))
    }

    private companion object {
        const val CHUNK = 2_000
    }
}
