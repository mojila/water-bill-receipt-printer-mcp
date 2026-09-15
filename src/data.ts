export interface UnitBill {
  id: string;
  name: string;
  usageM3: number;
  garbageFee: number;
  waterBill: number;
  total: number;
  period?: string;
}

export const UANG_PLASTIK_SAMPAH = 8498;

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
  const months = [
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
  ];
  const now = new Date();
  return `${months[now.getMonth()]} ${now.getFullYear()}`;
}

export function formatRupiah(amount: number): string {
  return "Rp. " + amount.toLocaleString("id-ID");
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
    id: name.toLowerCase().replace(/[^a-z0-9]/g, "-"),
    name,
    usageM3,
    garbageFee,
    waterBill,
    total,
    period: period || getDefaultPeriod(),
  };
}

export function getAllDefaultReceipts(period?: string): UnitBill[] {
  const billPeriod = period || getDefaultPeriod();
  return DEFAULT_UNITS.map((u) =>
    computeReceipt(u.name, u.usageM3, u.total, billPeriod)
  );
}

export function findReceiptByUnitName(query: string, period?: string): UnitBill | undefined {
  const q = query.toLowerCase().trim();
  const all = getAllDefaultReceipts(period);
  return all.find((item) => item.name.toLowerCase().includes(q));
}
