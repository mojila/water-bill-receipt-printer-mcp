# Nota Air Kos — Android (Bluetooth)

Aplikasi Android untuk mencetak **Nota Tagihan Air Kos Manyar 3/51-53** ke printer thermal
58mm **EPPOS EP5821** melalui **Bluetooth Classic (RFCOMM/SPP)** — tanpa kabel dan tanpa
driver Windows.

Ini adalah cara ketiga setelah **MCP Server** (`src/`) dan **Desktop App** (`desktop/`).
Logika nota (ESC/POS, format Rupiah, kalkulasi tagihan) adalah port 1:1 dari TypeScript,
sehingga hasil cetak identik dengan dua cara lainnya.

## Cara Kerja

| Komponen | Berkas | Keterangan |
|:--|:--|:--|
| Model + format nota | `data/ReceiptCore.kt` | Port dari `src/core.ts` |
| Perintah ESC/POS | `data/EscPos.kt` | Port dari `src/escpos.ts` |
| Penyimpanan JSON | `data/Store.kt` | Port dari `src/store.ts` |
| Bluetooth SPP | `printer/BluetoothPrinter.kt` | Pengganti Windows Spooler |
| State aplikasi | `MainViewModel.kt` | Data, periode, aksi cetak |
| UI Compose | `ui/NotaAirScreen.kt` | Daftar unit, pratinjau, pemilih printer |

## Fitur

- **Cari Printer Bluetooth**: memindai perangkat di sekitar, menampilkan yang sudah
  *paired* di atas, dan mengingat printer terpilih.
- **Cek Printer**: memastikan Bluetooth aktif dan printer sudah dipasangkan.
- **Daftar unit** dengan total m3, tagihan air, uang sampah, dan total keseluruhan.
- **Tambah / ubah / hapus unit** dengan kalkulasi tagihan air otomatis.
- **Periode tagihan** (bulan + tahun) dan **biaya uang sampah** dapat diubah.
- **Pratinjau nota 58mm** dalam font monospace sebelum dicetak.
- **Cetak Nota** (1 unit) dan **Cetak Semua** (berurutan dengan jeda yang bisa diatur).
- Data disimpan di `units.json` **format yang sama** dengan desktop app dan MCP server.

## Izin Bluetooth

Aplikasi meminta izin saat pertama dibuka:

| Android | Izin |
|:--|:--|
| 12+ (API 31+) | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` |
| 8–11 (API 26–30) | `ACCESS_FINE_LOCATION` (dibutuhkan sistem untuk memindai) |

Pastikan **Bluetooth menyala** dan printer sudah **dipasangkan (paired)** lewat Pengaturan
Android. PIN default printer thermal biasanya `0000` atau `1234`.

## Membangun APK

Prasyarat: **JDK 17** dan **Android SDK** (platform 35 + build-tools). Paling mudah lewat
**Android Studio**, yang menyediakan keduanya.

### 1. Dapatkan Gradle wrapper

Folder ini berisi `gradlew` dan `gradlew.bat`, tetapi **`gradle/wrapper/gradle-wrapper.jar`
(biner) belum ada**. Buat sekali dengan Gradle yang sudah terpasang:

```bash
gradle wrapper --gradle-version 8.11.1
```

Atau, cara termudah: buka folder `android/` di **Android Studio** (File → Open) dan biarkan
Android Studio menyiapkan wrapper + SDK.

### 2. Arahkan ke Android SDK

Salin `local.properties.example` menjadi `local.properties` dan sesuaikan `sdk.dir`:

```properties
sdk.dir=C\:\\Users\\Moijla\\AppData\\Local\\Android\\Sdk
```

`local.properties` diabaikan Git dan tidak boleh di-commit.

### 3. Build

Dari folder root proyek:

```bash
npm run android:debug      # APK debug
npm run android:install    # build + pasang ke perangkat/emulator
npm run android:release    # APK release (belum ditandatangani)
npm run android:bundle     # AAB untuk Play Store
npm run android:clean
```

Atau langsung dari folder `android/`:

```bash
gradlew.bat assembleDebug
```

Hasil: `android/app/build/outputs/apk/debug/app-debug.apk`

### 4. Pasang ke perangkat

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Menjalankan Pengujian

```bash
cd android && gradlew.bat testDebugUnitTest
```

Pengujian (`app/src/test/`) memverifikasi format Rupiah, kalkulasi tagihan air, pelipatan
baris 32 karakter, dan urutan byte ESC/POS (reset, font besar, potong kertas).

## Menyalin Data dari Desktop

Aplikasi memakai skema `units.json` yang sama dengan desktop app dan MCP server, jadi Anda
bisa memindahkan daftar unit tanpa mengetik ulang. Jalankan aplikasi sekali (agar folder
`files/` dibuat), lalu:

```bash
adb push data/units.json /data/local/tmp/units.json
adb shell run-as com.kosmanyar.notaair cp /data/local/tmp/units.json files/units.json
```

Buka ulang aplikasi. Untuk debug build, `run-as` tersedia; pada release build gunakan fitur
*backup* Android atau salin manual lewat pengaturan aplikasi.

## Catatan Teknis

- **Bluetooth Classic SPP**, bukan BLE. UUID standar
  `00001101-0000-1000-8000-00805F9B34FB`. Cocok untuk EP5821 dan hampir semua printer
  thermal 58mm.
- Socket RFCOMM dibuka **per nota** lalu ditutup, sehingga koneksi basi tidak menghambat
  cetakan berikutnya.
- Pemindaian Bluetooth dihentikan otomatis sebelum mengirim data, karena discovery
  mengganggu koneksi.
- Karakter nota dikirim sebagai **ASCII murni**, sama seperti jalur desktop.
- Izin Bluetooth diperiksa sebelum setiap operasi, bukan hanya saat aplikasi dibuka.
