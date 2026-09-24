# Label Printer 420B

Aplikasi Android kecil untuk mencetak seluruh halaman PDF label pengiriman ke Xprinter XP-420B melalui Bluetooth Classic (SPP), tanpa mengatur ulang ukuran kertas setiap kali mencetak.

## Alur penggunaan

1. Nyalakan XP-420B dan Bluetooth HP.
2. Buka aplikasi lalu tekan **Hubungkan Printer** pada pemakaian pertama.
3. Pilih XP-420B. Jika Android meminta PIN, masukkan `0000`.
4. Tekan **Pilih PDF**, atau gunakan menu **Bagikan** pada PDF dan pilih **Label Printer 420B**.
5. Tekan **CETAK SEMUA**.

Printer yang berhasil terhubung disimpan. Pada penggunaan berikutnya aplikasi akan menyambung kembali secara otomatis.

## Format cetak tetap

- Media: 100 × 150 mm
- Resolusi: 203 DPI / 8 dot per mm
- Kanvas: 800 × 1200 dot
- Bahasa printer: TSPL/TSPL2
- Transport: Bluetooth Classic SPP/RFCOMM
- PDF dirender utuh dan dipusatkan; isi tidak dipotong

## Privasi

Aplikasi tidak memiliki izin internet, tidak memakai akun, tidak mengunggah PDF, dan tidak menyimpan isi dokumen. Yang disimpan hanya URI PDF terakhir serta nama/alamat Bluetooth printer terakhir.

## Build

Prasyarat: JDK 17+, Android SDK API 36, dan Build Tools 35+.

```bash
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK debug yang dapat dipasang berada di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Batas verifikasi

Unit test memverifikasi scaling tanpa crop, pengemasan bitmap 1-bit, pemilihan otomatis XP-420B, dan framing perintah TSPL. Cetak Bluetooth pada perangkat fisik tetap perlu diuji dengan kombinasi HP Android dan XP-420B yang digunakan.
