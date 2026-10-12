package id.web.agentvaedah.strideway.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.data.remote.llm.LlmProviderId
import id.web.agentvaedah.strideway.data.remote.llm.LlmSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class PrivacyZoneSetting(
    val enabled: Boolean = false,
    val center: LatLon? = null,
    val radiusM: Double = 500.0,
)

enum class MapBase(val label: String) {
    STANDARD("Standar"),
    SATELLITE("Satelit"),
    HYBRID("Hybrid"),
}

enum class TerrainOverlay(val label: String) {
    NONE("Tanpa"),
    HILLSHADE("Relief"),
    SLOPE("Kemiringan"),
    AVALANCHE("Lereng >30°"),
    ASPECT("Arah lereng"),
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore

    private object Keys {
        val gender = stringPreferencesKey("athlete_gender")
        val height = doublePreferencesKey("athlete_height")
        val weight = doublePreferencesKey("athlete_weight")
        val age = intPreferencesKey("athlete_age")
        val hrMax = intPreferencesKey("athlete_hr_max")
        val hrRest = intPreferencesKey("athlete_hr_rest")
        val ftp = intPreferencesKey("athlete_ftp")
        val bikeWeight = doublePreferencesKey("athlete_bike_weight")
        val profileSaved = booleanPreferencesKey("athlete_saved")

        val privacyEnabled = booleanPreferencesKey("privacy_enabled")
        val privacyLat = doublePreferencesKey("privacy_lat")
        val privacyLon = doublePreferencesKey("privacy_lon")
        val privacyRadius = doublePreferencesKey("privacy_radius")

        val mapBase = stringPreferencesKey("map_base")
        val terrain = stringPreferencesKey("map_terrain")

        val llmProvider = stringPreferencesKey("llm_provider")
        fun llmModel(p: LlmProviderId) = stringPreferencesKey("llm_model_${p.name}")
        fun llmBaseUrl(p: LlmProviderId) = stringPreferencesKey("llm_base_${p.name}")

        val wheel = doublePreferencesKey("wheel_circumference")
        val activeRoute = longPreferencesKey("active_route")
    }

    val athlete: Flow<Athlete> = store.data.map { p ->
        val d = Athlete()
        Athlete(
            gender = p[Keys.gender]?.let { runCatching { Gender.valueOf(it) }.getOrNull() } ?: d.gender,
            heightCm = p[Keys.height] ?: d.heightCm,
            weightKg = p[Keys.weight] ?: d.weightKg,
            age = p[Keys.age] ?: d.age,
            hrMax = p[Keys.hrMax] ?: d.hrMax,
            hrRest = p[Keys.hrRest] ?: d.hrRest,
            ftpWatts = p[Keys.ftp] ?: d.ftpWatts,
            bikeWeightKg = p[Keys.bikeWeight] ?: d.bikeWeightKg,
        )
    }.distinctUntilChanged()

    /** False until the athlete saves the profile once; defaults are only a starting point. */
    val profileSaved: Flow<Boolean> = store.data.map { it[Keys.profileSaved] ?: false }

    suspend fun saveAthlete(a: Athlete) {
        store.edit { p ->
            p[Keys.gender] = a.gender.name
            p[Keys.height] = a.heightCm
            p[Keys.weight] = a.weightKg
            p[Keys.age] = a.age
            p[Keys.hrMax] = a.hrMax
            p[Keys.hrRest] = a.hrRest
            p[Keys.ftp] = a.ftpWatts
            p[Keys.bikeWeight] = a.bikeWeightKg
            p[Keys.profileSaved] = true
        }
    }

    val privacyZone: Flow<PrivacyZoneSetting> = store.data.map { p ->
        val lat = p[Keys.privacyLat]
        val lon = p[Keys.privacyLon]
        PrivacyZoneSetting(
            enabled = p[Keys.privacyEnabled] ?: false,
            center = if (lat != null && lon != null) LatLon(lat, lon) else null,
            radiusM = p[Keys.privacyRadius] ?: 500.0,
        )
    }.distinctUntilChanged()

    suspend fun savePrivacyZone(z: PrivacyZoneSetting) {
        store.edit { p ->
            p[Keys.privacyEnabled] = z.enabled
            p[Keys.privacyRadius] = z.radiusM
            if (z.center != null) {
                p[Keys.privacyLat] = z.center.lat
                p[Keys.privacyLon] = z.center.lon
            } else {
                p.remove(Keys.privacyLat)
                p.remove(Keys.privacyLon)
            }
        }
    }

    val mapBase: Flow<MapBase> = store.data.map { p ->
        p[Keys.mapBase]?.let { runCatching { MapBase.valueOf(it) }.getOrNull() } ?: MapBase.STANDARD
    }.distinctUntilChanged()

    val terrainOverlay: Flow<TerrainOverlay> = store.data.map { p ->
        p[Keys.terrain]?.let { runCatching { TerrainOverlay.valueOf(it) }.getOrNull() } ?: TerrainOverlay.NONE
    }.distinctUntilChanged()

    suspend fun setMapBase(b: MapBase) {
        store.edit { it[Keys.mapBase] = b.name }
    }

    suspend fun setTerrainOverlay(o: TerrainOverlay) {
        store.edit { it[Keys.terrain] = o.name }
    }

    val llm: Flow<LlmSettings> = store.data.map { p ->
        val provider = p[Keys.llmProvider]?.let { runCatching { LlmProviderId.valueOf(it) }.getOrNull() } ?: LlmProviderId.OPENAI
        LlmSettings(
            provider = provider,
            model = p[Keys.llmModel(provider)] ?: provider.defaultModel,
            baseUrl = p[Keys.llmBaseUrl(provider)] ?: provider.defaultBaseUrl,
        )
    }.distinctUntilChanged()

    suspend fun llmFor(provider: LlmProviderId): LlmSettings {
        val p = store.data.first()
        return LlmSettings(
            provider = provider,
            model = p[Keys.llmModel(provider)] ?: provider.defaultModel,
            baseUrl = p[Keys.llmBaseUrl(provider)] ?: provider.defaultBaseUrl,
        )
    }

    suspend fun saveLlm(s: LlmSettings) {
        store.edit { p ->
            p[Keys.llmProvider] = s.provider.name
            p[Keys.llmModel(s.provider)] = s.model.trim()
            p[Keys.llmBaseUrl(s.provider)] = s.baseUrl.trim().trimEnd('/')
        }
    }

    val wheelCircumferenceM: Flow<Double> = store.data.map { it[Keys.wheel] ?: 2.105 }.distinctUntilChanged()

    suspend fun setWheelCircumference(m: Double) {
        store.edit { it[Keys.wheel] = m }
    }

    val activeRouteId: Flow<Long?> = store.data.map { it[Keys.activeRoute] }.distinctUntilChanged()

    suspend fun setActiveRouteId(id: Long?) {
        store.edit { p -> if (id == null) p.remove(Keys.activeRoute) else p[Keys.activeRoute] = id }
    }
}
