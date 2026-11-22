package com.kosmanyar.notaair.data

/** ESC/POS command bytes for the 58mm EPPOS EP5821 thermal printer (port of `src/escpos.ts`). */
object EscPos {

    private const val ESC = 0x1B
    private const val GS = 0x1D

    private val RESET = byteArrayOf(ESC.toByte(), '@'.code.toByte())
    private val ALIGN_LEFT = byteArrayOf(ESC.toByte(), 'a'.code.toByte(), 0x00)
    private val ALIGN_CENTER = byteArrayOf(ESC.toByte(), 'a'.code.toByte(), 0x01)
    private val BOLD_ON = byteArrayOf(ESC.toByte(), 'E'.code.toByte(), 0x01)
    private val BOLD_OFF = byteArrayOf(ESC.toByte(), 'E'.code.toByte(), 0x00)
    private val DOUBLE_SIZE_ON = byteArrayOf(GS.toByte(), '!'.code.toByte(), 0x11)
    private val NORMAL_SIZE = byteArrayOf(GS.toByte(), '!'.code.toByte(), 0x00)
    private val PARTIAL_CUT = byteArrayOf(GS.toByte(), 'V'.code.toByte(), 0x01)

    private fun feedLines(count: Int) = byteArrayOf(ESC.toByte(), 'd'.code.toByte(), count.toByte())

    private fun String.ascii(): ByteArray = this.toByteArray(Charsets.US_ASCII)

    /**
     * Build the raw ESC/POS payload for one receipt. The command sequence matches
     * `buildEscposReceipt` in the TypeScript core byte for byte.
     */
    fun buildReceipt(bill: ReceiptData): ByteArray {
        val out = ArrayList<Byte>(512)
        val divider = "-".repeat(ReceiptCore.LINE_WIDTH)

        fun add(bytes: ByteArray) {
            for (b in bytes) out.add(b)
        }

        fun add(text: String) = add(text.ascii())

        add(RESET)

        add(ALIGN_CENTER)
        add(BOLD_ON)
        add("KOS MANYAR 3/51-53\n")
        add("NOTA TAGIHAN AIR\n")
        add(BOLD_OFF)
        add("$divider\n")

        add(ALIGN_CENTER)
        add(BOLD_ON)
        add(DOUBLE_SIZE_ON)
        add("${bill.periodUpper}\n")
        add(NORMAL_SIZE)
        add(BOLD_OFF)
        add("$divider\n")

        add(ALIGN_LEFT)
        add("Nama Penghuni:\n")
        add(BOLD_ON)
        add(DOUBLE_SIZE_ON)
        add("${bill.name.uppercase()}\n")
        add(NORMAL_SIZE)
        add(BOLD_OFF)
        add("\n")

        add(ALIGN_LEFT)
        add("Pemakaian Air:\n")
        add(BOLD_ON)
        add(DOUBLE_SIZE_ON)
        add("${bill.usageM3} m3\n")
        add(NORMAL_SIZE)
        add(BOLD_OFF)
        add("$divider\n")

        add(ALIGN_LEFT)
        add(NORMAL_SIZE)
        add(BOLD_OFF)
        add(ReceiptCore.padLine("Tagihan Air:", bill.waterBillFormatted) + "\n")
        add(ReceiptCore.padLine("Uang Plastik Sampah:", bill.garbageFeeFormatted) + "\n")
        add("${"=".repeat(ReceiptCore.LINE_WIDTH)}\n")

        add(ALIGN_CENTER)
        add(BOLD_ON)
        add("TOTAL TAGIHAN:\n")
        add(DOUBLE_SIZE_ON)
        add("${bill.totalFormatted}\n")
        add(NORMAL_SIZE)
        add(BOLD_OFF)
        add("$divider\n")

        add(ALIGN_CENTER)
        add(BOLD_ON)
        add("SCAN QRIS UNTUK PEMBAYARAN\n")
        add(BOLD_OFF)
        add("MOCH.AJI LAKSONO\n")
        add("NMID: ID1026637820527\n")

        add(QrisData.ESCPOS_BYTES)
        add("\n")

        add(ALIGN_CENTER)
        add("$divider\n")
        add("Terima Kasih\n")
        add("Dicetak: ${ReceiptCore.printDate()}\n")

        add(feedLines(4))
        add(PARTIAL_CUT)

        return out.toByteArray()
    }
}
