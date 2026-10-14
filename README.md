# Strideway

Aplikasi Android untuk merekam dan menganalisis ride dan run, dibangun dari `PRD_Strideway_Final.md` (fase 1 sampai 3). Package: `id.web.agentvaedah.strideway`.

Backend multi-tenant (FastAPI, PostGIS, CT106) dan unggah otomatis ke Strava adalah fase 4 dan belum ada di repo ini. Aplikasi bekerja penuh secara offline-first; antarmuka sinkronisasi sudah disiapkan di `data/remote/sync/RemoteSyncApi.kt` dan dalam keadaan nonaktif.

## Status build

Kode ini ditulis di laptop tanpa Android SDK dan tanpa akses keluar jaringan, jadi **belum pernah dikompilasi atau dijalankan**. Logika inti sudah dipisah ke modul `:core` (Kotlin murni) beserta unit test, dan nilai acuan test dihitung terpisah dengan Python. Langkah pertama di mesin yang punya Android Studio adalah build dan menjalankan test di bawah, lalu memperbaiki error kompilasi yang mungkin muncul.

## Dapatkan APK tanpa Android Studio (GitHub Actions)

Repo ini membawa workflow `.github/workflows/android.yml` yang menjalankan unit test dan membuat APK debug setiap kali ada push.

1. Ekstrak ZIP project, lalu buat repository kosong di GitHub (jangan centang "Add a README").
2. Dari folder hasil ekstrak:
   ```
   git remote add origin https://github.com/<akun>/strideway.git
   git push -u origin master
   ```
3. Buka tab **Actions** di GitHub, pilih run **Android** terbaru, tunggu sampai hijau (sekitar 10 menit untuk run pertama).
4. Di bagian **Artifacts**, unduh `strideway-debug-apk`. Isinya ZIP berisi `app-debug.apk`.
5. Salin `app-debug.apk` ke ponsel, buka, lalu izinkan "Instal dari sumber tidak dikenal" bila diminta.

Bila run merah, buka langkah yang gagal, salin log errornya, lalu kirim untuk diperbaiki. Untuk menjalankan ulang tanpa push, pakai tombol **Run workflow**.

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
  route/      rute (AI loop, intent), RouteFollower dan Navigator: manuver BRouter atau dari geometri, pengumuman 200 m dan saat belok, tiba
  training/   16 sesi terstruktur per level, WorkoutRunner (target watt, kepatuhan), tes FTP, rencana Base/Build/Peak/Taper
  coach/      konteks AI Coach dan VoiceCues (kalimat voice over)
  stats/      progres 7H-1T per metrik, weekly snapshot, streak, ringkasan zona HR
  records/    best effort (5-40 km ride, 1 km-half marathon run) dan rekor pribadi
  history/    filter riwayat (sport, tanggal, nama) dan grup per bulan
  heatmap/    heatmap pribadi dari titik jalur
  gear/       komponen sepeda, interval servis, status keausan
  fuel/       rencana cairan/karbohidrat dan jadwal pengingat minum/makan
  weather/    kode cuaca WMO dan saran latihan
  privacy/ challenge/ (tantangan opt-in dan penghargaan)
app/    Android: Compose Material 3, Hilt, Room, DataStore, Ktor, MapLibre
  data/       Room, DataStore, Keystore, BLE hub, GPS, sensor gerak, file I/O, klien LLM dan routing
  service/    RecordingController (logika sesi) dan RecordingService (foreground)
  ui/         home, maps, record, training, coach, you, activity, weather
```

## Fitur per tab

- **Home**: form/fitness/fatigue 42 hari; carousel Snapshot minggu ini, Progres, Sepeda (komponen paling aus dan servis berikutnya) (rentang 7H/1B/3B/6B/YTD/1T untuk jarak, elevasi, waktu, jumlah aktivitas; grafik bisa digeser), Kalender, Analisis HR (waktu per zona), Streak; tantangan bulanan yang diikuti secara sukarela dengan penghargaan saat tercapai; feed aktivitas. Detail aktivitas: peta, grafik elevasi/HR/kecepatan, ganti nama, hapus, ekspor GPX/TCX.
- **Peta**: pilih sport di kiri atas, cari lokasi (Nominatim) lalu simpan atau buat rute ke sana; Rencana rute meminta hingga 3 alternatif BRouter yang bisa diurutkan menurut jarak, tanjakan, atau porsi aspal; gambar rute sendiri dengan ketuk titik (mengikuti jalan, bisa undo); tombol lapisan (Standar, Satelit, Hybrid, Topo; heatmap pribadi; tempat menarik; jalur sepeda CyclOSM; medan), 3D/2D, dan lokasi sekarang. MapLibre dengan Standar (OpenFreeMap), OSM, Satelit dan Hybrid (Esri), Topo (OpenTopoMap), serta Mapbox Streets/Outdoors/Satelit memakai token Mapbox milik pengguna. Lencana cuaca dan kompas di peta. Lapisan relief, kemiringan, lereng >30°, dan arah lereng dari DEM AWS Terrarium. Impor `.gpx`, `.tcx`, `.fit`; rute tersimpan; AI Route. Setelah alternatif rute dipilih, panel rencana mengecil jadi strip sekitar 10% layar (ganti alternatif dengan panah, ketuk untuk membesar) sehingga peta tetap bisa digeser. Garis rekaman memakai gradien Signal ke kuning dengan glow dan penanda start/finish; rute rencana garis putus dengan panah arah.
- **Cuaca**: Open-Meteo tanpa API key: kondisi sekarang, angin, UV, tekanan, 24 jam, 7 hari, dan saran latihan.
- **Rekam**: layar standby seperti Strava (peta, ikon sport, tombol Mulai, Tambah rute). Saat merekam ada tampilan maksimal (timer 00:00:00, kecepatan rata-rata, jarak, naik, elevasi sekarang, HR) dan minimal (peta 75%, baris status HR/jeda/perbesar, waktu, rata-rata, jarak, kecepatan maks, cadence, gradien); panah posisi dan peta mengikuti arah gerak, kompas menunjuk utara sejati; tombol 3D, lokasi sekarang, preview rute, ganti rute, peringatan rute. Saat Mulai terdengar "Sudah siap gowes? Gas, hati-hati di jalan!", saat Stop "Rekaman berhenti. Selamat istirahat." Navigasi belokan demi belokan: banner atas berisi panah manuver, jarak, sisa km dan perkiraan tiba; suara "Dalam 200 meter, belok kanan" lalu "Belok kanan sekarang". Keluar rute >50 m selama 5 detik memicu getar dan suara; setelah 10 detik rute dihitung ulang dari posisi sekarang ke tujuan. Bila sesi latihan dipilih, strip latihan menunjukkan target watt, kepatuhan (Naikkan/Pas/Turunkan), dan sisa waktu interval, dengan suara di tiap pergantian interval. Voice over (TTS bawaan, OpenAI, atau ElevenLabs) untuk split per km, jeda/lanjut, zona HR, rute, dan pengingat minum/makan. Volume Atas jeda/lanjut, Volume Bawah ganti tampilan.
- **Latihan**: tab Rencana (wizard tujuan, tanggal event, hari latihan, level Pemula sampai Elite; rencana dibuat AI bila ada API key, kalau tidak oleh aturan periodisasi) dan Sesi (16 sesi dari recovery sampai race simulation, durasi menyesuaikan level). "Kayuh sesi ini" memasang sesi ke layar Rekam. Setelah tes FTP 20 menit atau ramp selesai, aplikasi menawarkan FTP baru dan memperbarui zona. Tombol chat membuka AI Coach (OpenAI, Gemini, Groq, Anthropic, atau Ollama/9Router dengan API key milik pengguna, terenkripsi Android Keystore).
- **Aktivitas**: rekor pribadi ala Strava (lencana PR di kartu dan detail, pengguna baru langsung dapat PR). Detail punya menu kanan atas: tambah foto, edit (judul, sport, deskripsi), potong, visibilitas peta, simpan sebagai rute, hitung ulang, hapus. "Bagikan dengan foto" (atau ketuk foto aktivitas) membuat gambar Feed 4:5 atau Story 9:16 dengan 4 template (Statistik bawah, Rute + statistik, Tanpa foto PNG transparan, Minimal) berisi Distance, Elev Gain, Time, Avg Power (bila ada), dan Speed; bagikan ke aplikasi lain atau simpan ke `Pictures/Strideway`. Ketuk peta untuk animasi rute; geser timeline untuk melihat kecepatan, HR, dan elevasi di setiap saat.
- **You**: profil (nama, jenis sepeda), tab Progress (grafik, streak, total, rekor pribadi, penghargaan) dan Aktivitas (cari, filter sport dan tanggal, grup per bulan dengan total). Garasi sepeda: pilih dari 15 komponen (shifter sampai tuas rem), km dihitung dari ride setelah tanggal pasang, tandai servis atau ganti dengan riwayat. Ikon gear membuka Pengaturan: profil dan zona, Privacy Zone, tema Terang/Gelap/Sistem, peta dan token Mapbox, suara, minum & energi (dengan saran AI), perangkat dan sensor ponsel, API key, backup/restore.

## Izin yang diminta

| Izin | Untuk |
|---|---|
| Lokasi akurat | merekam jalur |
| Notifikasi (Android 13+) | notifikasi tetap selama merekam |
| Perangkat sekitar / Bluetooth | sensor HR, cadence, power, suhu/tekanan |
| Getar | peringatan keluar rute |
| Foreground service lokasi dan perangkat terhubung | merekam saat layar mati |

## Catatan dan batasan

- **Database versi 3**: migrasi 1 ke 2 menambah kolom deskripsi dan visibilitas aktivitas serta tabel best effort, foto, lokasi tersimpan, dan komponen sepeda; migrasi 2 ke 3 menambah tabel manuver rute, rencana latihan, dan sesi terjadwal. Buat backup sebelum memasang versi baru di atas versi lama. Foto aktivitas, rencana latihan, dan gambar bagikan tidak ikut backup JSON.
- **Nama jalan** belum muncul di navigasi karena respons brouter.de tidak menyertakan tag `name`. Rute impor atau gambar tangan memakai belokan yang dideteksi dari geometri.
- **Bagikan gambar** memakai FileProvider (`${applicationId}.share`) dari cache aplikasi; simpan ke galeri lewat MediaStore tanpa izin penyimpanan di Android 10+.
- **Bento**: grup statistik (Home, Progress, detail aktivitas, Cuaca, Garasi) memakai grid 2 kolom dengan satu tile utama yang membentang.
- **Heatmap Global, Mingguan, dan Malam** butuh data banyak pengguna dari server (fase 4), jadi baru Pribadi yang aktif.
- **Merek**: warna Signal `#FF4F00` dan Ink `#0A0A0B` serta logo dari `assets/branding/` (aturan di `BRAND.md`). Judul dan angka besar memakai Strideway Sans (`tools/font/build_font.py`); teks lain Outfit (SIL OFL 1.1, lisensi di `assets/licenses/Outfit-OFL.txt`). Di mode terang, teks oranye memakai Signal yang lebih gelap `#C23B00` karena Signal di atas putih hanya 3,3:1.
- **Splash 5 detik** (`ui/splash/`, waktu di `core/splash/SplashTimeline.kt`) tampil sekali, pada pembukaan pertama setelah install; ketuk layar untuk langsung ke Home. Splash dilewati saat sesi rekam aktif, setelah rotasi, dan bila animasi sistem dimatikan. Kontur dan rutenya berasal dari `tools/motion-splash/export-scene.mjs`.
- **Ikon aplikasi** memakai ikon ban dan S dari `assets/branding/strideway-icon-color-dark.svg` di atas latar Ink.
- **Jam tangan dan smartband** hanya terbaca bila siaran detak jantung (HR broadcast) dinyalakan di perangkatnya. Aplikasi memakai profil Bluetooth standar, bukan SDK pabrikan.
- **TRIMP tanpa sensor HR** diperkirakan dari durasi pada 60% cadangan detak jantung. Aktivitas seperti ini ditandai "estimasi" di detail.
- **Watt** adalah estimasi fisika (gravitasi, gulir, aerodinamika) kecuali ada power meter.
- **Layanan publik**: OpenFreeMap, Esri, AWS Terrarium, Nominatim (maksimal 1 permintaan per detik, sudah diatur), dan brouter.de dipakai tanpa key. Untuk pemakaian ramai, pertimbangkan server sendiri.
- **Ollama/9Router** di jaringan lokal memakai `http://`, sehingga `usesCleartextTraffic` aktif. Penyedia lain tetap HTTPS.
