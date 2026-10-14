# Strideway: logo dan warna

## Isi folder

Tiga bentuk logo, masing-masing dalam empat varian warna, dalam format SVG (vektor) dan PNG (transparan).

| Bentuk | Dipakai untuk |
|---|---|
| `lockup` (ikon + tulisan) | Header, kop, kemasan, layar pembuka |
| `icon` (ban dan S) | Ikon aplikasi, avatar, watermark kecil |
| `wordmark` (tulisan saja) | Tempat yang sempit memanjang, footer, watermark panjang |

| Varian | Dipakai di atas |
|---|---|
| `color-light` | Latar putih atau terang |
| `color-dark` | Latar hitam atau foto gelap |
| `black` | Cetak satu warna, stempel, fax, dokumen hitam putih |
| `white` | Watermark di atas foto, bordir, sablon satu warna |

Contoh nama file: `strideway-lockup-color-dark.svg`. Semua file dibuat ulang dengan `python tools/branding/build_logo.py`.

## Warna

| Nama | HEX | RGB | Peran |
|---|---|---|---|
| Signal | `#FF4F00` | 255, 79, 0 | Aksen: ban pada ikon dan dua garis pada tulisan |
| Ink | `#0A0A0B` | 10, 10, 11 | Huruf, S, dan teks pada latar terang |
| Putih | `#FFFFFF` | 255, 255, 255 | Huruf dan S pada latar gelap |

Kontras (WCAG):

| Pasangan | Rasio | Artinya |
|---|---|---|
| Ink di atas putih | 19,79 : 1 | Aman untuk teks apa pun |
| Signal di atas Ink | 6,00 : 1 | Aman untuk teks dan grafik |
| Signal di atas putih | 3,30 : 1 | Aman untuk grafik dan teks besar, tidak untuk teks kecil |

Aturan pakai:
- Oranye hanya untuk aksen, sekitar sepersepuluh luas logo. Jangan dipakai untuk huruf `STRIDEWAY`.
- Jangan pakai oranye untuk paragraf atau teks kecil di atas putih. Pakai Ink.
- Jangan menaruh logo berwarna di atas latar oranye atau merah. Pakai varian `black` atau `white`.
- Warna di atas adalah warna layar. Untuk cetak, minta pencocokan warna (Pantone atau CMYK) ke percetakan dan lihat contoh cetaknya dulu, karena oranye terang sering bergeser saat dicetak.

## Filosofi logo

**Nama.** *Stride* adalah langkah lari. *Way* adalah jalan atau rute. Nama ini memuat dua olahraga: langkah untuk pelari, jalan untuk pesepeda.

**Ban segi delapan.** Lingkaran ban dibuat dari delapan sisi dengan sudut potong 45°, bukan kurva. Bentuk bersudut ini sama dengan potongan pada semua huruf di font Strideway Sans, jadi ikon dan tulisan terlihat berasal dari satu tangan. Ban mewakili bersepeda.

**Huruf S di tengah.** S adalah huruf awal Strideway. Garisnya berbelok tajam seperti jalan menanjak yang berkelok, dan letaknya di titik tengah seperti as roda. Ban mengelilingi S seperti roda mengelilingi porosnya.

**Kemiringan 12°.** Semua elemen condong ke depan dengan sudut yang sama. Tanpa kemiringan, segi delapan terlihat berhenti. Dengan kemiringan, ia terlihat bergerak.

**Dua garis pada tulisan.** Garis atas bergeser ke kanan, garis bawah ke kiri. Letaknya berseberangan sehingga seimbang, seperti dua garis lintasan lari yang berjajar di jalur yang sama. Garis tidak menyentuh huruf, jadi tulisan tetap terbaca di ukuran kecil. Garis mewakili lari dan lintasan.

**Berat huruf.** Huruf tebal agar tetap terbaca dari jauh, di jersei, di papan penunjuk, dan sebagai watermark di atas foto.

Ini tafsir yang kami berikan pada bentuknya. Pengguna tidak akan membaca semuanya dengan sendirinya, jadi makna ini perlu ikut diceritakan di materi merek.

## Filosofi warna

**Oranye Signal.** Oranye terang adalah warna yang dipakai pada rompi dan perlengkapan keselamatan pesepeda dan pelari, karena paling mudah terlihat di jalan. Kami memakainya untuk alasan yang sama: terlihat dari jauh di antara foto, peta, dan layar yang ramai. Oranye juga dekat dengan rasa panas, tenaga, dan denyut jantung yang naik saat berolahraga. Aplikasi olahraga lain banyak memakai biru atau hijau, jadi oranye membuat Strideway mudah dibedakan.

**Ink.** Hitam kehitaman dipilih untuk huruf karena paling tegas dan tahan di atas foto terang. Warnanya sedikit lebih lembut dari hitam murni agar tidak terlalu keras di layar.

**Putih.** Dipakai sebagai ruang bernapas dan sebagai warna huruf di atas latar gelap.

**Mengapa bukan hijau lime.** Hijau lime terang juga mencolok, tetapi kontrasnya hanya 1,32 : 1 di atas putih, jadi hampir hilang di latar terang. Oranye tetap terlihat di keduanya.

## Catatan

- Segi delapan juga bentuk rambu berhenti. Kemiringan 12° dan huruf S di tengahnya mengurangi kesan itu, tetapi ini bisa ditanyakan ke calon pengguna.
- Di ukuran sangat kecil (24 px ke bawah) huruf S di dalam ban menipis. Untuk ikon sekecil itu, pertimbangkan versi S yang lebih besar.
- Warna ini belum dipasang di aplikasi Android Strideway. Tema aplikasi masih memakai warna yang ada di `app/src/main/res/values/colors.xml`.
