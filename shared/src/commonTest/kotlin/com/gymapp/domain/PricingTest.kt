package com.gymapp.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class PricingTest {

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

    // ─── Kırılım: ekranın gösterdiği kalemler ───────────────────────────────
    //
    // Fiyat kartı bu kalemleri tek tek yazıyor. Önceden yalnızca toplam
    // hesaplanıyor, kalemleri ekran kendisi üretiyordu; testler de yalnızca
    // toplamı sınıyordu. Aradaki boşluk gerçek bir hataya yol açtı: iskonto
    // satırı kullanıcının yazdığı ham değeri basıyor, toplam ise kırpılmış
    // değerle hesaplanıyordu.

    @Test
    fun `kalemlerin toplami odenecek tutari verir`() {
        val k = Pricing.breakdown(
            basePrice = Money.ofMajor(1000.0),
            discount = Money.ofMajor(200.0),
            paymentType = PaymentMethod.CARD,
            installmentCount = 6,
        )
        assertEquals(Money.ofMajor(1000.0), k.basePrice)
        assertEquals(Money.ofMajor(200.0), k.discount)
        assertEquals(Money.ofMajor(800.0), k.net)
        assertEquals(Money.ofMajor(80.0), k.surcharge)
        // Kalemler toplamı ile gösterilen toplam ayrışamaz.
        assertEquals(k.net + k.surcharge, k.total)
        assertEquals(Money.ofMajor(880.0), k.total)
    }

    @Test
    fun `kirilim ile kaydedilen tutar ayni hesaptan gelir`() {
        Pricing.installmentOptions.forEach { taksit ->
            listOf(PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.MULTISPORT).forEach { yontem ->
                val k = Pricing.breakdown(Money.ofMajor(777.77), Money.ofMajor(33.33), yontem, taksit)
                assertEquals(price(777.77, 33.33, yontem, taksit), k.total)
            }
        }
    }

    /**
     * Regresyon: iskonto paket fiyatını aşınca kart "1.000 − 5.000 = 0" gibi
     * kendi içinde tutarsız bir aritmetik gösteriyordu. Kırpma duruyor ama
     * kırpıldığı **görülebilir** — ekran bunu yazmak için kullanıyor.
     */
    @Test
    fun `asiri iskonto kirpilir ve kirpildigi bildirilir`() {
        val yazilan = Money.ofMajor(5000.0)
        val k = Pricing.breakdown(Money.ofMajor(1000.0), yazilan, PaymentMethod.CASH, 1)

        assertEquals(Money.ofMajor(1000.0), k.discount)
        assertEquals(Money.ZERO, k.total)
        assertTrue(k.discountWasCapped(yazilan))
    }

    @Test
    fun `kirpilmayan iskonto bildirilmez`() {
        val yazilan = Money.ofMajor(200.0)
        val k = Pricing.breakdown(Money.ofMajor(1000.0), yazilan, PaymentMethod.CASH, 1)

        assertEquals(yazilan, k.discount)
        assertFalse(k.discountWasCapped(yazilan))
    }

    /** Negatif iskonto sıfıra çekilir; "kırpıldı" uyarısı da çıkmaz. */
    @Test
    fun `negatif iskonto kirpildi sayilmaz`() {
        val yazilan = Money.ofMajor(-500.0)
        val k = Pricing.breakdown(Money.ofMajor(1000.0), yazilan, PaymentMethod.CASH, 1)

        assertEquals(Money.ZERO, k.discount)
        assertEquals(Money.ofMajor(1000.0), k.total)
        assertFalse(k.discountWasCapped(yazilan))
    }

    @Test
    fun `vade farki yalnizca kartli taksitte gorunur`() {
        val nakit = Pricing.breakdown(Money.ofMajor(1000.0), Money.ZERO, PaymentMethod.CASH, 12)
        assertEquals(Rate.ZERO, nakit.surchargeRate)
        assertEquals(Money.ZERO, nakit.surcharge)

        val tekCekim = Pricing.breakdown(Money.ofMajor(1000.0), Money.ZERO, PaymentMethod.CARD, 1)
        assertEquals(Rate.ZERO, tekCekim.surchargeRate)
        assertEquals(Money.ZERO, tekCekim.surcharge)

        // Ekranda yazan oran, tutara uygulanan oranın kendisi olmalı.
        val taksitli = Pricing.breakdown(Money.ofMajor(1000.0), Money.ZERO, PaymentMethod.CARD, 12)
        assertEquals(Rate.ofPercent(20.0), taksitli.surchargeRate)
        assertEquals(taksitli.net.applyRate(taksitli.surchargeRate), taksitli.surcharge)
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
