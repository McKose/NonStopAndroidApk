package com.gymapp.domain

import com.gymapp.data.local.entity.PaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingTest {

    private val delta = 0.001

    @Test
    fun `nakit odemede vade farki uygulanmaz`() {
        val price = Pricing.finalPrice(1000.0, 0.0, PaymentType.CASH, installmentCount = 12)
        assertEquals(1000.0, price, delta)
    }

    @Test
    fun `multisport odemede vade farki uygulanmaz`() {
        val price = Pricing.finalPrice(1000.0, 0.0, PaymentType.MULTISPORT, installmentCount = 6)
        assertEquals(1000.0, price, delta)
    }

    @Test
    fun `kartla tek cekimde vade farki yoktur`() {
        val price = Pricing.finalPrice(1000.0, 0.0, PaymentType.CARD, installmentCount = 1)
        assertEquals(1000.0, price, delta)
    }

    @Test
    fun `kartla 6 taksitte yuzde 10 vade farki eklenir`() {
        val price = Pricing.finalPrice(1000.0, 0.0, PaymentType.CARD, installmentCount = 6)
        assertEquals(1100.0, price, delta)
    }

    @Test
    fun `iskonto vade farkindan once dusulur`() {
        // (1000 - 200) * 1.10
        val price = Pricing.finalPrice(1000.0, 200.0, PaymentType.CARD, installmentCount = 6)
        assertEquals(880.0, price, delta)
    }

    @Test
    fun `tanimsiz taksit sayisi vade farki eklemez`() {
        val price = Pricing.finalPrice(1000.0, 0.0, PaymentType.CARD, installmentCount = 7)
        assertEquals(1000.0, price, delta)
    }

    // ─── Regresyon: negatif tutar üretilemez ────────────────────────────────

    @Test
    fun `negatif iskonto fiyati artiramaz`() {
        val price = Pricing.finalPrice(1000.0, -500.0, PaymentType.CASH, installmentCount = 1)
        assertEquals(1000.0, price, delta)
    }

    @Test
    fun `iskonto paket fiyatini asamaz`() {
        val price = Pricing.finalPrice(1000.0, 5000.0, PaymentType.CASH, installmentCount = 1)
        assertEquals(0.0, price, delta)
    }

    @Test
    fun `gecersiz sayilar sifira cekilir`() {
        assertEquals(0.0, Pricing.finalPrice(Double.NaN, 0.0, PaymentType.CASH, 1), delta)
        assertEquals(1000.0, Pricing.finalPrice(1000.0, Double.NaN, PaymentType.CASH, 1), delta)
    }

    @Test
    fun `taksit secenekleri vade farki tablosuyla ayni`() {
        assertEquals(listOf(1, 2, 3, 6, 9, 12), Pricing.installmentOptions)
        // Her seçenek için hesap bir sonuç üretmeli (tablo ile UI sapamaz).
        Pricing.installmentOptions.forEach { count ->
            assertTrue(Pricing.finalPrice(100.0, 0.0, PaymentType.CARD, count) >= 100.0)
        }
    }
}
