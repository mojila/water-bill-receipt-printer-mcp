package com.kosmanyar.notaair.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosTest {

    private val ESC = '\u001B'

    private fun build(): ByteArray = EscPos.buildReceipt(
        ReceiptCore.computeReceipt("Mbak Oci", 17, 89000, "September 2026")
    )

    /** Renders control bytes as escape sequences so the payload can be asserted as text. */
    private fun ByteArray.toEscaped(): String = buildString {
        for (b in this@toEscaped) {
            when (b.toInt()) {
                0x1B -> append("\\e")
                0x1D -> append("\\g")
                0x0A -> append("\\n")
                0x00 -> append("\\0")
                else -> append(b.toInt().toChar())
            }
        }
    }

    @Test
    fun `payload starts with a printer reset`() {
        assertTrue(build().toEscaped().startsWith("\\e@"))
    }

    @Test
    fun `payload enables double size for the emphasised fields`() {
        val escaped = build().toEscaped()

        // The double-size command is GS ! 0x11 followed by the text it scales.
        assertTrue(escaped.contains(doubleSizeCommand() + "SEPTEMBER 2026"))
        assertTrue(escaped.contains(doubleSizeCommand() + "MBAK OCI"))
        assertTrue(escaped.contains(doubleSizeCommand() + "17 m3"))
        assertTrue(escaped.contains(doubleSizeCommand() + "Rp. 89.000"))
    }

    private fun doubleSizeCommand(): String = "\\g!${'\u0011'}"

    @Test
    fun `payload restores normal size after each emphasised field`() {
        val escaped = build().toEscaped()
        assertEquals(4, escaped.split(doubleSizeCommand()).size - 1)
        assertEquals(5, escaped.split("\\g!\\0").size - 1)
    }

    @Test
    fun `payload ends with feed and a partial cut`() {
        val escaped = build().toEscaped()
        assertTrue(escaped.endsWith("\\ed${'\u0004'}\\gV${'\u0001'}"))
    }

    @Test
    fun `payload contains QRIS info and breakdown lines`() {
        val escaped = build().toEscaped()
        assertTrue(escaped.contains("Tagihan Air:"))
        assertTrue(escaped.contains("Uang Plastik Sampah:"))
        assertTrue(escaped.contains("Rp. 8.498"))
        assertTrue(escaped.contains("TOTAL TAGIHAN:"))
        assertTrue(escaped.contains("SCAN QRIS UNTUK PEMBAYARAN"))
        assertTrue(escaped.contains("MOCH.AJI LAKSONO"))
        assertTrue(escaped.contains("ID1026637820527"))
    }

    @Test
    fun `reset command bytes are ESC and at sign`() {
        val bytes = build()
        assertEquals(ESC.code.toByte(), bytes[0])
        assertEquals('@'.code.toByte(), bytes[1])
    }
}
