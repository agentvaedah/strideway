# Strideway Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android app Strideway (PRD v3.1.0 fase 1 sampai 3): 5 tab, perekaman GPS 500 ms, BLE sensor, peta multi-layer, import/export GPX/TCX/FIT, AI Coach dan AI Route BYOK.

**Architecture:** Dua modul Gradle. `:core` Kotlin/JVM murni berisi semua rumus, parser, dan algoritma dengan unit test JUnit 5. `:app` Android (Compose M3, Hilt, Room, DataStore, Ktor, MapLibre) memakai `:core` untuk logika dan hanya berisi UI, I/O perangkat, dan persistence. MVVM: ViewModel expose `StateFlow<UiState>`, Room sebagai sumber kebenaran.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, Compose BOM 2024.12.01, Hilt 2.54, Room 2.6.1, DataStore 1.1.1, Ktor 3.0.3, kotlinx-serialization 1.7.3, MapLibre Android 11.8.0, JUnit 5.11.4.

**Spec:** `docs/superpowers/specs/2026-10-12-strideway-android-design.md`

## Global Constraints

- Package: `id.web.agentvaedah.strideway` (app), `id.web.agentvaedah.strideway.core` (core).
- compileSdk 35, targetSdk 35, minSdk 26. Java/Kotlin JVM target 17, tanpa `jvmToolchain` (hindari download toolchain).
- Tidak ada build di laptop ini (tanpa SDK, tanpa network). Setiap file harus ditulis cermat: import lengkap, API sesuai versi yang dipin.
- Lokasi: `LocationManager.GPS_PROVIDER`, 500 ms, 0 m. Dilarang `play-services-location`.
- Auto-pause: < 2.5 km/j selama > 3 s.
- Kalori: Keytel 2005 (pria = rumus PRD; wanita = (−20.4022 + 0.4472·HR − 0.1263·BB + 0.074·U)/4.184), minimal 0.
- API key LLM: Android Keystore AES-256-GCM, tidak pernah ditulis ke log atau backup.
- Tema: dark tetap (head unit di stang, silau rendah malam hari). Palet: bg `#0E1012`, surface `#171A1D`, surfaceHigh `#22262A`, teks `#F2F4F5`, teks sekunder `#A9B0B6`, aksen `#FC4C02` (teks di atas aksen pakai `#0E1012`), info `#4FA3E0`, sukses `#5BC27A`, warning `#F2B53A`, error `#F0524F`. Semua lolos WCAG AA di surface.
- Zona HR warna: Z1 `#7D868E`, Z2 `#4FA3E0`, Z3 `#5BC27A`, Z4 `#F2B53A`, Z5 `#F0524F`.
- Teks UI Bahasa Indonesia, tanpa em dash, tanpa emoji, tanpa buzzword. Komentar kode sedikit, hanya alasan non-obvious.
- Setiap layar data punya state kosong, loading, error yang menyebut sebab dan aksi berikutnya.
- Git lokal saja, tanpa remote.

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.properties, gradlew, gradlew.bat, .gitignore, README.md
core/build.gradle.kts
core/src/main/kotlin/id/web/agentvaedah/strideway/core/
  model/Models.kt            Sport, Gender, Athlete, LatLon, TrackPoint, Track, ActivitySummary
  physio/Calories.kt, Zones.kt, TrainingLoad.kt, PowerEstimator.kt
  geo/Geo.kt, ElevationGain.kt, GradeWindow.kt, Polyline.kt, TileMath.kt
  telemetry/AutoPause.kt, DeadReckoning.kt, SessionAccumulator.kt, LiveMetrics.kt
  sensor/GattParsers.kt, CadenceCalculator.kt
  track/MiniXml.kt, Gpx.kt, Tcx.kt, FitDecoder.kt, TrackFormats.kt, TrackStats.kt
  terrain/DemMath.kt
  privacy/PrivacyZone.kt
  challenge/Challenges.kt, TrainingCalendar.kt
  route/RouteIntent.kt
  format/Format.kt
core/src/test/kotlin/... (satu test per file sumber)
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml, res/ (strings, themes, adaptive icon placeholder "S", xml/file_paths)
app/src/main/java/id/web/agentvaedah/strideway/
  StridewayApp.kt, MainActivity.kt
  di/AppModule.kt
  data/db/  StridewayDatabase.kt, Entities.kt, Daos.kt, Converters.kt
  data/prefs/SettingsRepository.kt
  data/security/SecureKeyStore.kt
  data/repo/ActivityRepository.kt, RouteRepository.kt, ChatRepository.kt, SensorRepository.kt
  data/remote/HttpClientFactory.kt
  data/remote/llm/LlmProvider.kt, LlmClient.kt
  data/remote/routing/RoutingClient.kt
  data/remote/sync/RemoteSyncApi.kt
  data/ble/BleHub.kt
  data/location/GpsSource.kt, MotionSensors.kt
  data/files/TrackFileIo.kt, BackupManager.kt
  service/RecordingService.kt, RecordingController.kt, HardwareKeyBus.kt
  ui/theme/Color.kt, Type.kt, Theme.kt
  ui/navigation/StridewayNavHost.kt, BottomBar.kt, Routes.kt
  ui/components/ (MetricTile, TrackThumbnail, LineChart, StateViews, MapLibreView, SectionHeader)
  ui/home/, ui/activity/, ui/maps/, ui/record/, ui/coach/, ui/you/
```

---

### Task 1: Gradle skeleton + `:core` models + format

**Files:** semua file root Gradle, `core/build.gradle.kts`, `core/.../model/Models.kt`, `core/.../format/Format.kt`, test `FormatTest.kt`, `.gitignore`.

**Interfaces (Produces):**
```kotlin
package id.web.agentvaedah.strideway.core.model
enum class Sport { RIDE, RUN }
enum class Gender { MALE, FEMALE }
data class Athlete(val gender: Gender = Gender.MALE, val heightCm: Double = 170.0, val weightKg: Double = 70.0,
  val age: Int = 30, val hrMax: Int = 190, val hrRest: Int = 60, val ftpWatts: Int = 200, val bikeWeightKg: Double = 9.0)
data class LatLon(val lat: Double, val lon: Double)
data class TrackPoint(val lat: Double, val lon: Double, val ele: Double? = null, val timeMs: Long? = null,
  val hr: Int? = null, val cadence: Int? = null, val power: Int? = null) { val latLon get() = LatLon(lat, lon) }
data class Track(val name: String?, val sport: Sport?, val points: List<TrackPoint>)
data class ActivitySummary(val sport: Sport, val startMs: Long, val distanceM: Double, val movingMs: Long, val trimp: Double)

package id.web.agentvaedah.strideway.core.format
object Format {
  fun duration(ms: Long): String          // 0:05:09 -> "5:09", 3723000 -> "1:02:03"
  fun km(meters: Double): String          // 12345.0 -> "12.35"
  fun speedKmh(mps: Double): Double       // mps * 3.6
  fun pace(mps: Double): String           // min/km, 3.0 m/s -> "5:33", <0.3 -> "--:--"
  fun oneDecimal(v: Double): String       // Locale.US
}
```

- [ ] Step 1: tulis `FormatTest` (duration 309000→"5:09", 3723000→"1:02:03", 0→"0:00"; km 12345.0→"12.35"; pace 3.0→"5:33", 0.1→"--:--").
- [ ] Step 2: implement, lalu commit `chore: gradle skeleton and core models`.

### Task 2: physio (kalori, zona, TRIMP/CTL/ATL, watt)

**Interfaces:**
```kotlin
object Calories { fun kcalPerMinute(hr: Double, weightKg: Double, age: Int, gender: Gender): Double
                  fun kcalPerMinuteMet(sport: Sport, weightKg: Double): Double } // ride MET 8.0, run 9.8; MET*3.5*kg/200
data class Zone(val index: Int, val name: String, val low: Int, val high: Int)   // high = Int.MAX_VALUE untuk zona terbuka
object Zones { fun heartRate(hrMax: Int, hrRest: Int?): List<Zone>  // 5 zona 50/60/70/80/90/100 %, Karvonen bila hrRest != null
               fun zoneOf(value: Int, zones: List<Zone>): Int        // 0 bila di bawah Z1
               fun power(ftp: Int): List<Zone> }                     // Coggan 7: <=55,56-75,76-90,91-105,106-120,121-150,>150 %
object TrainingLoad {
  fun trimp(durationMin: Double, avgHr: Double, hrRest: Int, hrMax: Int, gender: Gender): Double
  data class DailyLoad(val epochDay: Long, val load: Double)
  data class FitnessPoint(val epochDay: Long, val fitness: Double, val fatigue: Double) { val form: Double get() = fitness - fatigue }
  fun series(loads: List<DailyLoad>, fromDay: Long, toDay: Long): List<FitnessPoint>  // CTL k=42, ATL k=7, x += (load-x)/k, mulai 0 dari hari load pertama
}
object PowerEstimator { fun watts(speedMps: Double, gradePercent: Double, totalMassKg: Double,
  cda: Double = 0.32, crr: Double = 0.005, rho: Double = 1.225): Double }
```
Nilai acuan (dihitung Python): Keytel pria HR150/70kg/30th = 14.2221; wanita HR150/60kg/30th = 9.8757; TRIMP pria 60 mnt HR150 rest60 max190 = 100.440, wanita = 113.517; 7 hari load 100: CTL 15.5223, ATL 66.0083; watt 30 km/j datar 80 kg = 146.115, 15 km/j 5 % 80 kg = 193.743.

- [ ] TDD: test dengan nilai acuan (toleransi 1e-3), lalu implement, commit `feat(core): physiology formulas`.

### Task 3: geo + telemetry

**Interfaces:**
```kotlin
object Geo { fun distanceM(a: LatLon, b: LatLon): Double   // haversine R=6371008.8
             fun bearingDeg(a: LatLon, b: LatLon): Double  // 0..360
             fun destination(from: LatLon, bearingDeg: Double, distanceM: Double): LatLon }
class ElevationGain(private val threshold: Double = 3.0) { fun add(ele: Double); val gainM: Double; val lossM: Double }
class GradeWindow(private val windowM: Double = 50.0) { fun add(cumDistanceM: Double, ele: Double): Double? }
object Polyline { fun encode(points: List<LatLon>): String; fun decode(encoded: String): List<LatLon> }
object TileMath { fun lonToTileX(lon: Double, z: Int): Int; fun latToTileY(lat: Double, z: Int): Int
                  fun tileWest(x: Int, z: Int): Double; fun tileNorth(y: Int, z: Int): Double }
class AutoPause(thresholdKmh: Double = 2.5, delayMs: Long = 3000) { fun update(speedKmh: Double, timeMs: Long): Boolean; val isPaused: Boolean }
class DeadReckoning(staleAfterMs: Long = 1500, maxDurationMs: Long = 30_000) {
  fun onGpsFix(pos: LatLon, speedMps: Double, bearingDeg: Double, timeMs: Long)
  fun onForwardAcceleration(accMps2: Double, timeMs: Long)
  fun onHeading(bearingDeg: Double)
  fun estimate(timeMs: Long): LatLon? }   // null bila GPS segar, belum ada fix, atau lewat maxDuration
data class LiveMetrics(...)   // lihat SessionAccumulator
class SessionAccumulator(athlete: Athlete, sport: Sport, startMs: Long) {
  fun onLocation(timeMs: Long, pos: LatLon, altitudeM: Double?, speedMps: Double?, interpolated: Boolean)
  fun onHeartRate(bpm: Int, timeMs: Long); fun onCadence(rpm: Int, timeMs: Long); fun onPower(watts: Int, timeMs: Long)
  fun onBarometricAltitude(altitudeM: Double)
  fun setManualPause(paused: Boolean, timeMs: Long)
  fun tick(timeMs: Long): LiveMetrics }
```
`LiveMetrics(elapsedMs, movingMs, distanceM, speedKmh, avgSpeedKmh, maxSpeedKmh, altitudeM: Double?, elevationGainM, gradePercent: Double?, heartRate: Int?, avgHeartRate: Int?, maxHeartRate: Int?, cadence: Int?, powerWatts: Int?, estimatedPowerWatts: Int?, calories: Double, autoPaused: Boolean, manualPaused: Boolean, deadReckoning: Boolean)`.
Aturan: lompatan > 30 m/s dibuang; jarak dan moving time hanya bertambah saat tidak pause; kalori pakai Keytel bila HR ada dan < 30 detik umurnya, else MET; avgSpeed = distance / moving; HR/cadence/power dianggap basi setelah 5 s (jadi null).
Acuan: Monas (−6.1754,106.8272) ke Bundaran HI (−6.1950,106.8230) = 2228.33 m, bearing 192.03°; 1° lintang = 111195.08 m; tile Monas z12 = (3263, 2118).

- [ ] TDD per kelas, commit `feat(core): geo and telemetry engine`.

### Task 4: sensor parser, track I/O, terrain, privacy, challenge, route intent

**Interfaces:**
```kotlin
object GattParsers { fun heartRate(v: ByteArray): Int?; fun battery(v: ByteArray): Int?
  data class Csc(val wheelRevs: Long?, val wheelEventTime: Int?, val crankRevs: Int?, val crankEventTime: Int?)
  fun csc(v: ByteArray): Csc; fun cyclingPowerWatts(v: ByteArray): Int? }
class CadenceCalculator { fun update(crankRevs: Int, eventTime1024: Int, nowMs: Long): Int? }   // uint16 rollover, 0 bila diam > 3 s
class WheelSpeedCalculator(var circumferenceM: Double = 2.105) { fun update(revs: Long, eventTime1024: Int): Double? }
class XmlNode(val name: String, val attrs: Map<String, String>, val children: List<XmlNode>, val text: String) {
  fun child(name: String): XmlNode?; fun children(name: String): List<XmlNode>; fun descendants(name: String): List<XmlNode> }
object MiniXml { fun parse(xml: String): XmlNode }   // nama tanpa prefix namespace
class TrackParseException(message: String) : Exception(message)
object Gpx { fun parse(xml: String): Track; fun write(track: Track): String }    // trkpt, rtept, wpt fallback; ekstensi gpxtpx:hr/cad
object Tcx { fun parse(xml: String): Track; fun write(track: Track, sport: Sport): String }
object FitDecoder { fun decode(bytes: ByteArray): Track }   // message record(20), sport(12)/session(18)
object TrackFormats { fun parse(fileName: String, bytes: ByteArray): Track }
object TrackStats { fun distanceM(points: List<TrackPoint>): Double; fun elevationGainM(points: List<TrackPoint>): Double }
object DemMath { fun terrariumElevation(r: Int, g: Int, b: Int): Double
  fun metersPerPixel(lat: Double, zoom: Int, tileSize: Int = 256): Double
  class SlopeAspect(val slopeDeg: FloatArray, val aspectDeg: FloatArray)
  fun slopeAspect(elev: DoubleArray, width: Int, height: Int, cellM: Double): SlopeAspect   // Horn; aspect −1 bila datar
  fun slopeColor(deg: Float): Int; fun avalancheColor(deg: Float): Int; fun aspectColor(aspect: Float, slope: Float): Int }   // ARGB
object PrivacyZone { fun <T> trim(points: List<T>, center: LatLon, radiusM: Double, pos: (T) -> LatLon): List<T> }
enum class ChallengeId { RIDE_500, GRAN_FONDO, RUN_100 }
data class ChallengeProgress(val id: ChallengeId, val currentKm: Double, val targetKm: Double) { val fraction: Double; val completed: Boolean }
object Challenges { fun monthly(activities: List<ActivitySummary>, month: YearMonth, zone: ZoneId): List<ChallengeProgress> }
object TrainingCalendar { fun activeDays(activities: List<ActivitySummary>, month: YearMonth, zone: ZoneId): Set<Int>
                          fun weeklyHours(activities: List<ActivitySummary>, month: YearMonth, zone: ZoneId): List<Pair<LocalDate, Double>> }  // Senin awal minggu
@Serializable data class RouteIntent(val start: String, val waypoints: List<String> = emptyList(), val end: String? = null,
  val sport: String = "road", val surface: String = "paved", val elevation: String = "any", val targetKm: Double? = null, val loop: Boolean = false)
object RouteIntents { fun parse(llmText: String): RouteIntent; fun brouterProfile(i: RouteIntent): String; val SYSTEM_PROMPT: String }
```
Acuan: Terrarium (128,0,0)=0.0 m, (129,44,128)=300.5 m; meter/piksel lat 0 z0 = 156543.034, lat −6.2 z12 = 37.995. FIT test memakai builder biner di test.

- [ ] TDD per file, commit `feat(core): sensors, track formats, terrain, challenges, route intent`.

### Task 5: `:app` fondasi: manifest, tema, navigasi 5 tab, Hilt, Room, DataStore, SecureKeyStore, repository

**Interfaces (Produces):**
```kotlin
// data/db
@Entity("activities") data class ActivityEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sport: Sport, val name: String,
  val startMs: Long, val elapsedMs: Long, val movingMs: Long, val distanceM: Double, val elevationGainM: Double, val calories: Double,
  val avgHr: Int?, val maxHr: Int?, val avgSpeedKmh: Double, val maxSpeedKmh: Double, val avgPower: Int?, val trimp: Double,
  val polyline: String, val source: String /* "record" | "import" */, val syncState: Int = 0)
@Entity("track_points", indices=[Index("activityId")], foreignKeys=[ForeignKey(ActivityEntity::class, ["id"], ["activityId"], onDelete = CASCADE)])
data class TrackPointEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val activityId: Long, val timeMs: Long, val lat: Double,
  val lon: Double, val ele: Double?, val speedMps: Double?, val hr: Int?, val cadence: Int?, val power: Int?, val interpolated: Boolean)
@Entity("routes") data class RouteEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val source: String /* import|ai */,
  val createdMs: Long, val distanceM: Double, val elevationGainM: Double, val polyline: String, val prompt: String?)
@Entity("route_points", ...) data class RoutePointEntity(id, routeId, seq: Int, lat, lon, ele: Double?)
@Entity("chat_messages") data class ChatMessageEntity(id, role: String /* user|assistant|error */, content: String, createdMs: Long, provider: String?)
@Entity("sensors") data class SensorEntity(@PrimaryKey val address: String, val name: String, val kind: String /* HR|CSC|POWER */, val autoConnect: Boolean = true)
// data/prefs
data class PrivacyZoneSetting(val enabled: Boolean, val center: LatLon?, val radiusM: Double = 500.0)
enum class MapBase { STANDARD, SATELLITE, HYBRID }; enum class TerrainOverlay { NONE, HILLSHADE, SLOPE, AVALANCHE, ASPECT }
data class LlmSettings(val provider: LlmProviderId, val model: String, val baseUrl: String)
class SettingsRepository { val athlete: Flow<Athlete>; suspend fun saveAthlete(a: Athlete)
  val privacyZone: Flow<PrivacyZoneSetting>; suspend fun savePrivacyZone(p: PrivacyZoneSetting)
  val mapBase: Flow<MapBase>; val terrainOverlay: Flow<TerrainOverlay>; suspend fun setMapBase(b: MapBase); suspend fun setTerrainOverlay(o: TerrainOverlay)
  val llm: Flow<LlmSettings>; suspend fun saveLlm(s: LlmSettings)
  val wheelCircumferenceM: Flow<Double>; suspend fun setWheelCircumference(m: Double)
  val activeRouteId: Flow<Long?>; suspend fun setActiveRouteId(id: Long?) }
class SecureKeyStore { suspend fun put(provider: LlmProviderId, apiKey: String); suspend fun get(provider: LlmProviderId): String?
  suspend fun remove(provider: LlmProviderId); fun hasKey(provider: LlmProviderId): Flow<Boolean> }
// data/repo
class ActivityRepository { fun activities(): Flow<List<ActivityEntity>>; fun activity(id: Long): Flow<ActivityEntity?>
  suspend fun points(id: Long): List<TrackPointEntity>; suspend fun createLive(sport: Sport, startMs: Long): Long
  suspend fun appendPoints(points: List<TrackPointEntity>); suspend fun finish(id: Long, m: LiveMetrics, athlete: Athlete)
  suspend fun importTrack(track: Track, athlete: Athlete): Long; suspend fun delete(id: Long); suspend fun rename(id: Long, name: String)
  fun summaries(): Flow<List<ActivitySummary>> }
class RouteRepository { fun routes(): Flow<List<RouteEntity>>; suspend fun points(routeId: Long): List<RoutePointEntity>
  suspend fun save(name: String, source: String, points: List<TrackPoint>, prompt: String?): Long; suspend fun delete(id: Long) }
class ChatRepository { fun messages(): Flow<List<ChatMessageEntity>>; suspend fun add(role: String, content: String, provider: String?): Long; suspend fun clear() }
```
Navigasi: route `home`, `maps`, `record`, `coach`, `you`, `activity/{id}`, `coach/keys`, `you/devices`, `you/privacy`. Bottom bar: 4 tab biasa + tombol Record tengah (lingkaran 56 dp oranye, ikon titik rekam, label "Rekam"). Ikon: Material Icons Extended (Home, Map, Forum, Person) dengan alasan: set ikon bawaan M3 konsisten dengan komponen M3, bukan Lucide.

- [ ] Tulis semua file, review silang import dan anotasi Hilt/Room, commit `feat(app): foundation, navigation, persistence`.

### Task 6: Recording engine (service) + Record UI

**Files:** `data/location/GpsSource.kt`, `data/location/MotionSensors.kt`, `service/RecordingController.kt`, `service/RecordingService.kt`, `service/HardwareKeyBus.kt`, `ui/record/*`, `ui/components/MapLibreView.kt`, `MainActivity` key handling.
**Interfaces:**
```kotlin
sealed interface RecordingState { data object Idle : RecordingState
  data class Active(val activityId: Long, val sport: Sport, val metrics: LiveMetrics, val path: List<LatLon>, val position: LatLon?,
    val headingDeg: Float?, val elevationProfile: List<Float>, val gpsFix: GpsFix, val sensors: List<SensorStatus>) : RecordingState
  data class Finished(val activityId: Long) : RecordingState }
enum class GpsFix { SEARCHING, OK, DEAD_RECKONING }
@Singleton class RecordingController { val state: StateFlow<RecordingState>; fun start(sport: Sport); fun togglePause(); fun stop(); fun discard() }
enum class HardwareKey { VOLUME_UP, VOLUME_DOWN }
@Singleton class HardwareKeyBus { var recordScreenActive: Boolean; val events: SharedFlow<HardwareKey>; fun emit(k: HardwareKey): Boolean }
```
GpsSource: `callbackFlow` dari `LocationManager.requestLocationUpdates(GPS_PROVIDER, 500L, 0f, listener, Looper)`. MotionSensors: `TYPE_LINEAR_ACCELERATION` + `TYPE_ROTATION_VECTOR` pada 20 000 µs (50 Hz), `TYPE_PRESSURE` bila ada (`SensorManager.getAltitude`). Proyeksi akselerasi ke arah heading untuk akselerasi maju. Titik ke Room tiap 5 s. Controller mengamankan sesi bila proses mati: `activities` dengan `elapsedMs = 0` dan `source = "record"` dianggap sesi terputus, ditawarkan "Lanjutkan simpan" di Record.

- [ ] Tulis file, review, commit `feat(app): live recording engine and head unit dashboard`.

### Task 7: BLE hub + Device Manager

`BleHub` (@Singleton): scan filter UUID HRS `0000180d-...`, CSC `00001816-...`, CP `00001818-...`; connect via `BluetoothDevice.connectGatt(ctx, false, cb, TRANSPORT_LE)`; enable notify CCCD `00002902-...`; antre operasi GATT (satu per satu); reconnect backoff 2, 4, 8, 16, 30 s; expose `StateFlow<List<SensorStatus>>`, `SharedFlow<SensorReading>`. `data class SensorStatus(address, name, kind, connected: Boolean, batteryPct: Int?)`, `sealed SensorReading { HeartRate(bpm), Cadence(rpm), WheelSpeed(mps), Power(watts) }`. UI `ui/you/DevicesScreen.kt`: izin, scan 15 s, daftar hasil, sambung/lupakan, catatan Huawei/Garmin HR broadcast.

- [ ] Tulis, review, commit `feat(app): BLE sensor hub and device manager`.

### Task 8: Home + detail aktivitas

Home (LazyColumn, bukan grid kartu seragam): kartu Form/Fitness/Fatigue dengan grafik garis 42 hari (fokus utama layar), Monthly Challenges (3 baris progress, lencana saat selesai), kalender bulan + jam per minggu, feed aktivitas (thumbnail Canvas dari polyline setelah privacy trim, jarak, waktu, elevasi, kalori). Detail: peta MapLibre jalur, grafik elevasi/HR/kecepatan, statistik, rename, hapus (konfirmasi), export GPX/TCX via SAF `CreateDocument`.

- [ ] Tulis, review, commit `feat(app): home feed, fitness, challenges, calendar, activity detail`.

### Task 9: Maps

`MapsScreen`: MapLibre penuh, style dibangun di kode (`Style.Builder().fromJson`) untuk STANDARD (`https://tiles.openfreemap.org/styles/liberty` lewat `fromUri`), SATELLITE (raster ESRI `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`), HYBRID (ESRI + raster label `https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}` + `.../Reference/World_Transportation/...`). Hillshade: `RasterDemSource` Terrarium `https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png` encoding terrarium + `HillshadeLayer`. Slope/Avalanche/Aspect: `TerrainOverlayRenderer` unduh tile Terrarium viewport (zoom 12, maks 3×3 tile), hitung `DemMath`, buat Bitmap, pasang `ImageSource` + `RasterLayer` opacity 0.55. Atribusi sumber peta tampil. Import file (SAF `OpenDocument`) ke rute atau aktivitas (dialog pilih). Daftar rute tersimpan, pilih untuk tampil, "Jadikan rute navigasi", export GPX. Panel AI Route (Task 10 menyediakan client).

- [ ] Tulis, review, commit `feat(app): multi-layer maps, terrain overlays, import/export`.

### Task 10: BYOK LLM, AI Coach, AI Route

```kotlin
enum class LlmProviderId(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
  OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"), GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash"),
  GROQ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"), ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
  OLLAMA("Ollama / 9Router", "http://192.168.1.10:11434/v1", "llama3.1") }
data class LlmMessage(val role: String, val content: String)
sealed class LlmResult { data class Ok(val text: String) : LlmResult(); data class Failure(val reason: String) : LlmResult() }
class LlmClient { suspend fun complete(system: String, messages: List<LlmMessage>): LlmResult; suspend fun test(): LlmResult }
class RoutingClient { suspend fun geocode(q: String): LatLon?; suspend fun route(points: List<LatLon>, profile: String): List<TrackPoint> }
```
Kode error dipetakan: 401/403 "API key ditolak", 429 "Kuota habis atau rate limit", timeout "Server tidak merespons", tanpa key "Belum ada API key". Ollama: key opsional. Coach system prompt memuat profil atlet, CTL/ATL/TSB hari ini, 5 aktivitas terakhir. AI Route: prompt `RouteIntents.SYSTEM_PROMPT`, geocode tiap tempat (Nominatim `format=json&limit=1&countrycodes=id`, header `User-Agent: Strideway/1.0 (id.web.agentvaedah.strideway)`, jeda 1 s antar request), loop bila `loop` (kembali ke start), BRouter `https://brouter.de/brouter?lonlats=lon,lat|...&profile=P&alternativeidx=0&format=geojson`.

- [ ] Tulis, review, commit `feat(app): BYOK AI coach and AI route generator`.

### Task 11: You (profil, zona, privacy, backup) + sync stub + README

Profil form dengan validasi rentang (TB 100–230, BB 30–200, usia 10–90, HRmax 120–230, HRrest 30–100 dan < HRmax, FTP 50–600), tabel zona HR & power, Device Manager link, Privacy Zone (peta tap untuk pusat, slider 200–1000 m), Backup ke JSON (aktivitas + titik + rute + profil; tanpa API key) dan Restore (konfirmasi, mengganti data). `RemoteSyncApi` interface + `DisabledRemoteSync`. README: build, izin, catatan placeholder ikon, keterbatasan.

- [ ] Tulis, review, commit `feat(app): profile, zones, privacy zone, backup, docs`.

### Task 12: Verifikasi akhir

- [ ] Review silang seluruh `:app` vs interface `:core` (nama, tipe, import).
- [ ] Grep em dash, emoji, `TODO` tanpa label, `FusedLocation`.
- [ ] Delivery Gate antislop + laporan.
