using System.Text.Json;

namespace PrintReceiptWater.Desktop;

/// <summary>
/// Persists units and settings to data/units.json, the same file the MCP server reads,
/// so the desktop app and the MCP tools always agree on the data.
/// The JSON is written in camelCase to match the Node.js implementation.
/// </summary>
public static class Store
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = true,
    };

    public static string DataDir { get; } = ResolveDataDir();
    public static string DataFile { get; } = Path.Combine(DataDir, "units.json");

    private static string ResolveDataDir()
    {
        var env = Environment.GetEnvironmentVariable("RECEIPT_DATA_DIR");
        if (!string.IsNullOrWhiteSpace(env)) return env;

        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            if (File.Exists(Path.Combine(dir.FullName, "package.json")))
            {
                return Path.Combine(dir.FullName, "data");
            }
            dir = dir.Parent;
        }
        return Path.Combine(AppContext.BaseDirectory, "data");
    }

    public static SettingsData DefaultSettings() => new()
    {
        Month = ReceiptData.Months[DateTime.Now.Month - 1],
        Year = DateTime.Now.Year,
        Period = ReceiptData.DefaultPeriod(),
        PrinterName = Environment.GetEnvironmentVariable("PRINTER_NAME") ?? ReceiptData.DefaultPrinterName,
        GarbageFee = ReceiptData.UangPlastikSampah,
    };

    public static StoreData DefaultStore() => new()
    {
        Version = 1,
        Settings = DefaultSettings(),
        Units = ReceiptData.DefaultUnits.Select(u => new UnitData
        {
            Id = ReceiptData.Slugify(u.Name),
            Name = u.Name,
            UsageM3 = u.UsageM3,
            Total = u.Total,
        }).ToList(),
    };

    public static StoreData Load()
    {
        try
        {
            if (File.Exists(DataFile))
            {
                var json = File.ReadAllText(DataFile);
                var parsed = JsonSerializer.Deserialize<StoreData>(json, JsonOptions);
                if (parsed is not null) return Normalize(parsed);
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Gagal membaca '{DataFile}': {ex.Message}");
        }
        return DefaultStore();
    }

    public static StoreData Save(StoreData store)
    {
        var normalized = Normalize(store);
        Directory.CreateDirectory(DataDir);
        File.WriteAllText(DataFile, JsonSerializer.Serialize(normalized, JsonOptions) + "\n");
        return normalized;
    }

    public static StoreData Normalize(StoreData store)
    {
        var fallback = DefaultStore();
        store.Settings ??= fallback.Settings;

        var settings = store.Settings;
        settings.Month = string.IsNullOrWhiteSpace(settings.Month) ? fallback.Settings.Month : settings.Month.Trim();
        settings.Year = settings.Year > 1900 ? settings.Year : fallback.Settings.Year;
        // Month + year are the source of truth; the period label is always derived from them.
        settings.Period = ReceiptData.MakePeriod(settings.Month, settings.Year);
        settings.PrinterName = string.IsNullOrWhiteSpace(settings.PrinterName)
            ? fallback.Settings.PrinterName
            : settings.PrinterName.Trim();
        settings.GarbageFee = settings.GarbageFee > 0 ? settings.GarbageFee : ReceiptData.UangPlastikSampah;

        var units = (store.Units ?? new List<UnitData>())
            .Where(u => u is not null && !string.IsNullOrWhiteSpace(u.Name))
            .Select((u, i) => new UnitData
            {
                Id = string.IsNullOrWhiteSpace(u.Id) ? $"{ReceiptData.Slugify(u.Name.Trim())}-{i + 1}" : u.Id,
                Name = u.Name.Trim(),
                UsageM3 = Math.Max(0, u.UsageM3),
                Total = Math.Max(0, u.Total),
            })
            .ToList();

        store.Units = units.Count > 0 ? units : fallback.Units;
        store.Version = 1;
        return store;
    }
}
