# Strideway Android — Design + Implementation Plan

## Context
`E:\RideWay` hanya berisi `PRD_Strideway_Final.md` (v3.1.0). Goal: bangun aplikasi Android Strideway sesuai PRD.
Keputusan user:
- Scope: **Android saja** (PRD Fase 1–3). Backend FastAPI/PostGIS/CT106 + Strava = Fase 4, di luar scope; hanya disiapkan kolom `syncState` + interface `RemoteSyncApi` (Ktor, base URL `https://track.agentvaedah.web.id`), default nonaktif.
- Peta: **MapLibre Native**. Routing AI: **BRouter public + Nominatim**.
- Laptop kantor: **tidak boleh install Android SDK, tidak boleh upload ke luar** (GitHub/CI ditolak). Plugin Antigravity `diemasmichiels.emulate` hanya launcher dan tetap butuh SDK + AVD, jadi tidak bisa dipakai.
  Konsekuensi: kode **tidak bisa dikompilasi di laptop ini**. Mitigasi: versi library yang sudah terbukti cocok, logika inti dipisah ke modul Kotlin murni dengan unit test, dan checklist build untuk dijalankan di mesin yang punya Android Studio.
- Toolchain yang ada: Java 8 JRE, git, Python 3.10. Tidak ada Gradle, Docker, gh, atau WSL.

## Architecture
Proyek Gradle (Kotlin DSL + version catalog), package `id.web.agentvaedah.strideway`.
- `:core` — **Kotlin/JVM murni** (tanpa Android): formula, parser, algoritma. Semua unit test JUnit5 ada di sini.
- `:app` — Android (Compose M3 dark, Hilt, Room, DataStore, Ktor, MapLibre, BLE, Foreground Service).
- Struktur `:app`: `data/` (room, prefs, ble, location, remote/llm, remote/routing, files), `domain/` (repository interfaces + use cases), `ui/` (home, maps, record, coach, you, components, theme), `service/` (RecordingService).
- MVVM: ViewModel + `StateFlow<UiState>`; repository offline-first (Room jadi sumber kebenaran).

Versi (pinned, kombinasi stabil): Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29, Compose BOM 2024.12.01, Room 2.6.1, Hilt 2.54 (+ hilt-navigation-compose 1.2.0), Navigation 2.8.5, Ktor 3.0.3 (OkHttp engine + content-negotiation + kotlinx-json), kotlinx-serialization 1.7.3, DataStore 1.1.1, MapLibre `org.maplibre.gl:android-sdk:11.8.0`, Lifecycle 2.8.7, WorkManager 2.10.0, JUnit 5.11.4. compileSdk/targetSdk 35, minSdk 26 (Nokia 5.3 = Android 10–12).

## `:core` modules (pure Kotlin, unit-tested)
| File | Isi |
|---|---|
| `physio/Calories.kt` | Keytel 2005 pria (formula PRD) + wanita (−20.4022 + 0.4472HR − 0.1263BB + 0.074U)/4.184; clamp ≥0 |
| `physio/Zones.kt` | 5 HR zone (%HRmax atau Karvonen via resting HR), 7 Coggan power zone dari FTP |
| `physio/TrainingLoad.kt` | Banister TRIMP per aktivitas; CTL (42 hari), ATL (7 hari) EWMA; TSB = CTL−ATL |
| `physio/PowerEstimator.kt` | Watt = gravitasi + rolling (Crr 0.005) + aero (CdA 0.32, ρ 1.225), massa atlet + sepeda |
| `geo/Geo.kt` | haversine, bearing, grade % (window jarak 50 m), elevation gain dengan hysteresis 3 m |
| `telemetry/AutoPause.kt` | state machine: speed < 2.5 km/j selama > 3 s ⇒ paused; resume saat ≥ 2.5 |
| `telemetry/DeadReckoning.kt` | saat fix GPS > 1.5 s basi: propagasi posisi dari speed terakhir + bearing + integrasi akselerasi maju (50 Hz), titik ditandai `interpolated` |
| `telemetry/SessionAccumulator.kt` | gabung sampel GPS/HR/cadence/power ⇒ metrik live (elapsed, moving, distance, avg/max speed, gain, kalori) |
| `track/MiniXml.kt`, `track/Gpx.kt`, `track/Tcx.kt` | parse + write. StAX tidak ada di Android dan XmlPullParser tidak ada di JVM murni, jadi pakai tokenizer XML minimal sendiri (elemen, atribut, teks, entity dasar) |
| `track/FitDecoder.kt` | decoder FIT biner (header, definition/data message, compressed timestamp) untuk message `record` (lat/lon semicircle, altitude, timestamp, HR, cadence, power, speed). FIT = **import saja** |
| `terrain/DemMath.kt` | decode Terrarium RGB ⇒ elevasi; slope (Horn), aspect 8 arah; palet slope, avalanche (>30° merah), aspect |
| `privacy/PrivacyZone.kt` | buang titik dalam radius (default 500 m) dari pusat yang diset user |
| `challenge/Challenges.kt` | Monthly: Ride 500 km, Gran Fondo (1 ride ≥ 100 km), Run 100 km; lencana |
| `route/RouteIntent.kt` | model JSON hasil LLM (start, waypoints, sport, surface, elevationPref, targetKm, loop) + validasi |

## `:app` features
**Data**
- Room: `ActivityEntity` (sport, start, durasi, moving, jarak, gain, kalori, avgHR, trimp, polyline encoded, syncState), `TrackPointEntity` (lat, lon, ele, t, hr, cad, power, speed, interpolated), `RouteEntity` + `RoutePointEntity` (sumber: import/AI), `ChatMessageEntity`, `SensorDeviceEntity` (mac, tipe, nama, autoConnect).
- DataStore: `AthleteProfile` (gender, TB, BB, usia, HRmax, HRrest, FTP, berat sepeda), privacy zone, preferensi layer peta, provider LLM aktif + model + baseUrl.
- `SecureKeyStore`: Android Keystore AES-256-GCM (`KeyGenParameterSpec` setara `MasterKeys.AES256_GCM_SPEC`; `security-crypto` sudah deprecated jadi dibuat wrapper sendiri). Ciphertext + IV disimpan di DataStore, satu entri per provider.

**Tab 1 Home** — feed aktivitas (thumbnail jalur digambar Canvas dari polyline, sudah difilter privacy zone, tanpa network), kartu Fitness/Fatigue/Form + grafik 42 hari, Monthly Challenges + progress bar + lencana, kalender bulanan (titik hari latihan) + total jam per minggu. Detail aktivitas: peta MapLibre, grafik elevasi/HR/speed, split, export GPX/TCX.

**Tab 2 Maps** — MapLibre layar penuh. Base layer: Standard (OpenFreeMap `liberty` vector, tanpa key), Satellite (ESRI World Imagery raster), Hybrid (ESRI + label/jalan OpenFreeMap). Terrain overlay: hillshade (raster-dem Terrarium AWS), Slope, Avalanche >30°, Aspect — dihitung `DemMath` dari tile Terrarium untuk viewport saat kamera idle (zoom 11–15), ditampilkan sebagai `ImageSource`. Import `.gpx/.tcx/.fit` via SAF; export rute GPX. Panel **AI Route**: teks bebas ⇒ LLM (BYOK) ⇒ `RouteIntent` JSON ⇒ Nominatim geocode (bias `countrycodes=id`) ⇒ BRouter (`brouter.de/brouter`, profil `fastbike` road / `trekking` / `hiking-beta` run, `format=geojson`) ⇒ preview jarak + gain ⇒ simpan rute / jadikan rute navigasi Record.

**Tab 3 Record** (tombol tengah menonjol) — `RecordingService` foreground (`location|connectedDevice`), `LocationManager.GPS_PROVIDER` 500 ms / 0 m (tanpa FusedLocationProvider), akselerometer + rotation vector 50 Hz untuk dead reckoning, barometer jika ada untuk elevasi. State dibagikan via `RecordingSessionRepository` (StateFlow), titik ditulis ke Room tiap 5 s (aman bila app mati).
UI: atas 50% peta (garis rute oranye `#FC4C02`, puck posisi, kompas heading, mini elevation profile, rute navigasi jika dipilih). Bawah 50% drawer kontras tinggi: grid 4 metrik (Ride Time, Avg Speed, HR + warna zona, Distance); swipe up ⇒ Cadence, Elevasi, Gain, Grade %, Kalori, Watt estimasi, status sensor BLE. Volume Atas = Pause/Resume, Volume Bawah = toggle peta/metrik full (ditangkap di `MainActivity.dispatchKeyEvent` hanya saat tab Record aktif). Layar tetap menyala. Pilih Ride/Run sebelum start; Stop ⇒ simpan + ringkasan.

**Tab 4 AI Coach** — chat (riwayat Room). Provider: OpenAI, Groq, Ollama/9Router (semua OpenAI-compatible `/v1/chat/completions`, base URL bisa diubah), Gemini (`generateContent`), Anthropic (`/v1/messages`, default model `claude-sonnet-5-5`). System prompt berisi profil atlet + ringkasan CTL/ATL/TSB + 5 aktivitas terakhir. Layar setting key: masukkan / tes koneksi / hapus. Error (401, 429, timeout, tanpa key) ditampilkan jelas di chat.

**Tab 5 You** — form parameter atlet, tabel HR zone & power zone (live dari `:core`), **Device Manager** BLE: scan (filter service HRS 0x180D, CSC 0x1816, Cycling Power 0x1818), connect/forget, auto-reconnect, baterai 0x180F. Catatan: Huawei Band / Garmin harus mengaktifkan "HR broadcast" agar terbaca sebagai HRS standar. Backup/Restore: seluruh DB + profil ke file JSON via SAF. Privacy Zone: pilih titik di peta + radius (default 500 m).

**Permissions** — fine location, background location (opsional), `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `BLUETOOTH_SCAN/CONNECT` (31+) / legacy BT + location (≤30), `POST_NOTIFICATIONS`, `HIGH_SAMPLING_RATE_SENSORS`, INTERNET. Alur rationale di Compose.

**Error handling** — Result sealed type di repository; GPS hilang ⇒ indikator "DR" + titik interpolasi; BLE putus ⇒ status merah + reconnect backoff; network routing/LLM gagal ⇒ snackbar dengan alasan.

## Execution
1. `git init` lokal (tanpa remote, tanpa push). Simpan design ini ke `docs/superpowers/specs/2026-10-12-strideway-android-design.md` dan task plan detail ke `docs/superpowers/plans/2026-10-12-strideway-android.md`, commit.
2. Eksekusi via `superpowers:subagent-driven-development`, urutan task:
   1. Skeleton Gradle (settings, version catalog, `:core`, `:app`, manifest, theme M3 dark, 5-tab nav + tombol Record menonjol, Hilt app). `gradlew`/`gradlew.bat` + `gradle-wrapper.properties` (jar wrapper tidak bisa dibuat offline ⇒ Android Studio meng-generate saat sync).
   2. `:core` physio + geo + telemetry (TDD: test dulu).
   3. `:core` track parsers GPX/TCX/FIT + terrain + privacy + challenges (TDD).
   4. Room + DataStore + SecureKeyStore + repositories.
   5. RecordingService (GPS 500 ms, sensor 50 Hz, DR, auto-pause) + Record UI (peta 50% / drawer, tombol volume).
   6. BLE hub + Device Manager UI.
   7. Home (feed, thumbnail, challenges, kalender, fitness/fatigue/form) + detail aktivitas.
   8. Maps (layer, terrain overlay, import/export).
   9. LLM client BYOK + AI Coach + AI Route (Nominatim + BRouter).
   10. You (profil, zona, backup/restore, privacy zone) + README build/run.
3. Per task: antislop (UI/copy/code) untuk kualitas, review subagent, commit lokal.

## Verification
Laptop ini tidak bisa build (tanpa SDK, keputusan user). Yang bisa dicek di sini:
- Review silang setiap file: import, signature API (MapLibre 11, Ktor 3, Room 2.6), konsistensi package/Hilt graph.
- Validasi angka formula `:core` lewat Python (Keytel, TRIMP/CTL, haversine, slope Horn, decode FIT sampel buatan) supaya expected value di unit test benar.
- `git status` bersih, struktur file lengkap sesuai daftar task.

Di mesin dengan Android Studio (didokumentasikan di `README.md`):
1. Open project ⇒ Gradle sync (wrapper jar ter-generate).
2. `./gradlew :core:test` — semua unit test hijau.
3. `./gradlew :app:assembleDebug lint`.
4. Install ke Nokia 5.3: rekam ride luar ruang ⇒ cek GPS 2 Hz, auto-pause, HR band via BLE, tombol volume, simpan ⇒ muncul di Home; import GPX ⇒ tampil di Maps; AI Coach dengan key sendiri; AI Route ⇒ GPX tersimpan.
