package com.gymapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingTest {

    private val delta = 0.001

    private fun price(
        base: Double,
        discount: Double,
        method: PaymentMethod,
        installmentCount: Int,
    ): Money = Pricing.finalPrice(
        basePrice = Money.ofMajor(base),
        discount = Money.ofMajor(discount),
        paymentType = method,
        installmentCount = installmentCount,
    )

    @Test
    fun `nakit odemede vade farki uygulanmaz`() {
        assertEquals(Money.ofMajor(1000.0), price(1000.0, 0.0, PaymentMethod.CASH, 12))
    }

    @Test
    fun `multisport odemede vade farki uygulanmaz`() {
        assertEquals(Money.ofMajor(1000.0), price(1000.0, 0.0, PaymentMethod.MULTISPORT, 6))
    }

    @Test
    fun `kartla tek cekimde vade farki yoktur`() {
        assertEquals(Money.ofMajor(1000.0), price(1000.0, 0.0, PaymentMethod.CARD, 1))
    }

    @Test
    fun `kartla 6 taksitte yuzde 10 vade farki eklenir`() {
        assertEquals(Money.ofMajor(1100.0), price(1000.0, 0.0, PaymentMethod.CARD, 6))
    }

    @Test
    fun `iskonto vade farkindan once dusulur`() {
        // (1000 - 200) * 1.10
        assertEquals(Money.ofMajor(880.0), price(1000.0, 200.0, PaymentMethod.CARD, 6))
    }

    @Test
    fun `tanimsiz taksit sayisi vade farki eklemez`() {
        assertEquals(Money.ofMajor(1000.0), price(1000.0, 0.0, PaymentMethod.CARD, 7))
    }

    // ─── Regresyon: negatif tutar üretilemez ────────────────────────────────

    @Test
    fun `negatif iskonto fiyati artiramaz`() {
        assertEquals(Money.ofMajor(1000.0), price(1000.0, -500.0, PaymentMethod.CASH, 1))
    }

    @Test
    fun `iskonto paket fiyatini asamaz`() {
        assertEquals(Money.ZERO, price(1000.0, 5000.0, PaymentMethod.CASH, 1))
    }

    /**
     * Regresyon: tutarlar `Double` iken kuruş kesirleri birikiyordu.
     * Kuruş tam sayı olduğu için sonuç tam bölünmeyen oranlarda da kararlı.
     */
    @Test
    fun `vade farki kurusa yuvarlanir ve sapma birikmez`() {
        // 333,33 * %3 = 9,9999 → 10,00 kuruş yuvarlaması
        assertEquals(Money(34_333), price(333.33, 0.0, PaymentMethod.CARD, 2))
    }

    // ─── Ekran önizlemesi ───────────────────────────────────────────────────

    @Test
    fun `onizleme gecersiz sayilarda firlatmaz`() {
        assertEquals(0.0, Pricing.previewPrice(Double.NaN, 0.0, PaymentMethod.CASH, 1), delta)
        assertEquals(1000.0, Pricing.previewPrice(1000.0, Double.NaN, PaymentMethod.CASH, 1), delta)
    }

    @Test
    fun `onizleme ile kaydedilen tutar ayni kuraldan gelir`() {
        val preview = Pricing.previewPrice(1000.0, 200.0, PaymentMethod.CARD, 6)
        val stored = price(1000.0, 200.0, PaymentMethod.CARD, 6)
        assertEquals(stored.asDouble, preview, delta)
    }

    @Test
    fun `taksit secenekleri vade farki tablosuyla ayni`() {
        assertEquals(listOf(1, 2, 3, 6, 9, 12), Pricing.installmentOptions)
        // Her seçenek için hesap bir sonuç üretmeli (tablo ile UI sapamaz).
        Pricing.installmentOptions.forEach { count ->
            assertTrue(price(100.0, 0.0, PaymentMethod.CARD, count) >= Money.ofMajor(100.0))
        }
    }

    // ─── Taksit normalizasyonu ──────────────────────────────────────────────

    @Test
    fun `taksit yalnizca kartli odemede gecerlidir`() {
        assertEquals(6, Pricing.normalizeInstallment(PaymentMethod.CARD, 6))
        assertEquals(1, Pricing.normalizeInstallment(PaymentMethod.CASH, 6))
        assertEquals(1, Pricing.normalizeInstallment(PaymentMethod.MULTISPORT, 6))
        // Tabloda olmayan taksit sayısı tek çekime düşer.
        assertEquals(1, Pricing.normalizeInstallment(PaymentMethod.CARD, 7))
    }
}
