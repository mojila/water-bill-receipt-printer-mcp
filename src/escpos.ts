import { UnitBill, formatRupiah } from "./data.js";

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

const LINE_WIDTH = 32; // 58mm standard width (32 chars)

export function padLine(left: string, right: string, width: number = LINE_WIDTH): string {
  const spaceNeeded = width - (left.length + right.length);
  if (spaceNeeded <= 0) {
    return left + " " + right;
  }
  return left + " ".repeat(spaceNeeded) + right;
}

export function centerText(text: string, width: number = LINE_WIDTH): string {
  if (text.length >= width) return text;
  const leftPad = Math.floor((width - text.length) / 2);
  return " ".repeat(leftPad) + text;
}

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
  const now = new Date();
  const printDate = `${String(now.getDate()).padStart(2, "0")}/${String(
    now.getMonth() + 1
  ).padStart(2, "0")}/${now.getFullYear()} ${String(now.getHours()).padStart(
    2,
    "0"
  )}:${String(now.getMinutes()).padStart(2, "0")}`;

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
  const now = new Date();
  const printDate = `${String(now.getDate()).padStart(2, "0")}/${String(
    now.getMonth() + 1
  ).padStart(2, "0")}/${now.getFullYear()} ${String(now.getHours()).padStart(
    2,
    "0"
  )}:${String(now.getMinutes()).padStart(2, "0")}`;

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
