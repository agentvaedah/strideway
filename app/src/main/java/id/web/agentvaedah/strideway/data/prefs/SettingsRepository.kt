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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import id.web.agentvaedah.strideway.core.model.Athlete
import id.web.agentvaedah.strideway.core.model.Gender
import id.web.agentvaedah.strideway.core.model.LatLon
import id.web.agentvaedah.strideway.core.model.Sport
import id.web.agentvaedah.strideway.data.remote.llm.LlmProviderId
import id.web.agentvaedah.strideway.data.remote.llm.LlmSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class PrivacyZoneSetting(
    val enabled: Boolean = false,
    val center: LatLon? = null,
    val radiusM: Double = 500.0,
)

enum class MapBase(val label: String, val needsMapbox: Boolean = false) {
    STANDARD("Standar"),
    OSM("OSM"),
    SATELLITE("Satelit"),
    HYBRID("Hybrid"),
    TOPO("Topo"),
    MAPBOX_STREETS("Mapbox Streets", needsMapbox = true),
    MAPBOX_OUTDOORS("Mapbox Outdoors", needsMapbox = true),
    MAPBOX_SATELLITE("Mapbox Satelit", needsMapbox = true),
}

/** Extra layers drawn over the base map, toggled independently. */
enum class MapOverlay(val label: String) {
    POI("Tempat menarik"),
    BIKE_LANES("Jalur sepeda"),
    HEATMAP("Heatmap pribadi"),
}

enum class TerrainOverlay(val label: String) {
    NONE("Tanpa"),
    HILLSHADE("Relief"),
    SLOPE("Kemiringan"),
    AVALANCHE("Lereng >30°"),
    ASPECT("Arah lereng"),
}

enum class BikeType(val label: String) {
    ROAD("Road bike"),
    GRAVEL("Gravel"),
    MTB("MTB"),
    FOLDING("Sepeda lipat"),
    CITY("City / hybrid"),
    EBIKE("E-bike"),
    TT("Time trial"),
    NONE("Tidak bersepeda"),
}

const val BIO_MAX = 160

data class UserProfile(
    val name: String = "",
    val bikeType: BikeType = BikeType.ROAD,
    val bikeName: String = "",
    val bio: String = "",
    /** Absolute path of the stored profile picture; null when the rider has none. */
    val photoPath: String? = null,
)

data class FuelSettings(
    val enabled: Boolean = true,
    val drinkEveryMin: Int = 0,
    val eatEveryMin: Int = 0,
)

enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap"),
}

/** Where spoken cues come from. API voices fall back to [DEVICE] when offline or without a key. */
enum class TtsProviderId(val label: String, val keyId: String?) {
    DEVICE("Suara perangkat", null),
    OPENAI("OpenAI", "OPENAI"),
    ELEVENLABS("ElevenLabs", "TTS_ELEVENLABS"),
}

data class VoiceSettings(
    val enabled: Boolean = true,
    val provider: TtsProviderId = TtsProviderId.DEVICE,
    val elevenVoiceId: String = DEFAULT_ELEVEN_VOICE,
    val split: Boolean = true,
    val route: Boolean = true,
    val pause: Boolean = true,
    val heartRate: Boolean = true,
    val routeAlerts: Boolean = true,
) {
    companion object {
        const val DEFAULT_ELEVEN_VOICE = "21m00Tcm4TlvDq8ikWAM"
    }
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
        val overlays = stringSetPreferencesKey("map_overlays")
        val maps3d = booleanPreferencesKey("map_3d")
        val mapsSport = stringPreferencesKey("map_sport")

        val llmProvider = stringPreferencesKey("llm_provider")
        fun llmModel(p: LlmProviderId) = stringPreferencesKey("llm_model_${p.name}")
        fun llmBaseUrl(p: LlmProviderId) = stringPreferencesKey("llm_base_${p.name}")

        val wheel = doublePreferencesKey("wheel_circumference")
        val activeRoute = longPreferencesKey("active_route")

        val voiceEnabled = booleanPreferencesKey("voice_enabled")
        val voiceProvider = stringPreferencesKey("voice_provider")
        val elevenVoice = stringPreferencesKey("voice_eleven_voice_id")
        val cueSplit = booleanPreferencesKey("cue_split")
        val cueRoute = booleanPreferencesKey("cue_route")
        val cuePause = booleanPreferencesKey("cue_pause")
        val cueHr = booleanPreferencesKey("cue_hr")
        val routeAlerts = booleanPreferencesKey("route_alerts_enabled")

        val theme = stringPreferencesKey("ui_theme")

        val userName = stringPreferencesKey("user_name")
        val bikeType = stringPreferencesKey("user_bike_type")
        val bikeName = stringPreferencesKey("user_bike_name")
        val userBio = stringPreferencesKey("user_bio")
        val userPhoto = stringPreferencesKey("user_photo_path")

        val joinedChallenges = stringSetPreferencesKey("challenges_joined")
        val awards = stringSetPreferencesKey("challenge_awards")

        val mapboxToken = stringPreferencesKey("mapbox_token")

        val fuelEnabled = booleanPreferencesKey("fuel_enabled")
        val fuelDrink = intPreferencesKey("fuel_drink_min")
        val fuelEat = intPreferencesKey("fuel_eat_min")
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

    val mapOverlays: Flow<Set<MapOverlay>> = store.data.map { p ->
        p[Keys.overlays].orEmpty().mapNotNull { runCatching { MapOverlay.valueOf(it) }.getOrNull() }.toSet()
    }.distinctUntilChanged()

    suspend fun setMapOverlay(o: MapOverlay, on: Boolean) {
        store.edit { p ->
            val cur = p[Keys.overlays].orEmpty()
            p[Keys.overlays] = if (on) cur + o.name else cur - o.name
        }
    }

    val maps3d: Flow<Boolean> = store.data.map { it[Keys.maps3d] ?: false }.distinctUntilChanged()

    suspend fun setMaps3d(on: Boolean) {
        store.edit { it[Keys.maps3d] = on }
    }

    /** Sport chosen on the Maps tab; decides the routing profile for planned and drawn routes. */
    val mapsSport: Flow<Sport> = store.data.map { p ->
        p[Keys.mapsSport]?.let { runCatching { Sport.valueOf(it) }.getOrNull() } ?: Sport.RIDE
    }.distinctUntilChanged()

    suspend fun setMapsSport(s: Sport) {
        store.edit { it[Keys.mapsSport] = s.name }
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

    val voice: Flow<VoiceSettings> = store.data.map { p ->
        val d = VoiceSettings()
        VoiceSettings(
            enabled = p[Keys.voiceEnabled] ?: d.enabled,
            provider = p[Keys.voiceProvider]?.let { runCatching { TtsProviderId.valueOf(it) }.getOrNull() } ?: d.provider,
            elevenVoiceId = p[Keys.elevenVoice] ?: d.elevenVoiceId,
            split = p[Keys.cueSplit] ?: d.split,
            route = p[Keys.cueRoute] ?: d.route,
            pause = p[Keys.cuePause] ?: d.pause,
            heartRate = p[Keys.cueHr] ?: d.heartRate,
            routeAlerts = p[Keys.routeAlerts] ?: d.routeAlerts,
        )
    }.distinctUntilChanged()

    suspend fun saveVoice(v: VoiceSettings) {
        store.edit { p ->
            p[Keys.voiceEnabled] = v.enabled
            p[Keys.voiceProvider] = v.provider.name
            p[Keys.elevenVoice] = v.elevenVoiceId.trim().ifEmpty { VoiceSettings.DEFAULT_ELEVEN_VOICE }
            p[Keys.cueSplit] = v.split
            p[Keys.cueRoute] = v.route
            p[Keys.cuePause] = v.pause
            p[Keys.cueHr] = v.heartRate
            p[Keys.routeAlerts] = v.routeAlerts
        }
    }

    suspend fun setRouteAlerts(on: Boolean) {
        store.edit { it[Keys.routeAlerts] = on }
    }

    val themeMode: Flow<ThemeMode> = store.data.map { p ->
        p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }.distinctUntilChanged()

    suspend fun setThemeMode(m: ThemeMode) {
        store.edit { it[Keys.theme] = m.name }
    }

    val profile: Flow<UserProfile> = store.data.map { p ->
        UserProfile(
            name = p[Keys.userName] ?: "",
            bikeType = p[Keys.bikeType]?.let { runCatching { BikeType.valueOf(it) }.getOrNull() } ?: BikeType.ROAD,
            bikeName = p[Keys.bikeName] ?: "",
            bio = p[Keys.userBio] ?: "",
            photoPath = p[Keys.userPhoto],
        )
    }.distinctUntilChanged()

    suspend fun saveProfile(u: UserProfile) {
        store.edit { p ->
            p[Keys.userName] = u.name.trim().take(40)
            p[Keys.bikeType] = u.bikeType.name
            p[Keys.bikeName] = u.bikeName.trim().take(40)
            p[Keys.userBio] = u.bio.trim().take(BIO_MAX)
        }
    }

    /** Separate from [saveProfile] so editing the text fields never drops a picture picked meanwhile. */
    suspend fun setProfilePhoto(path: String?) {
        store.edit { p -> if (path == null) p.remove(Keys.userPhoto) else p[Keys.userPhoto] = path }
    }

    val joinedChallenges: Flow<Set<String>> = store.data.map { it[Keys.joinedChallenges] ?: emptySet() }.distinctUntilChanged()

    suspend fun setChallengeJoined(id: String, joined: Boolean) {
        store.edit { p ->
            val cur = p[Keys.joinedChallenges] ?: emptySet()
            p[Keys.joinedChallenges] = if (joined) cur + id else cur - id
        }
    }

    /** Award keys as "CHALLENGE@yyyy-MM"; kept forever so past months stay in the trophy case. */
    val awards: Flow<Set<String>> = store.data.map { it[Keys.awards] ?: emptySet() }.distinctUntilChanged()

    suspend fun addAwards(keys: Collection<String>) {
        if (keys.isEmpty()) return
        store.edit { p -> p[Keys.awards] = (p[Keys.awards] ?: emptySet()) + keys }
    }

    val mapboxToken: Flow<String?> = store.data.map { it[Keys.mapboxToken]?.takeIf(String::isNotBlank) }.distinctUntilChanged()

    suspend fun setMapboxToken(token: String?) {
        store.edit { p -> if (token.isNullOrBlank()) p.remove(Keys.mapboxToken) else p[Keys.mapboxToken] = token.trim() }
    }

    /** Intervals of 0 mean "use the plan computed from duration and temperature". */
    val fuel: Flow<FuelSettings> = store.data.map { p ->
        FuelSettings(p[Keys.fuelEnabled] ?: true, p[Keys.fuelDrink] ?: 0, p[Keys.fuelEat] ?: 0)
    }.distinctUntilChanged()

    suspend fun saveFuel(f: FuelSettings) {
        store.edit { p ->
            p[Keys.fuelEnabled] = f.enabled
            p[Keys.fuelDrink] = f.drinkEveryMin.coerceIn(0, 120)
            p[Keys.fuelEat] = f.eatEveryMin.coerceIn(0, 180)
        }
    }
}
