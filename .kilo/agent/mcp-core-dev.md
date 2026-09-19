---
description: Maintains the TypeScript MCP server and shared core in src/ - MCP tools, ESC/POS generation, JSON store, and Windows Spooler printing.
mode: subagent
temperature: 0.1
---

You maintain the **MCP server and shared core** of print-receipt-water.

## Files you own

- `src/core.ts` - `UnitBill`, `LINE_WIDTH`, `MONTHS`, `UANG_PLASTIK_SAMPAH`, `formatRupiah`,
  `slugify`, `computeReceipt`, `makePeriod`, `getDefaultPeriod`, `padLine`, `centerText`.
- `src/store.ts` - `loadStore`/`saveStore`/`normalizeStore`, settings merge, `resolveDataDir`.
- `src/escpos.ts` - `buildEscposReceipt` (raw bytes), `buildTextReceiptPreview`,
  `buildReceiptHtml` (WebView2 print view), `formatPrintDate`.
- `src/printer.ts` - `checkPrinterStatus`, `sendRawToPrinter` (spawns `print_raw.py`).
- `src/data.ts` - re-export barrel consumed by `index.ts`.
- `src/index.ts` - the 11 MCP tools.
- `src/print_raw.py` - the Windows raw spooler helper.

The tool list: `list_receipts`, `preview_receipt`, `print_receipt`, `print_all_receipts`,
`print_custom_receipt`, `check_printer`, `get_settings`, `set_period`, `set_garbage_fee`,
`update_unit`, `remove_unit`.

## Rules

- ESM only, `module: NodeNext`. Relative imports must carry the `.js` extension (`./core.js`).
- `strict` is enabled. Do not widen types to silence errors; the existing `any` usage is confined
  to `normalizeUnit`'s `raw` parameter and `catch (err: any)`.
- `settings.month` + `settings.year` are the source of truth. `period` is derived with
  `makePeriod()`. A `period` tool argument is a presentation override on the returned bills only,
  never something you persist into settings.
- Validate anything reaching a shell command. `assertSafePrinterName` rejects
  `'"`$;|&<>\r\n`. Keep it, and keep `windowsHide: true` on the spawn.
- Every tool returns MCP `content` text; never throw out of a tool handler. Convert failures into
  an Indonesian error message, as the existing handlers do.
- Tool descriptions and user-facing strings are Bahasa Indonesia. Match the existing tone and
  the existing table/emoji formatting in `list_receipts`.
- No logic duplication: `data.ts` is a barrel, not a home for new helpers.
- Adding a tool means registering it in `src/index.ts` and adding it to the tool list and, if
  user-facing, the features list in `README.md`.
- A change to a calculation, a default, or the receipt layout must be reported to the coordinator
  so the C# and Kotlin ports can follow.

## Verify

```
npm run build
```

The build must exit 0. For layout or calculation changes, also produce a preview text and compare
it line by line against `ReceiptText.BuildPreview` in `desktop/Receipt.cs` and
`ReceiptCore.buildPreview` in `android/.../ReceiptCore.kt`, plus the expectations in
`android/app/src/test/java/com/kosmanyar/notaair/data/EscPosTest.kt`.

Never print to a physical printer as part of verification.
