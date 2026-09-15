using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

namespace PrintReceiptWater.Desktop;

public sealed record PrintResult(bool Success, string Message, string Preview);

/// <summary>
/// Sends raw ESC/POS bytes to the Windows spooler.
/// Primary path: embedded print_raw.py (same approach as the MCP server).
/// Fallback path: Win32 SpoolAPI P/Invoke, used when Python is unavailable.
/// </summary>
public static class RawPrinter
{
    public static PrintResult Print(ReceiptData bill, string printerName)
    {
        var preview = ReceiptText.BuildPreview(bill);
        var payload = EscPos.Build(bill);
        var target = string.IsNullOrWhiteSpace(printerName) ? ReceiptData.DefaultPrinterName : printerName.Trim();

        var (ok, error) = TryPythonSpool(payload, target);
        var via = "print_raw.py";

        if (!ok)
        {
            var (ok2, error2) = TryWin32Spool(payload, target);
            via = "Win32 SpoolAPI";
            if (!ok2) return new PrintResult(false, error2 ?? error ?? "Gagal mencetak.", preview);
        }

        return new PrintResult(true, $"Berhasil mengirim nota ke printer '{target}' ({via}).", preview);
    }

    public static (bool Available, string Port, string Driver, string Status) Check(string printerName)
    {
        var target = string.IsNullOrWhiteSpace(printerName) ? ReceiptData.DefaultPrinterName : printerName.Trim();

        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = "powershell",
                Arguments =
                    $"-NoProfile -Command \"Get-Printer -Name '{target.Replace("'", "''")}' -ErrorAction SilentlyContinue | " +
                    "Select-Object Name,PortName,DriverName,PrinterStatus | ConvertTo-Json\"",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
            },
        };

        try
        {
            process.Start();
            var stdout = process.StandardOutput.ReadToEnd();
            var stderr = process.StandardError.ReadToEnd();
            process.WaitForExit(10000);

            if (string.IsNullOrWhiteSpace(stdout))
            {
                return (false, "Unknown", "Unknown",
                    "Printer tidak ditemukan di Windows. Pastikan driver terpasang dan nama printer sesuai.");
            }

            using var doc = System.Text.Json.JsonDocument.Parse(stdout);
            var root = doc.RootElement;
            return (true,
                GetString(root, "PortName"),
                GetString(root, "DriverName"),
                GetString(root, "PrinterStatus") is { Length: > 0 } s ? s : "Normal");
        }
        catch (Exception ex)
        {
            return (false, "Unknown", "Unknown", $"Error memeriksa printer: {ex.Message}");
        }
    }

    private static string GetString(System.Text.Json.JsonElement root, string name)
        => root.TryGetProperty(name, out var value) && value.ValueKind != System.Text.Json.JsonValueKind.Null
            ? value.ToString()
            : "Unknown";

    private static (bool Ok, string? Error) TryPythonSpool(byte[] payload, string printerName)
    {
        var scriptPath = Path.Combine(AppContext.BaseDirectory, "print_raw.py");
        if (!File.Exists(scriptPath))
        {
            scriptPath = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "src", "print_raw.py"));
        }
        if (!File.Exists(scriptPath)) return (false, "Script print_raw.py tidak ditemukan.");

        var tempFile = Path.Combine(Path.GetTempPath(), $"receipt_{Guid.NewGuid():N}.bin");
        try
        {
            File.WriteAllBytes(tempFile, payload);

            var psi = new ProcessStartInfo
            {
                FileName = "python",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
            };
            psi.ArgumentList.Add(scriptPath);
            psi.ArgumentList.Add(printerName);
            psi.ArgumentList.Add(tempFile);

            using var process = Process.Start(psi);
            if (process is null) return (false, "Gagal menjalankan proses Python.");

            var stdout = process.StandardOutput.ReadToEnd();
            var stderr = process.StandardError.ReadToEnd();
            if (!process.WaitForExit(20000))
            {
                try { process.Kill(true); } catch { /* ignore */ }
                return (false, "Proses Python timeout.");
            }

            if (process.ExitCode == 0 && stdout.Contains("SUCCESS", StringComparison.Ordinal))
            {
                return (true, null);
            }

            var message = !string.IsNullOrWhiteSpace(stderr) ? stderr.Trim() : stdout.Trim();
            return (false, string.IsNullOrWhiteSpace(message)
                ? $"Python keluar dengan kode {process.ExitCode}."
                : message);
        }
        catch (Exception ex)
        {
            return (false, $"Gagal menjalankan Python: {ex.Message}");
        }
        finally
        {
            try { if (File.Exists(tempFile)) File.Delete(tempFile); } catch { /* ignore */ }
        }
    }

    private static (bool Ok, string? Error) TryWin32Spool(byte[] payload, string printerName)
        => OperatingSystem.IsWindows()
            ? Win32Spool.Write(printerName, "Nota Air Kos Manyar", payload)
            : (false, "SpoolAPI hanya tersedia di Windows.");
}

internal static class Win32Spool
{
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct DOC_INFO_1
    {
        [MarshalAs(UnmanagedType.LPWStr)] public string pDocName;
        [MarshalAs(UnmanagedType.LPWStr)] public string? pOutputFile;
        [MarshalAs(UnmanagedType.LPWStr)] public string? pDatatype;
    }

    [DllImport("winspool.drv", SetLastError = true, CharSet = CharSet.Unicode, EntryPoint = "OpenPrinterW")]
    private static extern bool OpenPrinter(string pPrinterName, out IntPtr phPrinter, IntPtr pDefault);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool ClosePrinter(IntPtr hPrinter);

    [DllImport("winspool.drv", SetLastError = true, CharSet = CharSet.Unicode, EntryPoint = "StartDocPrinterW")]
    private static extern int StartDocPrinter(IntPtr hPrinter, int level, ref DOC_INFO_1 pDocInfo);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool EndDocPrinter(IntPtr hPrinter);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool StartPagePrinter(IntPtr hPrinter);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool EndPagePrinter(IntPtr hPrinter);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool WritePrinter(IntPtr hPrinter, IntPtr pBytes, int dwCount, out int dwWritten);

    public static (bool Ok, string? Error) Write(string printerName, string docName, byte[] data)
    {
        if (!OpenPrinter(printerName, out var handle, IntPtr.Zero))
        {
            return (false, $"Gagal membuka printer '{printerName}' (Windows error {Marshal.GetLastWin32Error()}).");
        }

        try
        {
            var docInfo = new DOC_INFO_1 { pDocName = docName, pOutputFile = null, pDatatype = "RAW" };
            if (StartDocPrinter(handle, 1, ref docInfo) == 0)
            {
                return (false, $"Gagal memulai dokumen cetak (Windows error {Marshal.GetLastWin32Error()}).");
            }

            try
            {
                if (!StartPagePrinter(handle))
                {
                    return (false, $"Gagal memulai halaman cetak (Windows error {Marshal.GetLastWin32Error()}).");
                }

                var unmanaged = Marshal.AllocCoTaskMem(data.Length);
                try
                {
                    Marshal.Copy(data, 0, unmanaged, data.Length);
                    if (!WritePrinter(handle, unmanaged, data.Length, out var written) || written != data.Length)
                    {
                        return (false, $"Gagal menulis ke printer (Windows error {Marshal.GetLastWin32Error()}).");
                    }
                }
                finally
                {
                    Marshal.FreeCoTaskMem(unmanaged);
                }

                EndPagePrinter(handle);
            }
            finally
            {
                EndDocPrinter(handle);
            }
        }
        finally
        {
            ClosePrinter(handle);
        }

        return (true, null);
    }
}
