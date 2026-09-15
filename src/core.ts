export interface UnitBill {
  id: string;
  name: string;
  usageM3: number;
  garbageFee: number;
  waterBill: number;
  total: number;
  period?: string;
}

export const LINE_WIDTH = 32; // 58mm standard width (32 chars)
export const UANG_PLASTIK_SAMPAH = 8498;

export const MONTHS = [
  "Januari",
  "Februari",
  "Maret",
  "April",
  "Mei",
  "Juni",
  "Juli",
  "Agustus",
  "September",
  "Oktober",
  "November",
  "Desember",
];

export const DEFAULT_UNITS: Array<{ name: string; usageM3: number; total: number }> = [
  { name: "Mbak Oci", usageM3: 17, total: 89000 },
  { name: "Pak Budi", usageM3: 31, total: 138000 },
  { name: "Mbak Ningsih", usageM3: 31, total: 178000 },
  { name: "Pak Giman", usageM3: 57, total: 350000 },
  { name: "Pak Sutaji", usageM3: 32, total: 185000 },
  { name: "Pak Sugik", usageM3: 21, total: 72000 },
  { name: "Bu Sutik", usageM3: 21, total: 72000 },
  { name: "Bu Warti", usageM3: 22, total: 76000 },
];

export function getDefaultPeriod(): string {
  const now = new Date();
  return `${MONTHS[now.getMonth()]} ${now.getFullYear()}`;
}

export function makePeriod(month: string | number, year: number): string {
  const idx =
    typeof month === "number"
      ? month
      : MONTHS.findIndex((m) => m.toLowerCase() === String(month).toLowerCase().trim());
  const name = idx >= 0 && idx < 12 ? MONTHS[idx] : String(month);
  return `${name} ${year}`;
}

export function formatRupiah(amount: number): string {
  return "Rp. " + amount.toLocaleString("id-ID");
}

export function slugify(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]/g, "-");
}

export function computeReceipt(
  name: string,
  usageM3: number,
  total: number,
  period?: string,
  garbageFee: number = UANG_PLASTIK_SAMPAH
): UnitBill {
  const waterBill = total - garbageFee;
  return {
    id: slugify(name),
    name,
    usageM3,
    garbageFee,
    waterBill,
    total,
    period: period || getDefaultPeriod(),
  };
}

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
