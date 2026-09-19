using System.Diagnostics;
using System.Reflection;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace PrintReceiptWater.Desktop;

public sealed class MainForm : Form
{
    private static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    private static readonly HashSet<string> VirtualHosts = new(StringComparer.OrdinalIgnoreCase)
    {
        "app.local", "printer.local"
    };

    private readonly WebView2 _webView = new() { Dock = DockStyle.Fill, DefaultBackgroundColor = Color.White };

    public MainForm()
    {
        Text = "Nota Air Kos Manyar 3/51-53 - EPPOS EP5821";
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(1120, 800);
        MinimumSize = new Size(880, 640);
        Icon = LoadIconFromAsset() ?? LoadAppIcon();

        Controls.Add(_webView);
        Load += async (_, _) => await InitializeWebViewAsync();
    }

    private static Icon? LoadAppIcon()
    {
        try { return Icon.ExtractAssociatedIcon(Application.ExecutablePath); }
        catch { return null; }
    }

    /// <summary>Draws the window/taskbar icon from assets\receipt.png so it matches the app artwork.</summary>
    private static Icon? LoadIconFromAsset()
    {
        try
        {
            var assetPath = Path.Combine(AppPaths.IconDirectory, "receipt.png");
            if (!File.Exists(assetPath)) return null;

            using var source = Image.FromFile(assetPath);
            using var bitmap = new Bitmap(256, 256);
            using (var graphics = Graphics.FromImage(bitmap))
            {
                graphics.CompositingMode = System.Drawing.Drawing2D.CompositingMode.SourceCopy;
                graphics.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.HighQuality;
                graphics.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.HighQuality;
                graphics.DrawImage(source, new Rectangle(0, 0, 256, 256));
            }

            var handle = bitmap.GetHicon();
            try { return (Icon)Icon.FromHandle(handle).Clone(); }
            finally { DestroyIcon(handle); }
        }
        catch { return null; }
    }

    [System.Runtime.InteropServices.DllImport("user32.dll", SetLastError = true)]
    private static extern bool DestroyIcon(IntPtr handle);

    /// <summary>
    /// Exercises the message handlers with the same JSON payloads the UI sends,
    /// so the bridge contract can be verified without driving the window.
    /// </summary>
    public void RunSelfTest()
    {
        void Send(string type, string json = "{}")
        {
            using var doc = JsonDocument.Parse(json);
            Dispatch(type, doc.RootElement);
            Console.WriteLine($"  -> {type} handled");
        }

        var store = Store.Load();
        Console.WriteLine($"period start   : {store.Settings.Period}");

        Send("saveSettings", "{\"month\":\"Oktober\",\"year\":2026,\"garbageFee\":8498}");
        Console.WriteLine($"period now     : {Store.Load().Settings.Period}");

        Send("saveUnit", "{\"id\":null,\"name\":\"Unit Uji\",\"usageM3\":12,\"total\":70000}");
        var added = Store.Load().Units.FirstOrDefault(u => u.Name == "Unit Uji");
        Console.WriteLine($"unit added     : {added?.Name} id={added?.Id} m3={added?.UsageM3} total={added?.Total}");

        if (added is not null)
        {
            Send("saveUnit", $"{{\"id\":\"{added.Id}\",\"name\":\"Unit Uji\",\"usageM3\":15,\"total\":80000}}");
            var updated = Store.Load().Units.FirstOrDefault(u => u.Id == added.Id);
            Console.WriteLine($"unit updated   : m3={updated?.UsageM3} total={updated?.Total}");

            Send("preview", $"{{\"id\":\"{added.Id}\"}}");

            Send("deleteUnit", $"{{\"id\":\"{added.Id}\",\"name\":\"Unit Uji\"}}");
            Console.WriteLine($"unit deleted   : still present={Store.Load().Units.Any(u => u.Id == added.Id)}");
        }

        Send("checkPrinter", "{\"printerName\":\"POS-58\"}");

        // Restore the original month/year so the test leaves the data as it found it.
        Send("saveSettings", $"{{\"month\":\"{store.Settings.Month}\",\"year\":{store.Settings.Year}}}");
        Console.WriteLine($"period restored: {Store.Load().Settings.Period}");
    }

    private async Task InitializeWebViewAsync()
    {
        try
        {
            var userDataFolder = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "PrintReceiptWater", "WebView2");
            Directory.CreateDirectory(userDataFolder);
            Directory.CreateDirectory(Store.DataDir);

            var environment = await CoreWebView2Environment.CreateAsync(userDataFolder: userDataFolder);
            await _webView.EnsureCoreWebView2Async(environment);

            var core = _webView.CoreWebView2;
            core.Settings.AreDevToolsEnabled = true;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.AreDefaultContextMenusEnabled = true;
            core.Settings.IsZoomControlEnabled = true;

            core.SetVirtualHostNameToFolderMapping(
                "app.local", AppPaths.AssetsDirectory, CoreWebView2HostResourceAccessKind.Allow);
            core.SetVirtualHostNameToFolderMapping(
                "printer.local", Store.DataDir, CoreWebView2HostResourceAccessKind.Allow);

            core.WebMessageReceived += OnWebMessageReceived;
            core.NewWindowRequested += (_, e) => e.Handled = true;
            core.NavigationStarting += OnNavigationStarting;

            core.Navigate("https://app.local/index.html");
        }
        catch (Exception ex)
        {
            var logPath = Path.Combine(Path.GetTempPath(), "PrintReceiptWater-error.log");
            try
            {
                File.WriteAllText(logPath, $"{DateTime.Now:u}\n{ex}\n");
            }
            catch
            {
                // ignore logging failure
            }

            MessageBox.Show(
                "Gagal menjalankan WebView2.\n\n" +
                "Pastikan WebView2 Runtime terpasang (https://developer.microsoft.com/microsoft-edge/webview2/).\n\n" +
                $"Detail: {ex.Message}\n\nLog: {logPath}",
                "Nota Air Kos", MessageBoxButtons.OK, MessageBoxIcon.Error);
            Close();
        }
    }

    private void OnNavigationStarting(object? sender, CoreWebView2NavigationStartingEventArgs e)
    {
        if (!Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri)) return;

        if (uri.Scheme is "https" && VirtualHosts.Contains(uri.Host)) return;
        if (uri.Scheme is "about" || uri.Scheme is "data" or "blob") return;

        e.Cancel = true;
        OpenExternal(uri.ToString());
    }

    private static void OpenExternal(string url)
    {
        try
        {
            Process.Start(new ProcessStartInfo { FileName = url, UseShellExecute = true });
        }
        catch
        {
            // Ignore failures opening the default browser.
        }
    }

    private void OnWebMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        string raw;
        try { raw = e.WebMessageAsJson; }
        catch { return; }

        JsonElement message;
        try { message = JsonDocument.Parse(raw).RootElement; }
        catch { return; }

        if (message.ValueKind != JsonValueKind.Object) return;
        if (!message.TryGetProperty("type", out var typeProperty)) return;

        var payload = message.TryGetProperty("payload", out var p) ? p : default;
        Dispatch(typeProperty.GetString(), payload);
    }

    /// <summary>Routes a UI message to its handler. Shared by the WebView2 bridge and self-test.</summary>
    private void Dispatch(string? type, JsonElement payload)
    {
        switch (type)
        {
            case "ready":
                Post("state", BuildState());
                break;
            case "saveSettings":
                HandleSaveSettings(payload);
                break;
            case "saveUnit":
                HandleSaveUnit(payload);
                break;
            case "deleteUnit":
                HandleDeleteUnit(payload);
                break;
            case "resetUnits":
                Store.Save(Store.DefaultStore());
                Post("state", BuildState(), "Data unit dikembalikan ke daftar bawaan.");
                break;
            case "print":
                HandlePrint(payload);
                break;
            case "printAll":
                HandlePrintAll(payload);
                break;
            case "preview":
                HandlePreview(payload);
                break;
            case "checkPrinter":
                HandleCheckPrinter(payload);
                break;
            case "openDataFolder":
                OpenDataFolder();
                break;
            case "close":
                Close();
                break;
        }
    }

    private void OpenDataFolder()
    {
        try
        {
            Directory.CreateDirectory(Store.DataDir);
            Process.Start(new ProcessStartInfo { FileName = Store.DataDir, UseShellExecute = true });
        }
        catch (Exception ex)
        {
            Post("toast", null, $"Gagal membuka folder data: {ex.Message}");
        }
    }

    private object BuildState()
    {
        var store = Store.Load();
        var bills = store.Units.Select(u => ReceiptData.FromUnit(u, store.Settings)).ToList();

        return new
        {
            settings = new
            {
                store.Settings.Period,
                store.Settings.Month,
                store.Settings.Year,
                store.Settings.PrinterName,
                store.Settings.GarbageFee,
            },
            months = ReceiptData.Months,
            units = store.Units.Select(u => new { u.Id, u.Name, u.UsageM3, u.Total }),
            bills = bills.Select(b => new
            {
                b.Id,
                b.Name,
                b.UsageM3,
                b.Total,
                b.WaterBill,
                b.GarbageFee,
                b.Period,
                totalFormatted = b.TotalFormatted,
                waterBillFormatted = b.WaterBillFormatted,
                garbageFeeFormatted = b.GarbageFeeFormatted,
            }),
            totals = new
            {
                units = bills.Count,
                usageM3 = bills.Sum(b => b.UsageM3),
                water = bills.Sum(b => b.WaterBill),
                garbage = bills.Sum(b => b.GarbageFee),
                grand = bills.Sum(b => b.Total),
                grandFormatted = ReceiptData.FormatRupiah(bills.Sum(b => b.Total)),
                waterFormatted = ReceiptData.FormatRupiah(bills.Sum(b => b.WaterBill)),
                garbageFormatted = ReceiptData.FormatRupiah(bills.Sum(b => b.GarbageFee)),
            },
            dataFile = Store.DataFile,
        };
    }

    private void HandleSaveSettings(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object) return;

        var store = Store.Load();
        var settings = store.Settings;

        if (TryGetProperty(payload, "month", out var month) && month.ValueKind == JsonValueKind.String)
        {
            settings.Month = month.GetString() ?? settings.Month;
        }

        if (TryGetProperty(payload, "year", out var year) && year.TryGetInt32(out var parsedYear))
        {
            settings.Year = parsedYear;
        }

        settings.Period = ReceiptData.MakePeriod(settings.Month, settings.Year);

        if (TryGetProperty(payload, "printerName", out var printer) && printer.ValueKind == JsonValueKind.String)
        {
            var value = printer.GetString();
            if (!string.IsNullOrWhiteSpace(value)) settings.PrinterName = value.Trim();
        }

        if (TryGetProperty(payload, "garbageFee", out var fee) && fee.TryGetInt64(out var parsedFee))
        {
            settings.GarbageFee = parsedFee;
        }

        Store.Save(store);
        Post("state", BuildState(), $"Periode tagihan: {settings.Period}");
    }

    private void HandleSaveUnit(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object) return;

        var name = GetString(payload, "name")?.Trim();
        if (string.IsNullOrWhiteSpace(name))
        {
            Post("toast", null, "Nama unit tidak boleh kosong.", true);
            return;
        }

        var usageM3 = Math.Max(0, GetInt(payload, "usageM3"));
        var total = Math.Max(0, GetLong(payload, "total"));
        var id = GetString(payload, "id");

        var store = Store.Load();
        var target = name.ToLowerInvariant();
        var index = id is { Length: > 0 }
            ? store.Units.FindIndex(u => u.Id == id)
            : store.Units.FindIndex(u => u.Name.ToLowerInvariant() == target);
        if (index < 0 && id is { Length: > 0 }) index = store.Units.FindIndex(u => u.Id == id);

        if (index >= 0)
        {
            store.Units[index] = new UnitData
            {
                Id = string.IsNullOrWhiteSpace(id) ? store.Units[index].Id : id,
                Name = name,
                UsageM3 = usageM3,
                Total = total,
            };
        }
        else
        {
            var baseId = ReceiptData.Slugify(name);
            var uniqueId = baseId;
            var suffix = 2;
            while (store.Units.Any(u => u.Id == uniqueId))
            {
                uniqueId = $"{baseId}-{suffix++}";
            }

            store.Units.Add(new UnitData { Id = uniqueId, Name = name, UsageM3 = usageM3, Total = total });
        }

        Store.Save(store);
        Post("state", BuildState(), $"Unit '{name}' tersimpan.");
    }

    private void HandleDeleteUnit(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object) return;

        var id = GetString(payload, "id");
        var name = GetString(payload, "name");
        if (string.IsNullOrWhiteSpace(id) && string.IsNullOrWhiteSpace(name)) return;

        var store = Store.Load();
        var before = store.Units.Count;
        store.Units.RemoveAll(u =>
            (!string.IsNullOrWhiteSpace(id) && u.Id == id) ||
            (!string.IsNullOrWhiteSpace(name) &&
             string.Equals(u.Name, name, StringComparison.OrdinalIgnoreCase)));

        if (store.Units.Count == before)
        {
            Post("toast", null, "Unit tidak ditemukan.", true);
            return;
        }

        Store.Save(store);
        Post("state", BuildState(), $"Unit '{name ?? id}' dihapus.");
    }

    private void HandlePreview(JsonElement payload)
    {
        var bill = ResolveBill(payload);
        if (bill is null)
        {
            Post("toast", null, "Pilih unit terlebih dahulu untuk melihat pratinjau.", true);
            return;
        }

        Post("preview", null, null, false, new
        {
            name = bill.Name,
            text = ReceiptText.BuildPreview(bill),
            url = "https://printer.local/receipt-preview.html",
        });
    }

    private void HandlePrint(JsonElement payload)
    {
        var bill = ResolveBill(payload);
        if (bill is null)
        {
            Post("toast", null, "Unit tidak ditemukan.", true);
            return;
        }

        var store = Store.Load();
        var target = GetString(payload, "printerName");
        var printer = string.IsNullOrWhiteSpace(target) ? store.Settings.PrinterName : target!.Trim();
        if (!string.Equals(printer, store.Settings.PrinterName, StringComparison.Ordinal))
        {
            store.Settings.PrinterName = printer;
            Store.Save(store);
        }

        var result = RawPrinter.Print(bill, printer);
        Post("printResult", null, result.Message, !result.Success, new
        {
            name = bill.Name,
            preview = result.Preview,
            success = result.Success,
            printer,
        });

        if (result.Success) Post("state", BuildState());
    }

    private void HandlePrintAll(JsonElement payload)
    {
        var store = Store.Load();
        var target = GetString(payload, "printerName");
        var printer = string.IsNullOrWhiteSpace(target) ? store.Settings.PrinterName : target!.Trim();
        var delayMs = Math.Clamp(GetInt(payload, "delayMs") is var d && d > 0 ? d : 1500, 200, 15000);

        var bills = store.Units.Select(u => ReceiptData.FromUnit(u, store.Settings)).ToList();
        if (bills.Count == 0)
        {
            Post("toast", null, "Belum ada unit yang bisa dicetak.", true);
            return;
        }

        Task.Run(async () =>
        {
            var results = new List<object>();
            for (var i = 0; i < bills.Count; i++)
            {
                var bill = bills[i];
                var result = RawPrinter.Print(bill, printer);
                results.Add(new
                {
                    index = i + 1,
                    total = bills.Count,
                    name = bill.Name,
                    success = result.Success,
                    message = result.Message,
                });

                Post("printProgress", null, null, false, new { index = i + 1, total = bills.Count, name = bill.Name });

                if (i < bills.Count - 1 && delayMs > 0)
                {
                    await Task.Delay(delayMs);
                }
            }

            var failed = results.Count(r => !(bool)r.GetType().GetProperty("success")!.GetValue(r)!);            Post("printAllDone", null, $"Selesai: {results.Count - failed} berhasil, {failed} gagal.", failed > 0, new
            {
                results,
                failed,
                total = results.Count,
            });
        });
    }

    private void HandleCheckPrinter(JsonElement payload)
    {
        var store = Store.Load();
        var target = GetString(payload, "printerName");
        var printer = string.IsNullOrWhiteSpace(target) ? store.Settings.PrinterName : target!.Trim();

        var (available, port, driver, status) = RawPrinter.Check(printer);
        Post("printerStatus", null, null, !available, new { printer, available, port, driver, status });
    }

    private static ReceiptData? ResolveBill(JsonElement payload)
    {
        var store = Store.Load();
        var id = GetString(payload, "id");
        var name = GetString(payload, "name");
        var usageM3 = GetProperty(payload, "usageM3");
        var total = GetProperty(payload, "total");

        // Ad-hoc custom receipt entered directly in the form.
        if (!string.IsNullOrWhiteSpace(name) && usageM3.ValueKind == JsonValueKind.Number &&
            total.ValueKind == JsonValueKind.Number && string.IsNullOrWhiteSpace(id))
        {
            return new ReceiptData
            {
                Id = ReceiptData.Slugify(name!.Trim()),
                Name = name.Trim(),
                UsageM3 = Math.Max(0, GetInt(payload, "usageM3")),
                Total = Math.Max(0, GetLong(payload, "total")),
                GarbageFee = store.Settings.GarbageFee,
                Period = string.IsNullOrWhiteSpace(GetString(payload, "period"))
                    ? store.Settings.Period
                    : ReceiptData.MakePeriod(GetString(payload, "period")!, store.Settings.Year),
            };
        }

        if (!string.IsNullOrWhiteSpace(id))
        {
            var unit = store.Units.FirstOrDefault(u => u.Id == id);
            if (unit is not null) return ReceiptData.FromUnit(unit, store.Settings);
        }

        if (!string.IsNullOrWhiteSpace(name))
        {
            var unit = store.Units.FirstOrDefault(u =>
                string.Equals(u.Name, name!.Trim(), StringComparison.OrdinalIgnoreCase));
            if (unit is not null) return ReceiptData.FromUnit(unit, store.Settings);
        }

        return null;
    }

    private static JsonElement GetProperty(JsonElement element, string name)
        => element.ValueKind == JsonValueKind.Object && element.TryGetProperty(name, out var value)
            ? value
            : default;

    private static string? GetString(JsonElement element, string name)
    {
        var value = GetProperty(element, name);
        return value.ValueKind == JsonValueKind.String ? value.GetString() : null;
    }

    private static int GetInt(JsonElement element, string name)
    {
        var value = GetProperty(element, name);
        if (value.ValueKind == JsonValueKind.Number && value.TryGetInt32(out var result)) return result;
        if (value.ValueKind == JsonValueKind.String && int.TryParse(value.GetString(), out var parsed)) return parsed;
        return 0;
    }

    private static long GetLong(JsonElement element, string name)
    {
        var value = GetProperty(element, name);
        if (value.ValueKind == JsonValueKind.Number && value.TryGetInt64(out var result)) return result;
        if (value.ValueKind == JsonValueKind.String && long.TryParse(value.GetString(), out var parsed)) return parsed;
        return 0;
    }

    private static bool TryGetProperty(JsonElement element, string name, out JsonElement value)
    {
        if (element.ValueKind == JsonValueKind.Object && element.TryGetProperty(name, out value)) return true;
        value = default;
        return false;
    }

    private void Post(string type, object? payload = null, string? message = null, bool isError = false, object? extra = null)
    {
        var envelope = new Dictionary<string, object?>
        {
            ["type"] = type,
            ["payload"] = payload,
            ["message"] = message,
            ["isError"] = isError,
        };

        if (extra is not null) envelope["extra"] = extra;

        var json = JsonSerializer.Serialize(envelope, Json);
        if (InvokeRequired)
        {
            BeginInvoke(() => _webView.CoreWebView2?.PostWebMessageAsJson(json));
        }
        else
        {
            _webView.CoreWebView2?.PostWebMessageAsJson(json);
        }
    }
}

internal static class AppPaths
{
    public static string AssetsDirectory { get; } = ResolveAssetsDirectory();

    /// <summary>Directory holding the app artwork (receipt.png), used for the window icon.</summary>
    public static string IconDirectory { get; } = ResolveIconDirectory();

    private static string ResolveIconDirectory()
    {
        var baseDir = AppContext.BaseDirectory;

        var copied = Path.Combine(baseDir, "assets");
        if (File.Exists(Path.Combine(copied, "receipt.png"))) return copied;

        var repoDir = Path.GetFullPath(Path.Combine(baseDir, "..", "..", "..", "..", "assets"));
        if (File.Exists(Path.Combine(repoDir, "receipt.png"))) return repoDir;

        return copied;
    }

    private static string ResolveAssetsDirectory()
    {
        var baseDir = AppContext.BaseDirectory;

        var embedded = Path.Combine(baseDir, "assets");

        // Always refresh the extracted assets from the embedded resources so a stale
        // on-disk copy (e.g. an old index.html/app.css/app.js from a previous build)
        // can never be served. TryExtractEmbeddedAssets overwrites existing files.
        TryExtractEmbeddedAssets(embedded);
        if (File.Exists(Path.Combine(embedded, "index.html"))) return embedded;

        var manifestDir = Path.Combine(baseDir, "desktop", "assets");
        if (File.Exists(Path.Combine(manifestDir, "index.html"))) return manifestDir;

        var repoDir = Path.GetFullPath(Path.Combine(baseDir, "..", "..", "..", "..", "desktop", "assets"));
        if (File.Exists(Path.Combine(repoDir, "index.html"))) return repoDir;

        return embedded;
    }

        /// <summary>Extracts assets embedded in the assembly so the UI is always available.</summary>
        private static void TryExtractEmbeddedAssets(string targetDirectory)
    {
        try
        {
            var assembly = Assembly.GetExecutingAssembly();
            Directory.CreateDirectory(targetDirectory);

            foreach (var name in assembly.GetManifestResourceNames())
            {
                var extension = Path.GetExtension(name);
                if (extension is not (".html" or ".js" or ".css")) continue;

                using var stream = assembly.GetManifestResourceStream(name);
                if (stream is null) continue;

                // Embedded resource names look like "PrintReceiptWater.assets.index.html".
                var segments = name.Split('.');
                var fileName = segments.Length >= 2
                    ? segments[segments.Length - 2] + "." + segments[segments.Length - 1]
                    : name;

                using var file = File.Create(Path.Combine(targetDirectory, fileName));
                stream.CopyTo(file);
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Gagal mengekstrak aset UI: {ex.Message}");
        }
    }
}
