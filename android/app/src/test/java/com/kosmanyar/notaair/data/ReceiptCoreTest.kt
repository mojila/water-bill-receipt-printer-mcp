package com.kosmanyar.notaair.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptCoreTest {

    private val septemberTwoThousandTwentySix = "September 2026"

    @Test
    fun `formatRupiah uses Indonesian thousands separators`() {
        assertEquals("Rp. 80.502", ReceiptCore.formatRupiah(80502))
        assertEquals("Rp. 8.498", ReceiptCore.formatRupiah(8498))
        assertEquals("Rp. 350.000", ReceiptCore.formatRupiah(350000))
        assertEquals("Rp. 0", ReceiptCore.formatRupiah(0))
    }

    @Test
    fun `makePeriod normalises the month name`() {
        assertEquals(septemberTwoThousandTwentySix, ReceiptCore.makePeriod("september", 2026))
        assertEquals(septemberTwoThousandTwentySix, ReceiptCore.makePeriod(" SEPTEMBER ", 2026))
        assertEquals("September 2026", ReceiptCore.makePeriod("September", 2026))
    }

    @Test
    fun `makePeriod keeps an unknown month label`() {
        assertEquals("BulanKe13 2026", ReceiptCore.makePeriod("BulanKe13", 2026))
    }

    @Test
    fun `water bill is total minus the garbage fee`() {
        val bill = ReceiptCore.computeReceipt("Mbak Oci", 17, 89000, septemberTwoThousandTwentySix)
        assertEquals(80502L, bill.waterBill)
        assertEquals(8498L, bill.garbageFee)
        assertEquals("Rp. 89.000", bill.totalFormatted)
    }

    @Test
    fun `slugify replaces non alphanumeric characters`() {
        assertEquals("mbak-oci", ReceiptCore.slugify("Mbak Oci"))
        assertEquals("bu-warti-", ReceiptCore.slugify("Bu Warti!"))
    }

    @Test
    fun `padLine pads to the 32 character paper width`() {
        val line = ReceiptCore.padLine("Tagihan Air:", "Rp. 80.502")
        assertEquals(ReceiptCore.LINE_WIDTH, line.length)
        assertTrue(line.startsWith("Tagihan Air:"))
        assertTrue(line.endsWith("Rp. 80.502"))
    }

    @Test
    fun `padLine falls back to a separator when text overflows`() {
        val line = ReceiptCore.padLine("Uang Plastik Sampah Yang Sangat Panjang:", "Rp. 8.498")
        assertTrue(line.contains(":"))
        assertTrue(line.endsWith("Rp. 8.498"))
    }

    @Test
    fun `centerText centres within the paper width`() {
        assertEquals("  AB", ReceiptCore.centerText("AB", 6))
        assertEquals("ABCDEFG", ReceiptCore.centerText("ABCDEFG", 6))
    }

    @Test
    fun `preview contains the receipt sections`() {
        val bill = ReceiptCore.computeReceipt("Mbak Oci", 17, 89000, septemberTwoThousandTwentySix)
        val preview = ReceiptCore.buildPreview(bill)

        assertTrue(preview.contains("KOS MANYAR 3/51-53"))
        assertTrue(preview.contains("NOTA TAGIHAN AIR"))
        assertTrue(preview.contains("SEPTEMBER 2026"))
        assertTrue(preview.contains("MBAK OCI"))
        assertTrue(preview.contains("17 m3"))
        assertTrue(preview.contains("Rp. 89.000"))
    }

    @Test
    fun `period upper falls back to TAGIHAN when blank`() {
        val bill = ReceiptCore.computeReceipt("Mbak Oci", 17, 89000, "")
        assertEquals("TAGIHAN", bill.periodUpper)
    }
}
