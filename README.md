# Nota Tagihan Air Kos Manyar 3/51-53

Dua cara mencetak nota **Tagihan Air Kos Manyar 3/51-53** ke printer thermal 58mm **EPPOS EP5821** (driver Windows `POS-58`):

1. **MCP Server** (`src/`) — dipakai dari asisten AI via Model Context Protocol.
2. **Desktop App** (`desktop/`) — aplikasi Windows **WebView2** untuk mencetak dan mengubah data unit secara manual.
3. **Android App** (`android/`) — aplikasi Android yang mencetak lewat **Bluetooth Classic (SPP)** tanpa kabel.

Ketiganya memakai **file data yang sama** (`units.json`), jadi perubahan di aplikasi desktop langsung terlihat oleh MCP dan sebaliknya.

---

## Fitur Utama

- **Kalkulasi Otomatis**:
  - Embedded item tetap: **Uang Plastik Sampah** (default Rp. 8.498, bisa diubah)
  - **Tagihan Air = Total Tagihan - Uang Plastik Sampah**
  - Format angka standar Rupiah Indonesia (contoh: `Rp. 80.502`).
- **Layout 58mm ESC/POS**:
  - Font ukuran **BESAR & BOLD** (Double-Height & Double-Width) untuk:
    1. **Bulan & Tahun Tagihan** (contoh: `SEPTEMBER 2026`)
    2. **Nama Unit / Penghuni** (contoh: `MBAK OCI`)
    3. **Jumlah Pemakaian Air** (contoh: `17 m3`)
    4. **Total Tagihan** (contoh: `Rp. 89.000`)
  - Pembatas garis rapi (32 karakter sesuai lebar kertas 58mm).
  - Jarak sobekan kertas (*feed lines*) dan perintah potong kertas (*paper cut*).
- **Windows Spooler Native**:
  - Mengirim byte raw ESC/POS langsung ke antrean printer Windows `POS-58` via `winspool.drv` (tanpa distorsi teks driver GDI).
- **Bulan & Tahun Tagihan Bisa Diubah** di aplikasi desktop maupun lewat MCP.

---

## Desktop App (WebView2)

Aplikasi desktop untuk mengubah data dan mencetak nota.

**Fitur:**

- Tabel semua unit: nama, m3, tagihan air, uang sampah, total, plus total keseluruhan.
- Form tambah/ubah/hapus unit (nama, m3, total) dengan kalkulasi tagihan air otomatis.
- Pemilih **Bulan** & **Tahun** tagihan (lengkap dengan tombol bulan sebelumnya/berikutnya) serta biaya uang sampah.
- Pratinjau nota 58mm sebelum dicetak.
- Tombol **Cetak Nota** (1 unit) dan **Cetak Semua** (semua unit berurutan dengan jeda).
- **Cek Printer** untuk memastikan `POS-58` terdeteksi di Windows Spooler.

### Prasyarat Desktop

| Kebutuhan | Keterangan |
|:--|:--|
| .NET SDK 8.0+ | https://dotnet.microsoft.com/download |
| WebView2 Runtime | Sudah bawaan Windows 11 / Edge terbaru; jika belum: https://developer.microsoft.com/microsoft-edge/webview2/ |
| Python 3 | Untuk pengiriman raw ke spooler (sama seperti MCP server) |

### Menjalankan Desktop

```bash
npm run desktop:build     # build (Release)
npm run desktop:start     # build + jalankan
```

Atau langsung:

```bash
dotnet run --project desktop/PrintReceiptWater.Desktop.csproj -c Release
```

### Membuat Distribusi (publish)

```bash
npm run desktop:publish
# hasil: dist/desktop/PrintReceiptWater.exe (single file)
```

Folder `dist/desktop` bersifat portable: berisi exe, `print_raw.py`, dan folder `assets/`.

### Struktur Desktop

```
desktop/
├── PrintReceiptWater.Desktop.csproj   # host WebView2 (net8.0-windows)
├── Program.cs                         # entry point WinForms
├── MainForm.cs                        # host WebView2 + jembatan pesan JS <-> C#
├── Receipt.cs                         # model nota, kalkulasi, ESC/POS
├── RawPrinter.cs                      # kirim raw ke spooler (print_raw.py, fallback Win32)
├── Store.cs                           # baca/tulis data/units.json
├── app.manifest
└── assets/
    ├── index.html                     # UI
    ├── app.css
    ├── app.js                         # logika UI + jembatan WebView2
    └── print_raw.py
```

---

## Android App (Bluetooth SPP)

Aplikasi Android untuk mencetak nota lewat **Bluetooth Classic**, tanpa kabel dan tanpa
driver Windows. Penjelasan lengkap ada di [`android/README.md`](android/README.md).

**Fitur:**

- Cari & pilih printer thermal Bluetooth (yang sudah *paired* ditampilkan lebih dulu).
- Cek kesiapan Bluetooth dan printer sebelum mencetak.
- Daftar unit, tambah/ubah/hapus, periode bulan & tahun, biaya uang sampah.
- Pratinjau nota 58mm dan tombol **Cetak Nota** / **Cetak Semua** dengan jeda antar cetak.

### Prasyarat Android

| Kebutuhan | Keterangan |
|:--|:--|
| JDK 17 | https://adoptium.net atau bawaan Android Studio |
| Android SDK | Platform 35 + build-tools; termudah lewat Android Studio |
| Perangkat Android 8.0+ (API 26+) | Dengan Bluetooth dan printer sudah *paired* |

### Membangun Android

```bash
# Sekali saja: buat Gradle wrapper (binernya tidak disertakan di repo)
gradle wrapper --gradle-version 8.11.1

# Salin local.properties.example -> local.properties, sesuaikan sdk.dir

npm run android:debug      # APK debug
npm run android:install    # build + pasang via adb
npm run android:release    # APK release
npm run android:bundle     # AAB
npm run android:clean
```

Hasil build: `android/app/build/outputs/apk/debug/app-debug.apk`

### Struktur Android

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/                      # gradle-wrapper.jar dibuat lokal
├── local.properties.example
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml       # izin Bluetooth
        │   ├── java/com/kosmanyar/notaair/
        │   │   ├── MainActivity.kt
        │   │   ├── MainViewModel.kt
        │   │   ├── data/
        │   │   │   ├── ReceiptCore.kt    # port src/core.ts
        │   │   │   ├── EscPos.kt         # port src/escpos.ts
        │   │   │   └── Store.kt          # port src/store.ts
        │   │   ├── printer/
        │   │   │   └── BluetoothPrinter.kt
        │   │   └── ui/
        │   │       ├── Theme.kt
        │   │       └── NotaAirScreen.kt
        │   └── res/
        └── test/java/...                 # pengujian ESC/POS & format
```

### Lokasi Data di Android

Aplikasi memakai `units.json` dengan **skema identik** (`version`, `settings`, `units`),
disimpan di penyimpanan privat aplikasi (`files/units.json`) — bukan folder `data/` di repo.

---

## Data Unit

Data unit default (dapat diubah dari desktop app atau MCP):

| No | Unit / Penghuni | Pemakaian ($m^3$) | Tagihan Air | Plastik Sampah | Total Tagihan |
|:--:|:----------------|:----------------:|:-----------:|:--------------:|:-------------:|
| 1  | Mbak Oci        | 17               | Rp. 80.502  | Rp. 8.498      | Rp. 89.000    |
| 2  | Pak Budi        | 31               | Rp. 129.502 | Rp. 8.498      | Rp. 138.000   |
| 3  | Mbak Ningsih    | 31               | Rp. 169.502 | Rp. 8.498      | Rp. 178.000   |
| 4  | Pak Giman       | 57               | Rp. 341.502 | Rp. 8.498      | Rp. 350.000   |
| 5  | Pak Sutaji      | 32               | Rp. 176.502 | Rp. 8.498      | Rp. 185.000   |
| 6  | Pak Sugik       | 21               | Rp. 63.502  | Rp. 8.498      | Rp. 72.000    |
| 7  | Bu Sutik        | 21               | Rp. 63.502  | Rp. 8.498      | Rp. 72.000    |
| 8  | Bu Warti        | 22               | Rp. 67.502  | Rp. 8.498      | Rp. 76.000    |

### Lokasi File Data

`data/units.json` di root proyek (dibuat otomatis). Ubah lokasinya dengan environment variable `RECEIPT_DATA_DIR`.

```json
{
  "version": 1,
  "settings": {
    "month": "September",
    "year": 2026,
    "period": "September 2026",
    "printerName": "POS-58",
    "garbageFee": 8498
  },
  "units": [
    { "id": "mbak-oci", "name": "Mbak Oci", "usageM3": 17, "total": 89000 }
  ]
}
```

> `month` + `year` adalah sumber kebenaran; label `period` selalu diturunkan dari keduanya.

---

## Daftar Tools MCP

1. **`list_receipts`** — tabel lengkap seluruh unit. Argumen opsional: `period`.
2. **`preview_receipt`** — pratinjau teks nota 58mm. Argumen: `unitName`, `period` (opsional).
3. **`print_receipt`** — cetak nota satu unit. Argumen: `unitName`, `period`, `printerName` (opsional).
4. **`print_all_receipts`** — cetak semua nota berurutan. Argumen: `period`, `printerName`, `delayMs` (default `1500`).
5. **`print_custom_receipt`** — nota kustom. Argumen: `name`, `usageM3`, `total`, `garbageFee`, `period`, `printerName`.
6. **`check_printer`** — cek status printer di Windows Spooler. Argumen: `printerName`.
7. **`get_settings`** — lihat periode aktif (bulan & tahun), printer, biaya sampah, jumlah unit.
8. **`set_period`** — ubah **bulan & tahun** tagihan. Argumen: `month` (misal `"Oktober"`), `year` (misal `2026`).
9. **`set_garbage_fee`** — ubah biaya uang sampah per unit. Argumen: `garbageFee`.
10. **`update_unit`** — tambah/ubah unit. Argumen: `name`, `usageM3`, `total`.
11. **`remove_unit`** — hapus unit. Argumen: `name`.

---

## Cara Konfigurasi MCP Client

Tambahkan konfigurasi berikut ke konfigurasi MCP client Anda (misal `claude_desktop_config.json` atau Antigravity config):

```json
{
  "mcpServers": {
    "print-receipt-water": {
      "command": "node",
      "args": [
        "c:/Users/Moijla/Projects/print-receipt-water/build/index.js"
      ],
      "env": {
        "PRINTER_NAME": "POS-58"
      }
    }
  }
}
```

---

## Menjalankan Manual

- **Build MCP server**:
  ```bash
  npm run build
  ```
- **Jalankan MCP server**:
  ```bash
  npm start
  ```
- **Build desktop app**:
  ```bash
  npm run desktop:build
  ```
- **Build Android app**:
  ```bash
  npm run android:debug
  ```

---

## Catatan Teknis

- `print_raw.py` tersedia di dua tempat: `src/print_raw.py` (dipakai MCP server setelah build) dan `desktop/assets/print_raw.py` (dipakai desktop app). Sinkronkan dengan `npm run desktop:sync-assets`.
- Desktop app memakai `print_raw.py` sebagai jalur utama; jika Python tidak tersedia, otomatis jatuh ke **Win32 SpoolAPI** (`winspool.drv` P/Invoke) di `desktop/RawPrinter.cs`.
- Nama printer divalidasi sebelum dipakai agar tidak bisa menyuntikkan perintah ke argumen PowerShell.
