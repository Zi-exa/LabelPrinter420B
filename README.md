# Label Printer 420B

Aplikasi Android kecil untuk mencetak seluruh halaman PDF label pengiriman ke Xprinter XP-420B melalui Bluetooth Classic (SPP), tanpa mengatur ulang ukuran kertas setiap kali mencetak.

## Alur penggunaan

1. Nyalakan XP-420B dan Bluetooth HP.
2. Buka aplikasi lalu tekan **Hubungkan Printer** pada pemakaian pertama.
3. Pilih XP-420B. Jika Android meminta PIN, masukkan `0000`.
4. Tekan **Pilih PDF**, atau gunakan menu **Bagikan** pada PDF dan pilih **Label Printer 420B**.
5. Pilih halaman cetak: **Semua halaman** atau **Pilih halaman** (isi `1-3, 5` dst. Contoh: `1-3,5,8-10`). Tombol akan menampilkan rentang yang dipilih.
6. Tekan **CETAK**.

Printer yang berhasil terhubung disimpan. Pada penggunaan berikutnya aplikasi akan menyambung kembali secara otomatis.

## Format cetak tetap

- Media: 100 × 150 mm
- Resolusi: 203 DPI / 8 dot per mm
- Kanvas: 800 × 1200 dot
- Bahasa printer: TSPL/TSPL2
- Transport: Bluetooth Classic SPP/RFCOMM
- PDF dirender utuh dan dipusatkan; isi tidak dipotong

## Pembaruan aplikasi

Aplikasi mengecek `update.json` di `https://raw.githubusercontent.com/Zi-exa/LabelPrinter420B/master/update.json` saat dibuka dan via **Cek Pembaruan** di bawah layar. Jika versi baru tersedia, dialog muncul — tekan **Update** untuk download APK dari Releases. Tidak perlu uninstall, cukup install di atas versi lama (tanda tangan harus sama).

## Privasi

Aplikasi memakai internet hanya untuk cek pembaruan (`update.json` + download APK). Tidak memakai akun, tidak mengunggah PDF, dan tidak menyimpan isi dokumen. Yang disimpan hanya URI PDF terakhir, nama/alamat Bluetooth printer terakhir, serta pilihan halaman/kualitas.

## Kualitas cetak

- Draft-Hemat: tipis cepat (DENSITY 6, SPEED 4, threshold 140)
- Normal: seimbang (8, 3, 160)
- Tajam: pekat lambat (12, 2, 180)
- Ultra Tajam: pekat + dithering Floyd-Steinberg

Jika hasil masih hitam pekat, coba Draft.

## Build

Prasyarat: JDK 17+, Android SDK API 36, dan Build Tools 35+.

```bash
gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

APK yang dapat dipasang berada di:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk  # signed, 48-60 KB dengan R8
```

## Batas verifikasi

Unit test memverifikasi scaling tanpa crop, pengemasan bitmap 1-bit, pemilihan otomatis XP-420B, dan framing perintah TSPL. Cetak Bluetooth pada perangkat fisik tetap perlu diuji dengan kombinasi HP Android dan XP-420B yang digunakan.
