package com.kosmanyar.notaair.data

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Domain model and receipt formatting, ported 1:1 from `src/core.ts` and
 * `src/escpos.ts` so the Android app produces byte-identical ESC/POS output
 * to the MCP server and the Windows desktop app.
 */
object ReceiptCore {

    const val LINE_WIDTH = 32
    const val UANG_PLASTIK_SAMPAH = 8498L
    const val DEFAULT_PRINTER_NAME = "POS-58"

    val MONTHS = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    val DEFAULT_UNITS = listOf(
        DefaultUnit("Mbak Oci", 17, 89000L),
        DefaultUnit("Pak Budi", 31, 138000L),
        DefaultUnit("Mbak Ningsih", 31, 178000L),
        DefaultUnit("Pak Giman", 57, 350000L),
        DefaultUnit("Pak Sutaji", 32, 185000L),
        DefaultUnit("Pak Sugik", 21, 72000L),
        DefaultUnit("Bu Sutik", 21, 72000L),
        DefaultUnit("Bu Warti", 22, 76000L)
    )

    private val printDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun defaultPeriod(): String {
        val now = LocalDate.now()
        return makePeriod(MONTHS[now.monthValue - 1], now.year)
    }

    fun makePeriod(month: String, year: Int): String {
        val normalized = month.trim()
        val index = MONTHS.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        val name = if (index >= 0) MONTHS[index] else normalized
        return "$name $year"
    }

    fun formatRupiah(amount: Long): String =
        "Rp. " + NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).format(amount)

    fun slugify(name: String): String =
        name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")

    fun padLine(left: String, right: String, width: Int = LINE_WIDTH): String {
        val spaceNeeded = width - (left.length + right.length)
        return if (spaceNeeded <= 0) "$left $right" else left + " ".repeat(spaceNeeded) + right
    }

    fun centerText(text: String, width: Int = LINE_WIDTH): String {
        if (text.length >= width) return text
        return " ".repeat((width - text.length) / 2) + text
    }

    fun printDate(date: LocalDateTime = LocalDateTime.now()): String = date.format(printDateFormatter)

    fun computeReceipt(
        name: String,
        usageM3: Int,
        total: Long,
        period: String,
        garbageFee: Long = UANG_PLASTIK_SAMPAH
    ): ReceiptData = ReceiptData(
        id = slugify(name),
        name = name,
        usageM3 = usageM3,
        garbageFee = garbageFee,
        total = total,
        period = period
    )

    /** Human-readable 58mm preview, mirrors `buildTextReceiptPreview`. */
    fun buildPreview(bill: ReceiptData): String {
        val divider = "-".repeat(LINE_WIDTH)
        val doubleDivider = "=".repeat(LINE_WIDTH)
        return listOf(
            divider,
            centerText("KOS MANYAR 3/51-53"),
            centerText("NOTA TAGIHAN AIR"),
            divider,
            centerText("[ ${bill.periodUpper} ]"),
            divider,
            "Nama Penghuni:",
            ">>> ${bill.name.uppercase()} <<<  [FONT BESAR & BOLD]",
            "",
            "Pemakaian Air:",
            ">>> ${bill.usageM3} m3 <<<  [FONT BESAR & BOLD]",
            divider,
            padLine("Tagihan Air:", formatRupiah(bill.waterBill)),
            padLine("Uang Plastik Sampah:", formatRupiah(bill.garbageFee)),
            doubleDivider,
            centerText("TOTAL TAGIHAN:"),
            centerText(">>> ${formatRupiah(bill.total)} <<<"),
            centerText("[FONT BESAR & BOLD]"),
            divider,
            centerText("SCAN QRIS UNTUK PEMBAYARAN"),
            centerText("MOCH.AJI LAKSONO"),
            "",
            centerText("[ QR CODE QRIS ]"),
            "",
            divider,
            centerText("Terima Kasih"),
            centerText("Dicetak: ${printDate()}"),
            divider
        ).joinToString("\n")
    }
}

data class DefaultUnit(val name: String, val usageM3: Int, val total: Long)

data class ReceiptData(
    val id: String,
    val name: String,
    val usageM3: Int,
    val garbageFee: Long,
    val total: Long,
    val period: String
) {
    val waterBill: Long get() = total - garbageFee
    val totalFormatted: String get() = ReceiptCore.formatRupiah(total)
    val waterBillFormatted: String get() = ReceiptCore.formatRupiah(waterBill)
    val garbageFeeFormatted: String get() = ReceiptCore.formatRupiah(garbageFee)
    val periodUpper: String get() = period.ifBlank { "TAGIHAN" }.uppercase()
}
