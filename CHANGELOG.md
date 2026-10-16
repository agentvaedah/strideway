# Riwayat Update Strideway

Setiap versi di bawah sesuai dengan file ZIP yang dikirim. Kolom "Commit" adalah commit terakhir yang masuk ke ZIP tersebut; lihat isinya dengan `git show <commit>` atau `git log <commit-awal>..<commit>`.

| Versi | Tanggal | Commit | File |
|---|---|---|---|
| 0.5.0 | 16-10-2026 | tag `v0.5` | `Strideway-v0.5-part1.zip` + `-part2.zip` |
| 0.4 | 14-10-2026 | `3d8a6d8` | `Strideway-v0.4.zip` (atau `-part1` + `-part2`) |
| 0.3.1 | 13-10-2026 | `5c71860` | `Strideway-v0.3.1.zip` |
| 0.3 | 13-10-2026 | `a1e7c1d` | `Strideway-v0.3.zip` |
| 0.2 | 13-10-2026 | `4b7d669` | `Strideway-v0.2.zip` |
| 0.1 | 12-10-2026 | `589952b` | `Strideway.zip` |

Mulai 0.5.0, `versionName` di `app/build.gradle.kts` sama dengan nomor versi di sini dan tampil di bawah layar Pengaturan. Versi sebelumnya masih tertulis `0.1.0` di aplikasi.

---

## 0.5.0 (16-10-2026)

Tidak ada perubahan database; tidak perlu migrasi.

### Baru
- **Bagikan dengan foto** dirombak:
  - 10 template: Statistik bawah, Rute + statistik, Tanpa foto, Minimal, Peta penuh, Foto + peta, Angka besar, Elevasi, Kartu, Rekor.
  - Template berpeta kini menampilkan **peta asli** di bawah garis rute, dengan pilihan latar Standar, Satelit, atau Topo.
  - Pilihan template berupa deretan thumbnail.
- **Peta asli di kartu aktivitas** (Home dan You > Aktivitas), tidak lagi hanya garis.
- **Pratinjau rute 3D**: kamera menyusuri rute dengan kemiringan 60°, bisa diputar, dijeda, digeser, dan dipercepat 2× atau 4×. Kartu belokan berikut ikut tampil.
- **Navigasi siap sebelum Mulai**: layar Rekam menampilkan panjang rute, belokan pertama, Daftar belokan, dan Pratinjau 3D. Saat GPS belum terkunci, muncul "Menunggu GPS untuk navigasi".
- **Planner**: tombol Pratinjau 3D dan Navigasi langsung dari strip alternatif.
- **Rekor pribadi ala Strava**:
  - Kartu per jarak dengan medali dan selisih dari rekor sebelumnya.
  - Tiga terbaik per jarak.
  - Jarak yang belum ditempuh tampil sebagai target.
  - Strip Pencapaian di detail aktivitas.
- **Profil**:
  - Unggah foto profil dan bio dengan "Lihat selengkapnya".
  - Total aktivitas dan minggu beruntun.
  - Slot Followers/Following, aktif setelah ada akun online.

### Diubah
- **Home**: judul "Today" dengan tanggal dan foto profil, ringkasan minggu, grafik beban latihan per hari, latihan hari ini dengan tombol Kayuh.
- **Layar Rekam**: kecepatan sekarang jadi angka terbesar, dengan bar posisi (0 / rata-rata / maks), grafik 60 detik terakhir, dan tanda naik/turun. Tampilan Peta penuh punya panel kecepatan dan tombol Lokasi saya. Saat GPS lemah kecepatan tampil "--".
- **Splash** tampil setiap aplikasi dibuka setelah ditutup, bukan hanya sekali.
- **Animasi**: transisi antar layar, kartu masuk bertahap, angka menghitung naik, kartu mengecil saat ditekan. Semua mati bila animasi sistem dimatikan.
- Peta yang gagal dimuat (offline) menampilkan pesan, bukan layar kosong.

### Catatan
- Peta di kartu dan gambar bagikan butuh internet saat pertama dibuat, lalu disimpan di cache.
- Foto profil tidak ikut backup JSON; bio ikut.

---

## 0.4 (14-10-2026)

Fase L sampai R. **Database naik ke versi 3: buat backup sebelum memasang di atas versi lama.**

### Baru
- **Navigasi belokan demi belokan**: banner atas dengan panah manuver, jarak, sisa km, dan perkiraan tiba. Suara dua tahap ("Dalam 200 meter, belok kanan", lalu "Belok kanan sekarang") dan "Anda sudah sampai di tujuan". Manuver dari BRouter; rute impor atau gambar tangan memakai belokan dari geometri.
- **Hitung ulang rute** otomatis bila keluar rute lebih dari 10 detik.
- **Tab Latihan** menggantikan tab Coach:
  - 16 sesi terstruktur (recovery sampai race simulation), durasi menyesuaikan level Pemula, Menengah, Lanjut, Elite.
  - Rencana mingguan dengan fase Base, Build, Peak, Taper; dibuat AI bila ada API key, kalau tidak oleh aturan.
  - Mode latihan saat merekam: target watt, indikator Naikkan/Pas/Turunkan, suara di tiap interval.
  - Tes FTP 20 menit dan ramp, lalu tawaran memperbarui FTP dan zona.
  - Chat AI Coach dibuka dari tombol di tab Latihan.
- **Bagikan dengan foto**: 4 template (Statistik bawah, Rute + statistik, Tanpa foto, Minimal), format Feed 4:5 dan Story 9:16, berisi Distance, Elev Gain, Time, Avg Power, Speed. Bagikan ke aplikasi lain atau simpan ke galeri.
- **Suara saat Mulai** ("Sudah siap gowes? Gas, hati-hati di jalan!") dan **saat Stop** ("Rekaman berhenti. Selamat istirahat.").
- **Bento grid** untuk grup statistik di Home, Progress, detail aktivitas, Cuaca, dan Garasi.

### Diubah
- Warna brand Signal `#FF4F00` dan Ink `#0A0A0B`; teks oranye di mode terang `#C23B00`.
- Judul dan angka besar memakai Strideway Sans; teks lain tetap Outfit.
- Ikon aplikasi memakai logo brand.
- Splash hanya tampil pada pembukaan pertama setelah install.
- Garis peta lebih sporty: jalur rekaman bergradien dengan glow dan penanda start/finish; rute rencana garis putus dengan panah arah.
- Panel rencana rute mengecil jadi strip sekitar 10% layar setelah memilih alternatif, tidak lagi menutup peta.

### Catatan
- Nama jalan belum tampil di navigasi karena brouter.de tidak mengirimkannya.
- Rencana latihan dan gambar bagikan tidak ikut backup JSON.

---

## 0.3.1 (13-10-2026)

### Baru
- Splash 5 detik dengan wordmark Strideway Sans.
- Adegan splash dipakai bersama oleh aplikasi dan video motion; wordmark Strideway Sans di video.

---

## 0.3 (13-10-2026)

Fase G sampai K. **Database naik ke versi 2.**

### Baru
- **Rekam**: layar standby ala Strava, tampilan maksimal dan minimal saat merekam.
- **Aktivitas**: rekor pribadi (PR) ala Strava, riwayat bisa dicari dan dikelompokkan per bulan, menu aktivitas (edit, potong, visibilitas peta, simpan sebagai rute, hitung ulang), foto aktivitas, animasi putar ulang rute.
- **Peta**: pilih sport, cari dan simpan lokasi, perencana rute dengan alternatif BRouter, gambar rute sendiri, lembar lapisan (Standar, Satelit, Hybrid, Topo, heatmap pribadi, CyclOSM, medan).
- **Garasi sepeda**: 15 komponen dengan interval servis dan riwayat.
- **Home**: kartu insight sepeda di carousel.

---

## 0.2 (13-10-2026)

Fase A sampai F.

### Baru
- **Rekam**: lembar metrik ala Strava, peta mengikuti arah gerak, tombol 3D dan kontrol rute, peringatan keluar rute.
- **Suara**: voice over (TTS bawaan, OpenAI, ElevenLabs) untuk split km, jeda/lanjut, zona HR, rute; pengingat minum dan makan.
- **Tema** Terang, Gelap, Sistem dengan font Outfit.
- **You**: profil dengan nama dan sepeda, Pengaturan di balik ikon gear, tab Progress dan Aktivitas.
- **Home**: tantangan opt-in dengan penghargaan, carousel insight.
- **Peta**: OSM dan Mapbox (token sendiri), layar cuaca, lencana cuaca dan kompas.
- Sensor lingkungan BLE dan ringkasan sensor ponsel.
- Video motion splash 15 detik beserta generatornya.

### Diperbaiki
- Arah panah memakai utara sejati dan filter yang dihaluskan.

### Diubah
- Migrasi database tanpa fallback destruktif; skema Room diunggah dari CI.

---

## 0.1 (12-10-2026)

Versi awal.

### Baru
- Modul `core`: rumus fisiologi (kalori, zona HR dan power, TRIMP, fitness/fatigue/form), geo dan telemetri, parser sensor BLE, GPX/TCX/FIT, analisis medan, privasi, tantangan, perencana loop.
- Aplikasi: perekaman dengan foreground service, hub sensor BLE, dashboard head unit, feed Home, kalender, detail aktivitas, profil, perangkat, privasi, backup.
- AI Coach dan AI Route dengan API key milik pengguna; peta multi-lapisan dengan analisis medan.
- Workflow GitHub Actions untuk tes `core` dan build APK debug.

### Diperbaiki
- Penanganan tipe foreground service dan restart, race saat mulai, flush saat stop, siklus hidup peta, batas parser FIT, guard dt GPS, payload CSC pendek.
