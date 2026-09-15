namespace PrintReceiptWater.Desktop;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        if (args.Contains("--diagnostics"))
        {
            RunDiagnostics();
            return;
        }

        if (args.Contains("--self-test"))
        {
            ApplicationConfiguration.Initialize();
            Console.WriteLine("Bridge self-test (data dir: " + Store.DataDir + ")");
            using var form = new MainForm();
            form.RunSelfTest();
            Console.WriteLine("Done.");
            return;
        }

        ApplicationConfiguration.Initialize();
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) => ShowError(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => ShowError(e.ExceptionObject as Exception);

        Application.Run(new MainForm());
    }

    private static void RunDiagnostics()
    {
        Console.WriteLine($"BaseDirectory : {AppContext.BaseDirectory}");
        Console.WriteLine($"DataDir       : {Store.DataDir}");
        Console.WriteLine($"DataFile      : {Store.DataFile}");
        Console.WriteLine($"AssetsDir     : {AppPaths.AssetsDirectory}");
        Console.WriteLine($"Assets present: {File.Exists(Path.Combine(AppPaths.AssetsDirectory, "index.html"))}");
        Console.WriteLine($"print_raw.py  : {File.Exists(Path.Combine(AppContext.BaseDirectory, "print_raw.py"))}");

        var store = Store.Load();
        Console.WriteLine($"Settings      : {store.Settings.Period} | {store.Settings.PrinterName} | {store.Settings.GarbageFee}");
        Console.WriteLine($"Units         : {store.Units.Count}");
        foreach (var unit in store.Units.Take(3))
        {
            var bill = ReceiptData.FromUnit(unit, store.Settings);
            Console.WriteLine($"  - {bill.Name}: {bill.UsageM3} m3, air {bill.WaterBillFormatted}, total {bill.TotalFormatted}");
        }

        var first = store.Units.FirstOrDefault();
        if (first is not null)
        {
            var bytes = EscPos.Build(ReceiptData.FromUnit(first, store.Settings));
            Console.WriteLine($"ESC/POS bytes : {bytes.Length}");
        }
    }

    private static void ShowError(Exception? ex)
    {
        MessageBox.Show(
            ex?.ToString() ?? "Terjadi kesalahan yang tidak diketahui.",
            "Nota Air Kos - Error",
            MessageBoxButtons.OK,
            MessageBoxIcon.Error);
    }
}
