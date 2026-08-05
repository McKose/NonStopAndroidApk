package com.gymapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hakediş oranı birim dönüşümleri.
 *
 * Regresyon bağlamı: oran bir dönem `Double` kesir (0.40) olarak saklanıyor, personel
 * ekranı ise **yüzde** (40) alıyordu. İki birimin karışması hakedişi 100 kat yanlış
 * hesaplıyordu. Birim artık tipin kendisinde kodlu: [Rate] baz puan taşır (4000 = %40).
 */
class RateTest {

    private val delta = 0.001

    @Test
    fun `yuzde girdisi baz puana cevrilir`() {
        assertEquals(4_000, Rate.ofPercent(40.0).basisPoints)
        assertEquals(0, Rate.ofPercent(0.0).basisPoints)
        assertEquals(10_000, Rate.ofPercent(100.0).basisPoints)
    }

    @Test
    fun `baz puan yuzde olarak gosterilir`() {
        assertEquals(40.0, Rate(4_000).asPercent, delta)
        assertEquals(12.5, Rate(1_250).asPercent, delta)
    }

    @Test
    fun `yuzde donusumu gidis donus kararlidir`() {
        listOf(0.0, 1.0, 12.5, 33.33, 40.0, 99.99, 100.0).forEach { percent ->
            assertEquals(percent, Rate.ofPercent(percent).asPercent, 0.01)
        }
    }

    @Test
    fun `gecersiz girdiler guvenli araliga cekilir`() {
        assertEquals(0, Rate.ofPercentOrZero(null).basisPoints)
        assertEquals(0, Rate.ofPercent(-10.0).basisPoints)
        assertEquals(10_000, Rate.ofPercent(500.0).basisPoints)
        assertEquals(0, Rate.ofPercent(Double.NaN).basisPoints)
    }

    // ─── Hakediş hesabı ─────────────────────────────────────────────────────

    @Test
    fun `hakedis seans degerinin orani kadardir`() {
        // 250,00 TL seans × %40 = 100,00 TL
        assertEquals(Money.ofMajor(100.0), Money.ofMajor(250.0).applyRate(Rate.ofPercent(40.0)))
    }

    @Test
    fun `sifir oran hakedis uretmez`() {
        assertEquals(Money.ZERO, Money.ofMajor(250.0).applyRate(Rate.ZERO))
    }

    @Test
    fun `sifir seans degeri hakedis uretmez`() {
        assertEquals(Money.ZERO, Money.ZERO.applyRate(Rate.ofPercent(40.0)))
    }

    /** Tam bölünmeyen oranlarda kuruş yuvarlaması tek noktadan geçer. */
    @Test
    fun `kusuratli oran kurusa yuvarlanir`() {
        // 100,00 × %33,33 = 33,33 TL
        assertEquals(Money(3_333), Money.ofMajor(100.0).applyRate(Rate.ofPercent(33.33)))
    }
}
