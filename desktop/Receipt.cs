using System.Globalization;
using System.Text;
using System.Text.Json.Serialization;

namespace PrintReceiptWater.Desktop;

public sealed class UnitData
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public int UsageM3 { get; set; }
    public long Total { get; set; }
}

public sealed class SettingsData
{
    public string Period { get; set; } = "";
    public string Month { get; set; } = "";
    public int Year { get; set; }
    public string PrinterName { get; set; } = "POS-58";
    public long GarbageFee { get; set; } = ReceiptData.UangPlastikSampah;
}

public sealed class StoreData
{
    public int Version { get; set; } = 1;
    public SettingsData Settings { get; set; } = new();
    public List<UnitData> Units { get; set; } = new();
}

public sealed class ReceiptData
{
    public const long UangPlastikSampah = 8498;
    public const string DefaultPrinterName = "POS-58";
    public const int LineWidth = 32;

    public static readonly string[] Months =
    {
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    };

    public static readonly (string Name, int UsageM3, long Total)[] DefaultUnits =
    {
        ("Mbak Oci", 17, 89000),
        ("Pak Budi", 31, 138000),
        ("Mbak Ningsih", 31, 178000),
        ("Pak Giman", 57, 350000),
        ("Pak Sutaji", 32, 185000),
        ("Pak Sugik", 21, 72000),
        ("Bu Sutik", 21, 72000),
        ("Bu Warti", 22, 76000),
    };

    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public int UsageM3 { get; set; }
    public long GarbageFee { get; set; } = UangPlastikSampah;
    public long WaterBill => Total - GarbageFee;
    public long Total { get; set; }
    public string Period { get; set; } = "";

    public string TotalFormatted => FormatRupiah(Total);
    public string WaterBillFormatted => FormatRupiah(WaterBill);
    public string GarbageFeeFormatted => FormatRupiah(GarbageFee);

    [JsonIgnore]
    public string PeriodUpper => string.IsNullOrWhiteSpace(Period) ? "TAGIHAN" : Period.ToUpperInvariant();

    public static string FormatRupiah(long amount) =>
        "Rp. " + amount.ToString("N0", CultureInfo.GetCultureInfo("id-ID"));

    public static string MakePeriod(string month, int year)
    {
        var normalized = month?.Trim() ?? "";
        var index = Array.FindIndex(Months, m => string.Equals(m, normalized, StringComparison.OrdinalIgnoreCase));
        var name = index >= 0 ? Months[index] : normalized;
        return $"{name} {year}";
    }

    public static string DefaultPeriod() => MakePeriod(Months[DateTime.Now.Month - 1], DateTime.Now.Year);

    public static string Slugify(string name) =>
        new string(name.ToLowerInvariant().Select(c => char.IsLetterOrDigit(c) ? c : '-').ToArray());

    public static ReceiptData FromUnit(UnitData unit, SettingsData settings) => new()
    {
        Id = unit.Id,
        Name = unit.Name,
        UsageM3 = unit.UsageM3,
        Total = unit.Total,
        GarbageFee = settings.GarbageFee,
        Period = settings.Period,
    };
}

public static class ReceiptText
{
    public static string PadLine(string left, string right, int width = ReceiptData.LineWidth)
    {
        var spaceNeeded = width - (left.Length + right.Length);
        return spaceNeeded <= 0 ? left + " " + right : left + new string(' ', spaceNeeded) + right;
    }

    public static string Center(string text, int width = ReceiptData.LineWidth)
    {
        if (text.Length >= width) return text;
        var leftPad = (width - text.Length) / 2;
        return new string(' ', leftPad) + text;
    }

    public static string BuildPreview(ReceiptData bill)
    {
        var divider = new string('-', ReceiptData.LineWidth);
        var doubleDivider = new string('=', ReceiptData.LineWidth);
        return string.Join("\n", new[]
        {
            divider,
            Center("KOS MANYAR 3/51-53"),
            Center("NOTA TAGIHAN AIR"),
            divider,
            Center($"[ {bill.PeriodUpper} ]"),
            divider,
            "Nama Penghuni:",
            $">>> {bill.Name.ToUpperInvariant()} <<<  [FONT BESAR & BOLD]",
            "",
            "Pemakaian Air:",
            $">>> {bill.UsageM3} m3 <<<  [FONT BESAR & BOLD]",
            divider,
            PadLine("Tagihan Air:", bill.WaterBillFormatted),
            PadLine("Uang Plastik Sampah:", bill.GarbageFeeFormatted),
            doubleDivider,
            Center("TOTAL TAGIHAN:"),
            Center($">>> {bill.TotalFormatted} <<<"),
            Center("[FONT BESAR & BOLD]"),
            divider,
            Center("Terima Kasih"),
            Center($"Dicetak: {PrintDate()}"),
            divider,
        });
    }

    public static string PrintDate(DateTime? now = null)
    {
        var date = now ?? DateTime.Now;
        return $"{date:dd/MM/yyyy HH:mm}";
    }
}

/// <summary>
/// Builds raw ESC/POS bytes for the 58mm EPPOS EP5821 thermal printer.
/// </summary>
public static class EscPos
{
    private const char ESC = '\x1B';
    private const char GS = '\x1D';

    private static string Reset => $"{ESC}@";
    private static string AlignLeft => $"{ESC}a\x00";
    private static string AlignCenter => $"{ESC}a\x01";
    private static string BoldOn => $"{ESC}E\x01";
    private static string BoldOff => $"{ESC}E\x00";
    private static string DoubleSizeOn => $"{GS}!\x11";
    private static string NormalSize => $"{GS}!\x00";
    private static string PartialCut => $"{GS}V\x01";
    private static string FeedLines(int count) => $"{ESC}d{(char)count}";

    public static byte[] Build(ReceiptData bill)
    {
        var sb = new StringBuilder();
        var divider = new string('-', ReceiptData.LineWidth);

        sb.Append(Reset);

        sb.Append(AlignCenter).Append(BoldOn);
        sb.Append("KOS MANYAR 3/51-53\n");
        sb.Append("NOTA TAGIHAN AIR\n");
        sb.Append(BoldOff).Append(divider).Append('\n');

        sb.Append(AlignCenter).Append(BoldOn).Append(DoubleSizeOn);
        sb.Append(bill.PeriodUpper).Append('\n');
        sb.Append(NormalSize).Append(BoldOff).Append(divider).Append('\n');

        sb.Append(AlignLeft).Append("Nama Penghuni:\n");
        sb.Append(BoldOn).Append(DoubleSizeOn);
        sb.Append(bill.Name.ToUpperInvariant()).Append('\n');
        sb.Append(NormalSize).Append(BoldOff).Append('\n');

        sb.Append(AlignLeft).Append("Pemakaian Air:\n");
        sb.Append(BoldOn).Append(DoubleSizeOn);
        sb.Append(bill.UsageM3).Append(" m3\n");
        sb.Append(NormalSize).Append(BoldOff).Append(divider).Append('\n');

        sb.Append(AlignLeft).Append(NormalSize).Append(BoldOff);
        sb.Append(ReceiptText.PadLine("Tagihan Air:", bill.WaterBillFormatted)).Append('\n');
        sb.Append(ReceiptText.PadLine("Uang Plastik Sampah:", bill.GarbageFeeFormatted)).Append('\n');
        sb.Append(new string('=', ReceiptData.LineWidth)).Append('\n');

        sb.Append(AlignCenter).Append(BoldOn);
        sb.Append("TOTAL TAGIHAN:\n");
        sb.Append(DoubleSizeOn).Append(bill.TotalFormatted).Append('\n');
        sb.Append(NormalSize).Append(BoldOff).Append(divider).Append('\n');

        sb.Append(AlignCenter);
        sb.Append("Terima Kasih\n");
        sb.Append("Dicetak: ").Append(ReceiptText.PrintDate()).Append('\n');

        sb.Append(FeedLines(4));
        sb.Append(PartialCut);

        return Encoding.ASCII.GetBytes(sb.ToString());
    }
}
