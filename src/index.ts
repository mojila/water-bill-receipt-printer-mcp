import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

import {
  getAllDefaultReceipts,
  findReceiptByUnitName,
  computeReceipt,
  formatRupiah,
  UANG_PLASTIK_SAMPAH,
  MONTHS,
  makePeriod,
} from "./data.js";
import { buildEscposReceipt, buildTextReceiptPreview } from "./escpos.js";
import {
  sendRawToPrinter,
  checkPrinterStatus,
  DEFAULT_PRINTER_NAME,
} from "./printer.js";
import { getSettings, setSettings, getUnits, setUnits, loadStore } from "./store.js";

const server = new McpServer({
  name: "print-receipt-water",
  version: "1.0.0",
});

function text(body: string) {
  return { content: [{ type: "text" as const, text: body }] };
}

/**
 * TOOL 1: list_receipts
 * Melihat daftar semua unit, pemakaian m3, tagihan air, sampah, dan total.
 */
server.tool(
  "list_receipts",
  "Menampilkan tabel rincian seluruh tagihan air kos (nama penghuni, m3, tagihan air, uang sampah, dan total).",
  {
    period: z
      .string()
      .optional()
      .describe("Periode tagihan (contoh: 'September 2026'). Default bulan saat ini."),
  },
  async ({ period }) => {
    const receipts = getAllDefaultReceipts(period);
    const settings = getSettings();
    const lines = [
      `=== DAFTAR TAGIHAN AIR KOS MANYAR 3/51-53 (${receipts[0]?.period}) ===`,
      `Biaya Plastik Sampah per unit: ${formatRupiah(settings.garbageFee)}`,
      "",
      "| No | Nama Unit / Penghuni | Pemakaian (m3) | Tagihan Air   | Plastik Sampah | Total Tagihan |",
      "|---|----------------------|----------------|---------------|----------------|---------------|",
    ];

    receipts.forEach((r, idx) => {
      lines.push(
        `| ${idx + 1} | ${r.name.padEnd(20)} | ${String(r.usageM3).padStart(14)} | ${formatRupiah(
          r.waterBill
        ).padStart(13)} | ${formatRupiah(r.garbageFee).padStart(14)} | ${formatRupiah(
          r.total
        ).padStart(13)} |`
      );
    });

    const grandTotal = receipts.reduce((acc, curr) => acc + curr.total, 0);
    const totalWater = receipts.reduce((acc, curr) => acc + curr.waterBill, 0);
    const totalGarbage = receipts.reduce((acc, curr) => acc + curr.garbageFee, 0);
    const totalM3 = receipts.reduce((acc, curr) => acc + curr.usageM3, 0);

    lines.push(
      "|---|----------------------|----------------|---------------|----------------|---------------|"
    );
    lines.push(
      `|   | **TOTAL KESELURUHAN** | **${totalM3} m3** | **${formatRupiah(
        totalWater
      )}** | **${formatRupiah(totalGarbage)}** | **${formatRupiah(grandTotal)}** |`
    );

    return text(lines.join("\n"));
  }
);

/**
 * TOOL 2: preview_receipt
 * Menampilkan preview ASCII nota termal 58mm untuk satu unit tertentu
 */
server.tool(
  "preview_receipt",
  "Melihat pratinjau layout nota thermal 58mm untuk unit tertentu sebelum dicetak.",
  {
    unitName: z
      .string()
      .describe("Nama unit/penghuni kos, contoh: 'Mbak Oci', 'Pak Budi', dll."),
    period: z
      .string()
      .optional()
      .describe("Periode tagihan (contoh: 'September 2026'). Default bulan saat ini."),
  },
  async ({ unitName, period }) => {
    const bill = findReceiptByUnitName(unitName, period);
    if (!bill) {
      return text(
        `Unit dengan nama '${unitName}' tidak ditemukan. Silakan gunakan tool list_receipts untuk melihat nama unit yang tersedia.`
      );
    }

    const preview = buildTextReceiptPreview(bill);
    return text(`Pratinjau Nota Thermal (58mm) untuk ${bill.name}:\n\n\`\`\`\n${preview}\n\`\`\``);
  }
);

/**
 * TOOL 3: print_receipt
 * Mencetak nota satu unit ke printer thermal EPPOS EP5821 (POS-58)
 */
server.tool(
  "print_receipt",
  "Mencetak nota tagihan air untuk 1 penghuni kos ke printer thermal EPPOS EP5821.",
  {
    unitName: z
      .string()
      .describe("Nama unit/penghuni yang ingin dicetak, contoh: 'Mbak Oci'"),
    period: z
      .string()
      .optional()
      .describe("Periode tagihan (contoh: 'September 2026'). Default bulan saat ini."),
    printerName: z
      .string()
      .optional()
      .describe(`Nama printer Windows Spooler. Default: '${DEFAULT_PRINTER_NAME}'`),
  },
  async ({ unitName, period, printerName }) => {
    const bill = findReceiptByUnitName(unitName, period);
    if (!bill) {
      return text(
        `Unit dengan nama '${unitName}' tidak ditemukan dalam daftar. Gunakan tool 'print_custom_receipt' jika ingin mencetak unit di luar daftar default.`
      );
    }

    const targetPrinter = printerName || getSettings().printerName;
    const rawBuffer = buildEscposReceipt(bill);

    try {
      const result = await sendRawToPrinter(rawBuffer, targetPrinter);
      const preview = buildTextReceiptPreview(bill);
      return text(
        `✅ ${result.message}\n\nUnit: ${bill.name}\nPemakaian: ${bill.usageM3} m3\nTagihan Air: ${formatRupiah(
          bill.waterBill
        )}\nUang Plastik Sampah: ${formatRupiah(bill.garbageFee)}\nTotal: ${formatRupiah(
          bill.total
        )}\nPeriode: ${bill.period}\n\nPratinjau Fisik:\n\`\`\`\n${preview}\n\`\`\``
      );
    } catch (err: any) {
      return text(
        `❌ Gagal mencetak nota untuk ${bill.name} ke printer '${targetPrinter}'.\nError: ${err.message}`
      );
    }
  }
);

/**
 * TOOL 4: print_all_receipts
 * Mencetak seluruh nota unit satu per satu secara berurutan
 */
server.tool(
  "print_all_receipts",
  "Mencetak semua nota penghuni kos satu per satu secara berurutan dengan jeda waktu.",
  {
    period: z
      .string()
      .optional()
      .describe("Periode tagihan (contoh: 'September 2026'). Default bulan saat ini."),
    printerName: z
      .string()
      .optional()
      .describe(`Nama printer Windows Spooler. Default: '${DEFAULT_PRINTER_NAME}'`),
    delayMs: z
      .number()
      .optional()
      .describe("Jeda waktu antar cetak dalam milidetik. Default: 1500 ms."),
  },
  async ({ period, printerName, delayMs = 1500 }) => {
    const receipts = getAllDefaultReceipts(period);
    const targetPrinter = printerName || getSettings().printerName;
    const results: string[] = [];

    for (let i = 0; i < receipts.length; i++) {
      const bill = receipts[i];
      try {
        const rawBuffer = buildEscposReceipt(bill);
        await sendRawToPrinter(rawBuffer, targetPrinter);
        results.push(
          `✅ [${i + 1}/${receipts.length}] ${bill.name} (${formatRupiah(bill.total)}) berhasil dicetak.`
        );
      } catch (err: any) {
        results.push(`❌ [${i + 1}/${receipts.length}] ${bill.name} gagal dicetak: ${err.message}`);
      }

      // Delay between prints so paper cutter / motor does not get overwhelmed
      if (i < receipts.length - 1 && delayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }
    }

    return text(
      `Selesai memproses pencetakan ${receipts.length} nota:\n\n` + results.join("\n")
    );
  }
);

/**
 * TOOL 5: print_custom_receipt
 * Mencetak nota kustom untuk nama, pemakaian, atau total yang berbeda
 */
server.tool(
  "print_custom_receipt",
  "Mencetak nota kustom dengan nama penghuni, m3 pemakaian, atau total kustom.",
  {
    name: z.string().describe("Nama penghuni / unit kos"),
    usageM3: z.number().describe("Jumlah pemakaian air dalam m3"),
    total: z.number().describe("Total pembayaran dalam Rupiah"),
    garbageFee: z
      .number()
      .optional()
      .describe(`Biaya sampah. Default: ${UANG_PLASTIK_SAMPAH}`),
    period: z
      .string()
      .optional()
      .describe("Periode tagihan (contoh: 'September 2026'). Default bulan saat ini."),
    printerName: z
      .string()
      .optional()
      .describe(`Nama printer Windows Spooler. Default: '${DEFAULT_PRINTER_NAME}'`),
  },
  async ({ name, usageM3, total, garbageFee, period, printerName }) => {
    const settings = getSettings();
    const fee = garbageFee !== undefined ? garbageFee : settings.garbageFee;
    const bill = computeReceipt(name, usageM3, total, period, fee);
    const targetPrinter = printerName || settings.printerName;
    const rawBuffer = buildEscposReceipt(bill);

    try {
      const result = await sendRawToPrinter(rawBuffer, targetPrinter);
      const preview = buildTextReceiptPreview(bill);
      return text(
        `✅ ${result.message}\n\nNota Kustom ${bill.name} berhasil dicetak.\n\n\`\`\`\n${preview}\n\`\`\``
      );
    } catch (err: any) {
      return text(`❌ Gagal mencetak nota kustom: ${err.message}`);
    }
  }
);

/**
 * TOOL 6: check_printer
 * Mengecek status dan ketersediaan printer di Windows
 */
server.tool(
  "check_printer",
  "Memeriksa apakah printer POS-58 / EPPOS terdeteksi dan siap di Windows Spooler.",
  {
    printerName: z
      .string()
      .optional()
      .describe(`Nama printer yang ingin diperiksa. Default: '${DEFAULT_PRINTER_NAME}'`),
  },
  async ({ printerName }) => {
    const target = printerName || getSettings().printerName;
    const status = await checkPrinterStatus(target);

    return text(
      [
        `Status Printer '${target}':`,
        `- Terdeteksi: ${status.isAvailable ? "Ya ✅" : "Tidak ❌"}`,
        `- Port: ${status.port}`,
        `- Driver: ${status.driver}`,
        `- Status Spooler: ${status.status}`,
        status.rawError ? `- Detail Error: ${status.rawError}` : "",
      ]
        .filter(Boolean)
        .join("\n")
    );
  }
);

/**
 * TOOL 7: get_settings
 * Melihat periode tagihan (bulan & tahun) serta printer yang sedang dipakai
 */
server.tool(
  "get_settings",
  "Menampilkan pengaturan aktif: periode bulan & tahun tagihan, printer, dan biaya sampah.",
  {},
  async () => {
    const settings = getSettings();
    const store = loadStore();
    return text(
      [
        `Periode aktif: ${settings.period}`,
        `Bulan: ${settings.month}`,
        `Tahun: ${settings.year}`,
        `Printer: ${settings.printerName}`,
        `Biaya plastik sampah: ${formatRupiah(settings.garbageFee)}`,
        `Jumlah unit terdaftar: ${store.units.length}`,
        `File data: ${process.env.RECEIPT_DATA_DIR || "data/units.json"}`,
      ].join("\n")
    );
  }
);

/**
 * TOOL 8: set_period
 * Mengubah bulan & tahun tagihan yang dipakai semua nota
 */
server.tool(
  "set_period",
  "Mengubah bulan dan tahun tagihan (misal September 2026) yang dipakai untuk semua nota.",
  {
    month: z
      .string()
      .describe(`Nama bulan dalam Bahasa Indonesia, contoh: 'September'. Pilihan: ${MONTHS.join(", ")}`),
    year: z.number().describe("Tahun tagihan, contoh: 2026"),
  },
  async ({ month, year }) => {
    const store = setSettings({ month, year, period: makePeriod(month, year) });
    return text(`✅ Periode tagihan diubah menjadi: ${store.settings.period}`);
  }
);

/**
 * TOOL 9: set_garbage_fee
 * Mengubah biaya plastik sampah per unit
 */
server.tool(
  "set_garbage_fee",
  "Mengubah biaya plastik sampah (uang sampah) per unit yang dipakai untuk semua nota.",
  {
    garbageFee: z.number().describe("Biaya plastik sampah dalam Rupiah, contoh: 8498"),
  },
  async ({ garbageFee }) => {
    const store = setSettings({ garbageFee });
    return text(
      `✅ Biaya plastik sampah diubah menjadi ${formatRupiah(store.settings.garbageFee)}.`
    );
  }
);

/**
 * TOOL 10: update_unit
 * Menambah atau mengubah unit: nama, pemakaian m3, dan total tagihan
 */
server.tool(
  "update_unit",
  "Menambah unit baru atau mengubah data unit yang sudah ada (nama, pemakaian m3, total tagihan).",
  {
    name: z.string().describe("Nama unit/penghuni, contoh: 'Mbak Oci'"),
    usageM3: z.number().describe("Jumlah pemakaian air dalam m3"),
    total: z.number().describe("Total tagihan dalam Rupiah"),
  },
  async ({ name, usageM3, total }) => {
    const units = getUnits();
    const target = name.toLowerCase().trim();
    const index = units.findIndex((u) => u.name.toLowerCase().trim() === target);

    if (index >= 0) {
      units[index] = { ...units[index], name, usageM3, total };
      setUnits(units);
      return text(
        `✅ Unit '${name}' diperbarui: ${usageM3} m3, total ${formatRupiah(total)}.`
      );
    }

    units.push({
      id: name.toLowerCase().replace(/[^a-z0-9]/g, "-"),
      name,
      usageM3,
      total,
    });
    setUnits(units);
    return text(
      `✅ Unit baru '${name}' ditambahkan: ${usageM3} m3, total ${formatRupiah(total)}.`
    );
  }
);

/**
 * TOOL 11: remove_unit
 * Menghapus unit dari daftar
 */
server.tool(
  "remove_unit",
  "Menghapus unit/penghuni dari daftar tagihan.",
  {
    name: z.string().describe("Nama unit/penghuni yang ingin dihapus, contoh: 'Mbak Oci'"),
  },
  async ({ name }) => {
    const units = getUnits();
    const target = name.toLowerCase().trim();
    const filtered = units.filter((u) => u.name.toLowerCase().trim() !== target);

    if (filtered.length === units.length) {
      return text(`Unit dengan nama '${name}' tidak ditemukan dalam daftar.`);
    }

    setUnits(filtered);
    return text(`✅ Unit '${name}' dihapus. Sisa ${filtered.length} unit terdaftar.`);
  }
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("MCP Server 'print-receipt-water' berjalan via stdio.");
}

main().catch((err) => {
  console.error("Fatal error in MCP Server:", err);
  process.exit(1);
});
