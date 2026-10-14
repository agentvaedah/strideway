# Strideway splash video

Video intro 15 detik (1080x1920, 30 fps, H.264) yang dirender dari kode, jadi bisa diulang dan diubah.

## Hasil

`assets/motion/splash_15s.mp4` (dengan suara), `splash_15s_silent.mp4` (tanpa suara), `splash_poster.png` (frame terakhir).

Frame terakhir dan frame pertama memakai `#0E1012`, sama dengan `windowBackground` di `app/src/main/res/values/themes.xml`, jadi sambungan ke layar Home tidak melompat.

## Render ulang

Kebutuhan: Node 20+, Python 3 dengan numpy, ffmpeg di PATH, font Bahnschrift (bawaan Windows) untuk angka HUD. Wordmark memakai Strideway Sans dari `assets/fonts/`, yang dibangun dengan `python tools/font/build_font.py` (butuh fontTools).

```
cd tools/motion-splash
npm install
node render.mjs                       # tanpa suara, sekitar 4 menit
python -I make_audio.py               # out/splash_audio.wav
ffmpeg -i ../../assets/motion/splash_15s_silent.mp4 -i out/splash_audio.wav -c:v copy -c:a aac -b:a 128k ../../assets/motion/splash_15s.mp4
node render.mjs --stills 2.6,9.8,14.9 # PNG pratinjau di out/
```

## Isi

| Waktu | Adegan |
|---|---|
| 0,0 sampai 1,5 | titik GPS menyala, dua ping, kontur terbaca dari titik itu |
| 1,5 sampai 8,0 | rute naik dengan kamera mengikuti arah, profil elevasi tumbuh, HUD kecepatan, zona detak jantung, kadens |
| 8,0 sampai 11,2 | kamera mundur, lapisan kemiringan (palet sama dengan `DemMath.slopeColor`) menyapu dari bawah |
| 11,2 sampai 13,65 | peta memudar, titik menarik garis di bawah kata, huruf muncul di belakangnya, titik melompat jadi titik pada huruf i |
| 13,8 sampai 15,0 | tagline dan diam |

Angka kecepatan, detak jantung, dan kadens adalah nilai demo, bukan data nyata.

## Data splash di aplikasi

Splash 5 detik di aplikasi (`app/.../ui/splash/`) memakai medan dan rute yang sama dengan video. Keduanya dibangun dari `scene.mjs`, yang juga dipakai `render.mjs`.

```
node export-scene.mjs --preview   # menulis app/src/main/res/raw/splash_scene.json dan out/scene_preview.png
```

Isinya polyline kontur di sekitar awal rute (disederhanakan Douglas-Peucker 1,2 unit), 1.100 unit pertama rute, dan heading yang sudah dihaluskan. Ukurannya sekitar 50 KB. Jalankan ulang bila medan atau rute di `scene.mjs` berubah.

## Memutar video di aplikasi

Video ini tidak dipasang ke aplikasi; aplikasi memakai splash Compose 5 detik di atas. Splash API Android 12+ tidak memutar video, jadi pemakaian video berarti Activity sendiri (misalnya `VideoView` atau Media3 dengan `res/raw/splash_15s.mp4`) yang tampil setelah splash sistem. Video 15 detik sebaiknya bisa dilewati dan hanya tampil pada peluncuran pertama.
