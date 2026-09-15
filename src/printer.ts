import { exec, spawn } from "child_process";
import * as path from "path";
import * as fs from "fs";
import * as os from "os";
import { promisify } from "util";
import { fileURLToPath } from "url";

const execAsync = promisify(exec);

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const DEFAULT_PRINTER_NAME = process.env.PRINTER_NAME || "POS-58";

export interface PrinterStatus {
  name: string;
  isAvailable: boolean;
  port: string;
  driver: string;
  status: string;
  rawError?: string;
}

/**
 * Check whether the thermal printer is installed and available in Windows Spooler
 */
export async function checkPrinterStatus(printerName: string = DEFAULT_PRINTER_NAME): Promise<PrinterStatus> {
  const psCommand = `powershell -NoProfile -Command "Get-Printer -Name '${printerName}' -ErrorAction SilentlyContinue | Select-Object Name, PortName, DriverName, PrinterStatus | ConvertTo-Json"`;

  try {
    const { stdout, stderr } = await execAsync(psCommand);
    if (!stdout || stdout.trim() === "") {
      return {
        name: printerName,
        isAvailable: false,
        port: "Unknown",
        driver: "Unknown",
        status: "Printer tidak ditemukan di Windows. Pastikan driver terpasang dan nama printer sesuai.",
        rawError: stderr,
      };
    }

    const data = JSON.parse(stdout);
    return {
      name: data.Name || printerName,
      isAvailable: true,
      port: data.PortName || "Unknown",
      driver: data.DriverName || "Unknown",
      status: data.PrinterStatus || "Normal",
    };
  } catch (err: any) {
    return {
      name: printerName,
      isAvailable: false,
      port: "Unknown",
      driver: "Unknown",
      status: "Error memeriksa printer",
      rawError: err.message,
    };
  }
}

/**
 * Send raw binary ESC/POS data directly to Windows Spooler using print_raw.py
 */
export async function sendRawToPrinter(
  data: Buffer,
  printerName: string = DEFAULT_PRINTER_NAME
): Promise<{ success: boolean; message: string }> {
  // Path to print_raw.py
  // In dev: src/print_raw.py; In build: ../src/print_raw.py or build/print_raw.py
  let scriptPath = path.resolve(__dirname, "print_raw.py");
  if (!fs.existsSync(scriptPath)) {
    scriptPath = path.resolve(__dirname, "../src/print_raw.py");
  }

  if (!fs.existsSync(scriptPath)) {
    throw new Error(`Script printer '${scriptPath}' tidak ditemukan.`);
  }

  // Write temporary binary file
  const tempFilePath = path.join(
    os.tmpdir(),
    `receipt_${Date.now()}_${Math.random().toString(36).substring(7)}.bin`
  );
  await fs.promises.writeFile(tempFilePath, data);

  return new Promise((resolve, reject) => {
    const child = spawn("python", [scriptPath, printerName, tempFilePath], {
      windowsHide: true,
    });

    let stderr = "";
    let stdout = "";

    child.stdout.on("data", (chunk) => {
      stdout += chunk.toString();
    });

    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString();
    });

    child.on("close", async (code) => {
      // Clean up temp file
      try {
        if (fs.existsSync(tempFilePath)) {
          await fs.promises.unlink(tempFilePath);
        }
      } catch {
        // ignore unlink error
      }

      if (code === 0 && stdout.includes("SUCCESS")) {
        resolve({
          success: true,
          message: `Berhasil mengirim nota ke printer '${printerName}'.`,
        });
      } else {
        const errorMsg = stderr.trim() || stdout.trim() || `Process exited with code ${code}`;
        reject(
          new Error(`Gagal mencetak ke printer '${printerName}': ${errorMsg}`)
        );
      }
    });

    child.on("error", async (err) => {
      try {
        if (fs.existsSync(tempFilePath)) {
          await fs.promises.unlink(tempFilePath);
        }
      } catch {}
      reject(new Error(`Gagal menjalankan proses Python printer: ${err.message}`));
    });
  });
}
