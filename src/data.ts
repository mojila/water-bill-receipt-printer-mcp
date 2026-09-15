export {
  UANG_PLASTIK_SAMPAH,
  MONTHS,
  DEFAULT_UNITS,
  LINE_WIDTH,
  getDefaultPeriod,
  makePeriod,
  formatRupiah,
  computeReceipt,
  slugify,
  padLine,
  centerText,
} from "./core.js";

export type { UnitBill } from "./core.js";

import { UnitBill } from "./core.js";
import { getAllReceipts, findReceipt, getSettings } from "./store.js";

export function getAllDefaultReceipts(period?: string): UnitBill[] {
  return getAllReceipts().map((bill) => (period ? { ...bill, period } : bill));
}

export function findReceiptByUnitName(query: string, period?: string): UnitBill | undefined {
  const bill = findReceipt(query);
  if (!bill) return undefined;
  return period ? { ...bill, period } : bill;
}

export const DEFAULT_PRINTER_NAME = getSettings().printerName;
