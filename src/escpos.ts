import { UnitBill, formatRupiah, padLine, centerText, LINE_WIDTH } from "./core.js";

// ESC/POS Command Constants
const ESC = "\x1b";
const GS = "\x1d";

export const COMMANDS = {
  RESET: `${ESC}@`,
  ALIGN_LEFT: `${ESC}a\x00`,
  ALIGN_CENTER: `${ESC}a\x01`,
  ALIGN_RIGHT: `${ESC}a\x02`,
  BOLD_ON: `${ESC}E\x01`,
  BOLD_OFF: `${ESC}E\x00`,
  // Double height & double width (GS ! 0x11 = 17 decimal)
  DOUBLE_SIZE_ON: `${GS}!\x11`,
  // Double height only (GS ! 0x01 = 1 decimal)
  DOUBLE_HEIGHT_ON: `${GS}!\x01`,
  // Normal size (GS ! 0x00)
  NORMAL_SIZE: `${GS}!\x00`,
  FEED_LINES: (n: number) => `${ESC}d${String.fromCharCode(n)}`,
  PARTIAL_CUT: `${GS}V\x01`,
  FULL_CUT: `${GS}V\x00`,
};

/**
 * Generate binary buffer containing ESC/POS commands for 58mm thermal printer
 */
export function buildEscposReceipt(bill: UnitBill): Buffer {
  const parts: string[] = [];

  // Reset printer
  parts.push(COMMANDS.RESET);

  // HEADER (Center)
  parts.push(COMMANDS.ALIGN_CENTER);
  parts.push(COMMANDS.BOLD_ON);
  parts.push("KOS MANYAR 3/51-53\n");
  parts.push("NOTA TAGIHAN AIR\n");
  parts.push(COMMANDS.BOLD_OFF);
  parts.push("-".repeat(LINE_WIDTH) + "\n");

  // BULAN & TAHUN (Font Besar & Bold)
  parts.push(COMMANDS.ALIGN_CENTER);
  parts.push(COMMANDS.BOLD_ON);
  parts.push(COMMANDS.DOUBLE_SIZE_ON);
  const periodText = (bill.period || "TAGIHAN").toUpperCase();
  parts.push(periodText + "\n");
  parts.push(COMMANDS.NORMAL_SIZE);
  parts.push(COMMANDS.BOLD_OFF);
  parts.push("-".repeat(LINE_WIDTH) + "\n");

  // NAMA PENGHUNI (Font Besar & Bold)
  parts.push(COMMANDS.ALIGN_LEFT);
  parts.push("Nama Penghuni:\n");
  parts.push(COMMANDS.BOLD_ON);
  parts.push(COMMANDS.DOUBLE_SIZE_ON);
  parts.push(bill.name.toUpperCase() + "\n");
  parts.push(COMMANDS.NORMAL_SIZE);
  parts.push(COMMANDS.BOLD_OFF);
  parts.push("\n");

  // JUMLAH PEMAKAIAN (Font Besar & Bold)
  parts.push(COMMANDS.ALIGN_LEFT);
  parts.push("Pemakaian Air:\n");
  parts.push(COMMANDS.BOLD_ON);
  parts.push(COMMANDS.DOUBLE_SIZE_ON);
  parts.push(`${bill.usageM3} m3\n`);
  parts.push(COMMANDS.NORMAL_SIZE);
  parts.push(COMMANDS.BOLD_OFF);
  parts.push("-".repeat(LINE_WIDTH) + "\n");

  // RINCIAN TAGIHAN (Normal font, rapi rata kiri-kanan)
  parts.push(COMMANDS.ALIGN_LEFT);
  parts.push(COMMANDS.NORMAL_SIZE);
  parts.push(COMMANDS.BOLD_OFF);
  parts.push(padLine("Tagihan Air:", formatRupiah(bill.waterBill)) + "\n");
  parts.push(padLine("Uang Plastik Sampah:", formatRupiah(bill.garbageFee)) + "\n");
  parts.push("=".repeat(LINE_WIDTH) + "\n");

  // TOTAL (Font Besar & Bold)
  parts.push(COMMANDS.ALIGN_CENTER);
  parts.push(COMMANDS.BOLD_ON);
  parts.push("TOTAL TAGIHAN:\n");
  parts.push(COMMANDS.DOUBLE_SIZE_ON);
  parts.push(formatRupiah(bill.total) + "\n");
  parts.push(COMMANDS.NORMAL_SIZE);
  parts.push(COMMANDS.BOLD_OFF);
  parts.push("-".repeat(LINE_WIDTH) + "\n");

  // FOOTER
  const printDate = formatPrintDate();

  parts.push(COMMANDS.ALIGN_CENTER);
  parts.push("Terima Kasih\n");
  parts.push(`Dicetak: ${printDate}\n`);

  // FEED PAPER & TEAR SPACE
  parts.push(COMMANDS.FEED_LINES(4));
  // Cut command (in case model has auto-cutter)
  parts.push(COMMANDS.PARTIAL_CUT);

  return Buffer.from(parts.join(""), "ascii");
}

/**
 * Generate human-readable text preview simulating the 58mm thermal receipt
 */
export function buildTextReceiptPreview(bill: UnitBill): string {
  const divider = "-".repeat(LINE_WIDTH);
  const doubleDivider = "=".repeat(LINE_WIDTH);
  const period = (bill.period || "TAGIHAN").toUpperCase();
  const printDate = formatPrintDate();

  return [
    divider,
    centerText("KOS MANYAR 3/51-53"),
    centerText("NOTA TAGIHAN AIR"),
    divider,
    centerText(`[ ${period} ]`),
    divider,
    "Nama Penghuni:",
    `>>> ${bill.name.toUpperCase()} <<<  [FONT BESAR & BOLD]`,
    "",
    "Pemakaian Air:",
    `>>> ${bill.usageM3} m3 <<<  [FONT BESAR & BOLD]`,
    divider,
    padLine("Tagihan Air:", formatRupiah(bill.waterBill)),
    padLine("Uang Plastik Sampah:", formatRupiah(bill.garbageFee)),
    doubleDivider,
    centerText("TOTAL TAGIHAN:"),
    centerText(`>>> ${formatRupiah(bill.total)} <<<`),
    centerText("[FONT BESAR & BOLD]"),
    divider,
    centerText("Terima Kasih"),
    centerText(`Dicetak: ${printDate}`),
    divider,
  ].join("\n");
}

export function formatPrintDate(date: Date = new Date()): string {
  return `${String(date.getDate()).padStart(2, "0")}/${String(date.getMonth() + 1).padStart(
    2,
    "0"
  )}/${date.getFullYear()} ${String(date.getHours()).padStart(2, "0")}:${String(
    date.getMinutes()
  ).padStart(2, "0")}`;
}

/** Render the 58mm receipt for a WebView2 page (HTML + browser print dialog). */
export function buildReceiptHtml(bill: UnitBill, paperWidthMm: number = 58): string {
  const escape = (value: string) =>
    value
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  const period = (bill.period || "TAGIHAN").toUpperCase();

  return `<!doctype html>
<html lang="id">
<head>
<meta charset="utf-8" />
<title>Nota ${escape(bill.name)}</title>
<style>
  @page { size: ${paperWidthMm}mm auto; margin: 3mm 3mm 3mm 3mm; }
  * { box-sizing: border-box; }
  body { margin: 0; background: #eef0f2; font-family: "Segoe UI", Tahoma, sans-serif; }
  .sheet {
    width: ${paperWidthMm}mm; margin: 12px auto; padding: 3mm;
    background: #fff; color: #000; font-size: 11px; line-height: 1.35;
  }
  .center { text-align: center; }
  .big { font-size: 17px; font-weight: 800; letter-spacing: .4px; }
  .divider { border-top: 1px dashed #000; margin: 4px 0; }
  .double { border-top: 2px solid #000; margin: 4px 0; }
  .row { display: flex; justify-content: space-between; gap: 6px; }
  .muted { font-size: 10px; }
  .toolbar {
    position: sticky; top: 0; display: flex; gap: 8px; justify-content: center;
    padding: 10px; background: #1f2937; color: #fff;
  }
  .toolbar button {
    font: inherit; font-size: 13px; padding: 6px 14px; border: 0; border-radius: 6px;
    background: #2563eb; color: #fff; cursor: pointer;
  }
  .toolbar button.ghost { background: #4b5563; }
  @media print {
    body { background: #fff; }
    .toolbar { display: none; }
    .sheet { margin: 0; padding: 0; width: auto; }
  }
</style>
</head>
<body>
<div class="toolbar">
  <button onclick="window.print()">Cetak</button>
  <button class="ghost" onclick="window.chrome?.webview?.postMessage({type:'close'})">Tutup</button>
</div>
<div class="sheet">
  <div class="center">KOS MANYAR 3/51-53<br />NOTA TAGIHAN AIR</div>
  <div class="divider"></div>
  <div class="center big">${escape(period)}</div>
  <div class="divider"></div>
  <div>Nama Penghuni:</div>
  <div class="big">${escape(bill.name.toUpperCase())}</div>
  <div style="margin-top:6px">Pemakaian Air:</div>
  <div class="big">${escape(String(bill.usageM3))} m3</div>
  <div class="divider"></div>
  <div class="row"><span>Tagihan Air:</span><span>${escape(formatRupiah(bill.waterBill))}</span></div>
  <div class="row"><span>Uang Plastik Sampah:</span><span>${escape(formatRupiah(bill.garbageFee))}</span></div>
  <div class="double"></div>
  <div class="center">TOTAL TAGIHAN:</div>
  <div class="center big">${escape(formatRupiah(bill.total))}</div>
  <div class="divider"></div>
  <div class="center muted">Terima Kasih<br />Dicetak: ${escape(formatPrintDate())}</div>
</div>
</body>
</html>`;
}
