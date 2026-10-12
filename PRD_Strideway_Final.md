# Product Requirement Document (PRD) — Final Edition
# Project: Strideway (Strideway Athlete OS)
**Document Version:** 3.1.0 (Production Architecture)  
**Brand Identity:** **Strideway** — The Smart Endurance Companion (Ride & Run)  
**Target Environment:** 
- Mobile App: Android Native (Kotlin, Jetpack Compose, Material 3 Dark)
- Project Package Name: `id.web.agentvaedah.strideway`
- Architecture: MVVM + Clean Architecture + Offline-First (Room DB + Ktor Client)
- Backend: Multi-Tenant REST/WebSocket (FastAPI, PostgreSQL 16 + PostGIS, Docker CT106)
- API Proxy & Gateway: Cloudflare Tunnel (`track.agentvaedah.web.id`)
- Target Hardware: Nokia 5.3 (Dedicated Top-Tube Head Unit) & Universal Android Smartphones

---

## 1. Executive Summary & Visi Produk
Strideway (Strideway Athlete OS) adalah platform telemetri olahraga dan navigasi endurance (Cycling & Running) tingkat enterprise. Dirancang untuk menggabungkan ergonomi visual aplikasi Strava Premium, ketelitian metrik fisiologis laboratorium olahraga, kartografi medan militer, generator rute bertenaga AI, serta sistem AI Coach interaktif mandiri (*Bring Your Own Key* — BYOK) sehingga dapat digunakan secara aman oleh ribuan pengguna publik tanpa membebankan biaya API ke pemilik platform.

---

## 2. Struktur Navigasi Aplikasi (5-Tab Core Experience)

Sistem navigasi menggunakan pola 5-Tab Bottom Navigation Bar dengan tombol aksi tengah yang menonjol:

1. **Tab 1: Home (Aktivitas & Gamifikasi)**
   - Activity Feed linimasa linier (Peta thumbnail GPS, Jarak, Waktu, Elevasi, Kalori).
   - Monthly Challenges Tracker (Ride 500 km, Gran Fondo 100 km, Run 100 km) dengan status progres bar dan pencapaian lencana (*Achievements*).
   - Training Log & Kalender interaktif bulanan dengan total jam latihan mingguan.
   - Skor Kebugaran *Fitness, Fatigue & Form* (Model Impuls TRIMP).

2. **Tab 2: Maps (Peta, Rute & AI Automation)**
   - Viewport peta layar penuh dengan kontrol multi-layer:
     - *Base Layer:* Standard Vector (OSM), Satellite High-Res (ESRI), Hybrid.
     - *Terrain Analytics:* Gradient/Slope Shading, Avalanche Hazard Gradient (>30°), Aspect Direction (Paparan matahari & angin).
   - Modul Import/Export: Parser file `.gpx`, `.tcx`, `.fit` (Panitia Event / Audax Indonesia).
   - **AI Generative Route Engine:** Atlet menginput teks bahasa alami (misal: *"Rute road bike 50 km dari Kalibata, aspal mulus, lewati Monas & Sudirman, elevasi landai"*), sistem menghasilkan file jalur navigasi GPX instan.

3. **Tab 3: Record (Live Head Unit Dashboard — Pusat Kendali)**
   - **Bagian Atas (50% Layar):** Peta interaktif vector realtime, garis rute oranye Strava, indikator posisi atlet, kompas heading, dan mini elevation profile.
   - **Bagian Bawah (50% Layar):** Swipeable Drawer Card berlatar kontras tinggi.
     - *Tampilan Utama:* Grid 4 Metrik Inti (Ride Time, Avg Speed km/h, Heart Rate BPM + Zona Warna, Distance km).
     - *Tampilan Swipe Up (Expanded):* Cadence RPM, Current Elevation, Elev Gain (m), Slope/Grade %, Kalori (Formula Keytel 2005), Watt Estimasi, Status Sensor BLE.
   - Kontrol fisik: Tombol Volume Atas (Pause/Resume), Tombol Volume Bawah (Switch Tab Peta/Metrik).

4. **Tab 4: AI Coach (Pelatih Interaktif Bersepeda & Lari)**
   - Konsultasi interaktif chat seputar biomekanika tubuh, analisis kram, bike fitting, pacing lari, strategi brifter, dan nutrisi hidrasi.
   - Arsitektur **BYOK (Bring Your Own Key)**: Pengguna memasukkan API Key mereka sendiri (OpenAI, Gemini, Groq, Anthropic, Ollama/9Router). Kuota mandiri, data privat, bebas biaya server pusat.

5. **Tab 5: You (Profil, Analitik & Perangkat)**
   - Parameter Atlet: TB, BB, Usia, HR Max, Resting HR, FTP Power.
   - Heart Rate & Power Zones Management (5 HR Zones & 7 Coggan Power Zones).
   - **Universal Device Manager:** Hub koneksi Bluetooth BLE untuk Huawei Band 11 Pro, Smartwatch WearOS/Garmin, sensor detak jantung dada, sensor cadence, dan speed hub.
   - Backup, Restore, dan Privacy Zone (penyamaran radius 500m area tempat tinggal).

---

## 3. Spesifikasi Teknis Mesin Telemetri (Low-Latency Engine)

### 3.1. Akurasi Lokasi & Dead Reckoning
- Bypass Google FusedLocationProvider untuk mencegah latensi pengumpulan data (*batching lag*).
- Menggunakan `LocationManager.GPS_PROVIDER` murni dengan interval polling 500 ms dan displacement 0 meter.
- Sensor Fusion Dead Reckoning: Akselerometer IMU internal (50 Hz) mengisi jeda tracking saat melewati terowongan atau kolong flyover.
- Algoritma Auto-Pause Cerdas: Kecepatan < 2.5 km/jam selama > 3 detik menghentikan pencatatan waktu bergerak (*moving time*).

### 3.2. Formula Kalori Fisiologis Medis (Keytel et al. 2005)
Kalkulasi kalori dinamis berdasarkan gender, bobot badan aktual, dan detak jantung real-time:
$$\text{Kalori/menit} = \frac{-55.0969 + (0.6309 \times \text{HR}) + (0.1988 \times \text{BB}) + (0.2017 \times \text{Umur})}{4.184}$$

---

## 4. Keamanan Multi-Tenant & Arsitektur Cloud

- **Database:** PostgreSQL 16 dengan ekstensi PostGIS untuk query geospasial linier.
- **Isolasi Penyewa:** Row-Level Security (RLS) berdasarkan `user_id` pada setiap tabel aktivitas.
- **Enkripsi Klien:** API Key pengguna untuk AI Coach dienkripsi menggunakan Android Keystore `MasterKeys.AES256_GCM_SPEC` di local hardware token.
- **Transmisi Data:** TLS 1.3 via Cloudflare Tunnel terhubung ke container Docker CT106 Proxmox.

---

## 5. Matriks Roadmap Implementasi

| Fase | Durasi | Target Utama |
|---|---|---|
| **Fase 1: Framework & UI** | Minggu 1 | Implementasi 5-Tab Bottom Navigation, Jetpack Compose UI, dan Room DB lokal. |
| **Fase 2: Sensor & Live Record** | Minggu 2 | GPS Provider 500ms, BLE GATT Hub (Huawei & XOSS), Swipeable Drawer Dashboard. |
| **Fase 3: BYOK AI Coach & Routes** | Minggu 3 | Modul LLM Ktor Client (OpenAI/Gemini/Groq API Key storage), Peta Multi-Layer & AI Route Generator. |
| **Fase 4: Multi-Tenant & Deploy** | Minggu 4 | Integrasi backend PostgreSQL PostGIS, Strava V3 auto-upload, dan deployment container CT106. |
