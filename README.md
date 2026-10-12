# Strideway

Aplikasi Android untuk merekam dan menganalisis ride dan run, dibangun dari `PRD_Strideway_Final.md` (fase 1 sampai 3). Package: `id.web.agentvaedah.strideway`.

Backend multi-tenant (FastAPI, PostGIS, CT106) dan unggah otomatis ke Strava adalah fase 4 dan belum ada di repo ini. Aplikasi bekerja penuh secara offline-first; antarmuka sinkronisasi sudah disiapkan di `data/remote/sync/RemoteSyncApi.kt` dan dalam keadaan nonaktif.

## Status build

Kode ini ditulis di laptop tanpa Android SDK dan tanpa akses keluar jaringan, jadi **belum pernah dikompilasi atau dijalankan**. Logika inti sudah dipisah ke modul `:core` (Kotlin murni) beserta unit test, dan nilai acuan test dihitung terpisah dengan Python. Langkah pertama di mesin yang punya Android Studio adalah build dan menjalankan test di bawah, lalu memperbaiki error kompilasi yang mungkin muncul.

## Build

Kebutuhan: Android Studio Ladybug (2024.2) atau lebih baru, JDK 17, Android SDK 35.

1. Buka folder ini di Android Studio. Saat Gradle sync, Studio membuat `gradle-wrapper.jar` dari `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.11.1). Untuk command line tanpa Studio, jalankan sekali `gradle wrapper --gradle-version 8.11.1`.
2. Unit test modul inti:
   ```
   ./gradlew :core:test
   ```
3. APK debug dan lint:
   ```
   ./gradlew :app:assembleDebug :app:lint
   ```
4. Pasang ke ponsel: `./gradlew :app:installDebug`, atau ambil `app/build/outputs/apk/debug/app-debug.apk`.

## Struktur

```
core/   Kotlin/JVM murni, diuji dengan JUnit 5
  physio/     kalori Keytel 2005, zona HR (Karvonen) dan power Coggan, TRIMP + fitness/fatigue/form, estimasi watt
  geo/        haversine, bearing, gain elevasi dengan histeresis, gradien, polyline, tile math
  telemetry/  auto-pause 2,5 km/j > 3 s, dead reckoning, akumulator sesi
  sensor/     parser GATT HR / CSC / Cycling Power, cadence dan kecepatan roda
  track/      parser dan penulis GPX, TCX; decoder FIT; statistik aktivitas
  terrain/    decode DEM Terrarium, slope/aspect Horn, palet slope, avalanche, aspect, hillshade
  privacy/ challenge/ route/ coach/
app/    Android: Compose Material 3, Hilt, Room, DataStore, Ktor, MapLibre
  data/       Room, DataStore, Keystore, BLE hub, GPS, sensor gerak, file I/O, klien LLM dan routing
  service/    RecordingController (logika sesi) dan RecordingService (foreground)
  ui/         home, maps, record, coach, you, activity
```

## Fitur per tab

- **Home**: form/fitness/fatigue 42 hari, tantangan bulanan (Ride 500 km, Gran Fondo 100 km, Run 100 km), kalender latihan dan jam per minggu, feed aktivitas dengan sketsa jalur offline. Detail aktivitas: peta, grafik elevasi/HR/kecepatan, ganti nama, hapus, ekspor GPX/TCX.
- **Peta**: MapLibre dengan peta Standar (OpenFreeMap), Satelit dan Hybrid (Esri). Lapisan relief, kemiringan, lereng >30°, dan arah lereng dihitung di ponsel dari DEM AWS Terrarium. Impor `.gpx`, `.tcx`, `.fit`; rute tersimpan; AI Route (LLM milik pengguna untuk membaca permintaan, Nominatim untuk mencari tempat, BRouter untuk jalur di jalan).
- **Rekam**: `LocationManager.GPS_PROVIDER` 500 ms / 0 m, IMU 50 Hz untuk dead reckoning saat GPS hilang, barometer bila ada. Separuh atas peta dengan garis oranye, kompas, dan profil elevasi; separuh bawah empat metrik utama, geser ke atas untuk cadence, elevasi, gain, gradien, kalori, watt, dan status sensor. Volume Atas jeda/lanjut, Volume Bawah ganti tampilan. Titik ditulis ke Room tiap 5 detik; sesi yang terputus karena aplikasi mati dipulihkan saat dibuka lagi.
- **AI Coach**: chat dengan OpenAI, Gemini, Groq, Anthropic, atau Ollama/9Router memakai API key milik pengguna. Key dienkripsi AES-256-GCM dengan kunci Android Keystore dan tidak ikut backup.
- **You**: profil atlet, tabel zona, perangkat BLE, Privacy Zone, backup/restore JSON.

## Izin yang diminta

| Izin | Untuk |
|---|---|
| Lokasi akurat | merekam jalur |
| Notifikasi (Android 13+) | notifikasi tetap selama merekam |
| Perangkat sekitar / Bluetooth | sensor HR, cadence, power |
| Foreground service lokasi dan perangkat terhubung | merekam saat layar mati |

## Catatan dan batasan

- **Ikon aplikasi masih placeholder**: huruf S oranye di latar grafit (`res/drawable/ic_launcher_foreground.xml`). Ganti dengan logo resmi bila sudah ada.
- **Huawei Band 11 Pro dan jam Garmin** hanya terbaca bila mode siaran detak jantung (HR broadcast) dinyalakan di jamnya. Aplikasi memakai profil Bluetooth standar, bukan SDK pabrikan.
- **TRIMP tanpa sensor HR** diperkirakan dari durasi pada 60% cadangan detak jantung. Aktivitas seperti ini ditandai "estimasi" di detail.
- **Watt** adalah estimasi fisika (gravitasi, gulir, aerodinamika) kecuali ada power meter.
- **Layanan publik**: OpenFreeMap, Esri, AWS Terrarium, Nominatim (maksimal 1 permintaan per detik, sudah diatur), dan brouter.de dipakai tanpa key. Untuk pemakaian ramai, pertimbangkan server sendiri.
- **Ollama/9Router** di jaringan lokal memakai `http://`, sehingga `usesCleartextTraffic` aktif. Penyedia lain tetap HTTPS.
