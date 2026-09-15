import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

import {
  getAllDefaultReceipts,
  findReceiptByUnitName,
  computeReceipt,
  formatRupiah,
  UANG_PLASTIK_SAMPAH,
  getDefaultPeriod,
} from "./data.js";
import { buildEscposReceipt, buildTextReceiptPreview } from "./escpos.js";
import {
  sendRawToPrinter,
  checkPrinterStatus,
  DEFAULT_PRINTER_NAME,
} from "./printer.js";

const server = new McpServer({
  name: "print-receipt-water",
  version: "1.0.0",
});

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
    const lines = [
      `=== DAFTAR TAGIHAN AIR KOS MANYAR 3/51-53 (${receipts[0]?.period}) ===`,
      `Biaya Plastik Sampah per unit: ${formatRupiah(UANG_PLASTIK_SAMPAH)}`,
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

    return {
      content: [
        {
          type: "text",
          text: lines.join("\n"),
        },
      ],
    };
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
      return {
        content: [
          {
            type: "text",
            text: `Unit dengan nama '${unitName}' tidak ditemukan. Silakan gunakan tool list_receipts untuk melihat nama unit yang tersedia.`,
          },
        ],
      };
    }

    const preview = buildTextReceiptPreview(bill);
    return {
      content: [
        {
          type: "text",
          text: `Pratinjau Nota Thermal (58mm) untuk ${bill.name}:\n\n\`\`\`\n${preview}\n\`\`\``,
        },
      ],
    };
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
      return {
        content: [
          {
            type: "text",
            text: `Unit dengan nama '${unitName}' tidak ditemukan dalam daftar. Gunakan tool 'print_custom_receipt' jika ingin mencetak unit di luar daftar default.`,
          },
        ],
      };
    }

    const targetPrinter = printerName || DEFAULT_PRINTER_NAME;
    const rawBuffer = buildEscposReceipt(bill);

    try {
      const result = await sendRawToPrinter(rawBuffer, targetPrinter);
      const preview = buildTextReceiptPreview(bill);
      return {
        content: [
          {
            type: "text",
            text: `✅ ${result.message}\n\nUnit: ${bill.name}\nPemakaian: ${bill.usageM3} m3\nTagihan Air: ${formatRupiah(
              bill.waterBill
            )}\nUang Plastik Sampah: ${formatRupiah(bill.garbageFee)}\nTotal: ${formatRupiah(
              bill.total
            )}\nPeriode: ${bill.period}\n\nPratinjau Fisik:\n\`\`\`\n${preview}\n\`\`\``,
          },
        ],
      };
    } catch (err: any) {
      return {
        content: [
          {
            type: "text",
            text: `❌ Gagal mencetak nota untuk ${bill.name} ke printer '${targetPrinter}'.\nError: ${err.message}`,
          },
        ],
      };
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
    const targetPrinter = printerName || DEFAULT_PRINTER_NAME;
    const results: string[] = [];

    for (let i = 0; i < receipts.length; i++) {
      const bill = receipts[i];
      try {
        const rawBuffer = buildEscposReceipt(bill);
        await sendRawToPrinter(rawBuffer, targetPrinter);
        results.push(`✅ [${i + 1}/${receipts.length}] ${bill.name} (${formatRupiah(bill.total)}) berhasil dicetak.`);
      } catch (err: any) {
        results.push(`❌ [${i + 1}/${receipts.length}] ${bill.name} gagal dicetak: ${err.message}`);
      }

      // Delay between prints so paper cutter / motor does not get overwhelmed
      if (i < receipts.length - 1 && delayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }
    }

    return {
      content: [
        {
          type: "text",
          text: `Selesai memproses pencetakan ${receipts.length} nota:\n\n` + results.join("\n"),
        },
      ],
    };
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
    const fee = garbageFee !== undefined ? garbageFee : UANG_PLASTIK_SAMPAH;
    const bill = computeReceipt(name, usageM3, total, period, fee);
    const targetPrinter = printerName || DEFAULT_PRINTER_NAME;
    const rawBuffer = buildEscposReceipt(bill);

    try {
      const result = await sendRawToPrinter(rawBuffer, targetPrinter);
      const preview = buildTextReceiptPreview(bill);
      return {
        content: [
          {
            type: "text",
            text: `✅ ${result.message}\n\nNota Kustom ${bill.name} berhasil dicetak.\n\n\`\`\`\n${preview}\n\`\`\``,
          },
        ],
      };
    } catch (err: any) {
      return {
        content: [
          {
            type: "text",
            text: `❌ Gagal mencetak nota kustom: ${err.message}`,
          },
        ],
      };
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
    const target = printerName || DEFAULT_PRINTER_NAME;
    const status = await checkPrinterStatus(target);

    return {
      content: [
        {
          type: "text",
          text: [
            `Status Printer '${target}':`,
            `- Terdeteksi: ${status.isAvailable ? "Ya ✅" : "Tidak ❌"}`,
            `- Port: ${status.port}`,
            `- Driver: ${status.driver}`,
            `- Status Spooler: ${status.status}`,
            status.rawError ? `- Detail Error: ${status.rawError}` : "",
          ]
            .filter(Boolean)
            .join("\n"),
        },
      ],
    };
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
