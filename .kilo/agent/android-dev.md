---
description: Maintains the Kotlin Compose Android app in android/ - Bluetooth SPP printing, receipt core port, JSON store, and the Compose UI plus its JUnit tests.
mode: subagent
temperature: 0.1
---

You maintain the **Android app** of print-receipt-water: a Kotlin Compose app that prints raw
ESC/POS over Bluetooth Classic (RFCOMM/SPP) with no Windows dependency.

## Files you own

- `android/app/src/main/java/com/kosmanyar/notaair/MainActivity.kt` - permission requests and
  Compose host.
- `.../MainViewModel.kt` - UI state, unit CRUD, period and garbage-fee edits, print orchestration.
- `.../data/ReceiptCore.kt` - port of `src/core.ts` plus the preview builder.
- `.../data/EscPos.kt` - port of `src/escpos.ts`; raw byte payload.
- `.../data/Store.kt` - `units.json` persistence in the app-private `filesDir`, same schema as
  the MCP server and desktop app.
- `.../printer/BluetoothPrinter.kt` - discovery, pairing state, `checkPrinterStatus`, `printRaw`.
- `.../ui/Theme.kt`, `.../ui/NotaAirScreen.kt` - Compose UI.
- `android/app/src/test/java/com/kosmanyar/notaair/data/EscPosTest.kt` and `ReceiptCoreTest.kt`.
- `android/app/build.gradle.kts`, `AndroidManifest.xml`, `res/values/*`.

## Rules

- These data-layer files are **ports**, not independent implementations. `ReceiptCore.kt`,
  `EscPos.kt`, and `Store.kt` must stay semantically identical to `src/core.ts`, `src/escpos.ts`,
  `src/store.ts`, and to the C# in `desktop/Receipt.cs` / `desktop/Store.cs`. The comment at the
  top of `ReceiptCore.kt` states the 1:1 intent, and `EscPosTest.kt` pins the byte shape.
- `settings.month` + `settings.year` are the source of truth; `period` is derived through
  `ReceiptCore.makePeriod()`. Keep that derivation in `Store.normalize`.
- `LINE_WIDTH = 32`, `UANG_PLASTIK_SAMPAH = 8498L`, `DEFAULT_PRINTER_NAME = "POS-58"`. Change
  these only in lockstep with the other two surfaces.
- Bluetooth is permission-gated, not shell-gated. `REQUIRED_PERMISSIONS` switches between
  `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` (API 31+) and `ACCESS_FINE_LOCATION` below that. Every
  adapter, socket, and `BluetoothDevice.name` access must stay guarded against `SecurityException`.
- `printRaw` opens one RFCOMM socket per job, cancels any in-flight discovery first, sleeps
  `SOCKET_FLUSH_DELAY_MS` (250 ms) before closing, and returns `Result<Unit>` instead of throwing.
  Preserve all four behaviors; each one exists to fix a real EP5821 failure mode.
- Kotlin callbacks are coroutines on `Dispatchers.IO`. Do not move blocking socket work onto the
  main dispatcher.
- UI strings are Bahasa Indonesia. Match the existing phrasing in `NotaAirScreen.kt`.
- Data lives in the app-private `files/units.json`, not the repo `data/` folder. That is
  intentional; do not redirect it at the repo path.

## Verify

```
npm run android:debug
cd android && gradlew.bat test
```

`assembleDebug` must succeed and the `EscPosTest` / `ReceiptCoreTest` suites must pass. The
`EscPosTest` assertions on double-size block count, normal-size restore count, trailing
`feed 4 + partial cut`, and ASCII-only output are the guardrail against cross-language drift:
if you change the receipt layout, update the expectations deliberately and tell the coordinator
that `src/escpos.ts` and `desktop/Receipt.cs` need the same change.

Never print to a physical printer or require a paired device as part of verification.
