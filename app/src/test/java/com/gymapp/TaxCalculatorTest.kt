package com.gymapp

import com.gymapp.data.local.entity.TransactionEntity
import com.gymapp.domain.tax.TaxCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TaxCalculatorTest {

    private val eps = 0.01

    @Test
    fun `vat matches user example 15000 kart+MS = 3000 KDV`() {
        val vat = TaxCalculator.vatFor(15_000.0)
        assertEquals(3000.0, vat, eps)
    }

    @Test
    fun `income tax brackets boundaries match 2026 schedule`() {
        // Dilim sınırları
        assertEquals(0.0, TaxCalculator.incomeTaxFor(0.0), eps)
        assertEquals(28_500.0, TaxCalculator.incomeTaxFor(190_000.0), eps)
        assertEquals(70_500.0, TaxCalculator.incomeTaxFor(400_000.0), eps)
        assertEquals(232_500.0, TaxCalculator.incomeTaxFor(1_000_000.0), eps)
        assertEquals(687_500.0, TaxCalculator.incomeTaxFor(2_300_000.0), eps)
    }

    @Test
    fun `income tax middle of bracket`() {
        // 300k: 28500 + (300000-190000)*0.20 = 28500 + 22000 = 50500
        assertEquals(50_500.0, TaxCalculator.incomeTaxFor(300_000.0), eps)
        // 3M: 687500 + (3000000-2300000)*0.40 = 687500 + 280000 = 967500
        assertEquals(967_500.0, TaxCalculator.incomeTaxFor(3_000_000.0), eps)
    }

    @Test
    fun `quarterly cumulative bracket step-up`() {
        // Q1 ve Q2 toplamı 190k'yı geçmemiş, Q3'te aşıyor — dilim geçişi doğru mu
        val tz = TimeZone.getTimeZone("Europe/Istanbul")
        val year = 2026
        fun msOf(month: Int, day: Int): Long {
            val c = Calendar.getInstance(tz).apply {
                clear(); set(year, month, day, 12, 0, 0); set(Calendar.MILLISECOND, 0)
            }
            return c.timeInMillis
        }
        val tx = listOf(
            TransactionEntity(amount = 50_000.0, type = "INCOME", category = "MEMBERSHIP",
                description = "", date = msOf(1, 15), paymentMethod = "CARD"),
            TransactionEntity(amount = 50_000.0, type = "INCOME", category = "MEMBERSHIP",
                description = "", date = msOf(4, 15), paymentMethod = "CARD"),
            // Q3: 120k → kümülatif 220k, 190k üstü dilime girdi
            TransactionEntity(amount = 120_000.0, type = "INCOME", category = "MEMBERSHIP",
                description = "", date = msOf(7, 15), paymentMethod = "MULTISPORT"),
            TransactionEntity(amount = 80_000.0, type = "INCOME", category = "MEMBERSHIP",
                description = "", date = msOf(10, 15), paymentMethod = "CARD")
        )

        val result = TaxCalculator.computeYear(year, tx, tz = tz)
        assertEquals(4, result.size)
        // Q1 kümülatif 50k → 7500
        assertEquals(7_500.0, result[0].cumulativeIncomeTax, eps)
        assertEquals(7_500.0, result[0].quarterIncomeTax, eps)
        // Q2 kümülatif 100k → 15000; o çeyrek payı 7500
        assertEquals(15_000.0, result[1].cumulativeIncomeTax, eps)
        assertEquals(7_500.0, result[1].quarterIncomeTax, eps)
        // Q3 kümülatif 220k → 28500 + (220k-190k)*0.20 = 28500 + 6000 = 34500
        assertEquals(34_500.0, result[2].cumulativeIncomeTax, eps)
        assertEquals(34_500.0 - 15_000.0, result[2].quarterIncomeTax, eps)
        // Q4 kümülatif 300k → 28500 + (300k-190k)*0.20 = 28500 + 22000 = 50500
        assertEquals(50_500.0, result[3].cumulativeIncomeTax, eps)
        assertEquals(50_500.0 - 34_500.0, result[3].quarterIncomeTax, eps)

        // Yıllık toplam vergi = son çeyreğin kümülatifi
        val totalIncomeTax = result.sumOf { it.quarterIncomeTax }
        assertEquals(50_500.0, totalIncomeTax, eps)

        // Yıllık KDV = 300k * 0.20 = 60000
        val totalVat = result.sumOf { it.vat }
        assertEquals(60_000.0, totalVat, eps)
    }

    @Test
    fun `pending and cash transactions are excluded`() {
        val tz = TimeZone.getTimeZone("Europe/Istanbul")
        val year = 2026
        val c = Calendar.getInstance(tz).apply {
            clear(); set(year, 1, 15, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val ts = c.timeInMillis
        val tx = listOf(
            TransactionEntity(amount = 10_000.0, type = "INCOME", category = "x",
                description = "", date = ts, paymentMethod = "CASH"), // nakit hariç
            TransactionEntity(amount = 5_000.0, type = "INCOME", category = "x",
                description = "", date = ts, paymentMethod = "CARD", isPending = true), // bekleyen hariç
            TransactionEntity(amount = 20_000.0, type = "INCOME", category = "x",
                description = "", date = ts, paymentMethod = "CARD") // sadece bu sayılır
        )
        val r = TaxCalculator.computeYear(year, tx, tz = tz)
        assertEquals(20_000.0, r[0].cumulativeTaxable, eps)
        assertEquals(4_000.0, r[0].vat, eps)
    }
}
