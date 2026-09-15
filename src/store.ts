import * as fs from "fs";
import * as path from "path";
import { fileURLToPath } from "url";

import {
  DEFAULT_UNITS,
  MONTHS,
  UnitBill,
  computeReceipt,
  getDefaultPeriod,
  makePeriod,
  slugify,
  UANG_PLASTIK_SAMPAH,
} from "./core.js";

export interface StoredUnit {
  id: string;
  name: string;
  usageM3: number;
  total: number;
}

export interface Settings {
  period: string;
  month: string;
  year: number;
  printerName: string;
  garbageFee: number;
}

export interface StoreData {
  version: number;
  settings: Settings;
  units: StoredUnit[];
}

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function resolveDataDir(): string {
  if (process.env.RECEIPT_DATA_DIR) {
    return process.env.RECEIPT_DATA_DIR;
  }
  const cwdData = path.resolve(process.cwd(), "data");
  const buildDir = path.resolve(__dirname, "..");
  if (fs.existsSync(path.join(buildDir, "package.json"))) {
    return path.resolve(buildDir, "data");
  }
  return cwdData;
}

export const DATA_DIR = resolveDataDir();
export const DATA_FILE = path.join(DATA_DIR, "units.json");

export function getDefaultSettings(): Settings {
  const now = new Date();
  return {
    period: getDefaultPeriod(),
    month: MONTHS[now.getMonth()],
    year: now.getFullYear(),
    printerName: process.env.PRINTER_NAME || "POS-58",
    garbageFee: UANG_PLASTIK_SAMPAH,
  };
}

export function getDefaultStore(): StoreData {
  const settings = getDefaultSettings();
  return {
    version: 1,
    settings,
    units: DEFAULT_UNITS.map((u) => ({
      id: slugify(u.name),
      name: u.name,
      usageM3: u.usageM3,
      total: u.total,
    })),
  };
}

function normalizeUnit(raw: any, index: number): StoredUnit | undefined {
  if (!raw || typeof raw !== "object") return undefined;
  const name = String(raw.name ?? "").trim();
  if (!name) return undefined;
  const usageM3 = Number(raw.usageM3);
  const total = Number(raw.total);
  return {
    id: String(raw.id || slugify(name) || `unit-${index + 1}`),
    name,
    usageM3: Number.isFinite(usageM3) ? usageM3 : 0,
    total: Number.isFinite(total) ? total : 0,
  };
}

export function normalizeStore(raw: any): StoreData {
  const fallback = getDefaultStore();
  if (!raw || typeof raw !== "object") return fallback;

  const rawSettings = raw.settings && typeof raw.settings === "object" ? raw.settings : {};
  const year = Number(rawSettings.year);
  const month = String(rawSettings.month || fallback.settings.month);
  const settings: Settings = {
    month,
    year: Number.isFinite(year) && year > 1900 ? Math.trunc(year) : fallback.settings.year,
    period: "",
    printerName: String(rawSettings.printerName || fallback.settings.printerName),
    garbageFee: Number.isFinite(Number(rawSettings.garbageFee))
      ? Number(rawSettings.garbageFee)
      : UANG_PLASTIK_SAMPAH,
  };
  // Month + year are the source of truth; the period label is always derived from them.
  settings.period = makePeriod(settings.month, settings.year);

  const units = Array.isArray(raw.units)
    ? raw.units.map(normalizeUnit).filter((u: StoredUnit | undefined): u is StoredUnit => !!u)
    : [];

  return {
    version: 1,
    settings,
    units: units.length > 0 ? units : fallback.units,
  };
}

export function loadStore(): StoreData {
  try {
    if (fs.existsSync(DATA_FILE)) {
      const parsed = JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
      return normalizeStore(parsed);
    }
  } catch (err) {
    console.error(`Gagal membaca '${DATA_FILE}': ${(err as Error).message}`);
  }
  return getDefaultStore();
}

export function saveStore(store: StoreData): StoreData {
  const normalized = normalizeStore(store);
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.writeFileSync(DATA_FILE, JSON.stringify(normalized, null, 2) + "\n", "utf8");
  return normalized;
}

export function getUnits(): StoredUnit[] {
  return loadStore().units;
}

export function setUnits(units: StoredUnit[]): StoreData {
  const store = loadStore();
  store.units = Array.isArray(units) ? units : store.units;
  return saveStore(store);
}

export function getSettings(): Settings {
  return loadStore().settings;
}

export function setSettings(patch: Partial<Settings>): StoreData {
  const store = loadStore();
  const merged: Settings = { ...store.settings, ...patch } as Settings;
  const year = Number(merged.year);
  merged.year = Number.isFinite(year) && year > 1900 ? Math.trunc(year) : store.settings.year;
  merged.month = String(merged.month || store.settings.month);
  merged.period = makePeriod(merged.month, merged.year);
  merged.garbageFee = Number.isFinite(Number(merged.garbageFee))
    ? Number(merged.garbageFee)
    : store.settings.garbageFee;
  merged.printerName = String(merged.printerName || store.settings.printerName);
  store.settings = merged;
  return saveStore(store);
}

export function getAllReceipts(store: StoreData = loadStore()): UnitBill[] {
  return store.units.map((u) =>
    computeReceipt(u.name, u.usageM3, u.total, store.settings.period, store.settings.garbageFee)
  );
}

export function findReceipt(query: string, store: StoreData = loadStore()): UnitBill | undefined {
  const q = query.toLowerCase().trim();
  const bills = getAllReceipts(store);
  return (
    bills.find((b) => b.name.toLowerCase() === q) ||
    bills.find((b) => b.id === q) ||
    bills.find((b) => b.name.toLowerCase().includes(q))
  );
}
