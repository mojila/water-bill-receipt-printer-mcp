# MCP Server: Thermal Printer EPPOS EP5821 (Tagihan Air Kos Manyar 3/51-53)

MCP (Model Context Protocol) Server untuk mencetak nota **Tagihan Air Kos Manyar 3/51-53** ke printer thermal 58mm **EPPOS EP5821** (terdaftar sebagai driver `POS-58` di Windows).

---

## Fitur Utama

- **Kalkulasi Otomatis**:
  - Embedded item tetap: **Uang Plastik Sampah = Rp. 8.498**
  - **Tagihan Air = Total Tagihan - Rp. 8.498**
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

---

## Data Unit Bawaan

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

---

## Daftar Tools MCP

1. **`list_receipts`**:
   Menampilkan tabel lengkap seluruh unit, rincian m3, tagihan air, sampah, dan total.
   - Argumen opsional: `period` (contoh: `"September 2026"`).

2. **`preview_receipt`**:
   Melihat pratinjau teks nota (simulasi 32 kolom) di layar sebelum dicetak.
   - Argumen wajib: `unitName` (contoh: `"Mbak Oci"`).
   - Argumen opsional: `period`.

3. **`print_receipt`**:
   Mencetak nota satu unit tertentu langsung ke printer EPPOS EP5821.
   - Argumen wajib: `unitName` (contoh: `"Pak Budi"`).
   - Argumen opsional: `period`, `printerName` (default: `"POS-58"`).

4. **`print_all_receipts`**:
   Mencetak seluruh nota untuk ke-8 unit satu per satu secara berurutan dengan jeda waktu.
   - Argumen opsional: `period`, `printerName`, `delayMs` (default: `1500`).

5. **`print_custom_receipt`**:
   Mencetak nota kustom untuk penghuni di luar daftar atau bulan khusus.
   - Argumen: `name`, `usageM3`, `total`, `garbageFee` (default: `8498`), `period`, `printerName`.

6. **`check_printer`**:
   Mengecek status ketersediaan printer `POS-58` di Windows Spooler.

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

- **Build**:
  ```bash
  npm run build
  copy src\print_raw.py build\print_raw.py
  ```
- **Jalankan Server**:
  ```bash
  npm start
  ```
