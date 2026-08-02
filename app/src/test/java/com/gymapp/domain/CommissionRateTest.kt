package com.gymapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CommissionRateTest {

    private val delta = 0.0001

    /**
     * Regresyon: ekran hakedişi **yüzde** olarak alıyor, hesap ise **kesir** bekliyordu.
     * Dönüşüm yapılmadığında 40 girilince hakediş 100 kat fazla hesaplanıyordu.
     */
    @Test
    fun `yuzde girdisi kesre cevrilir`() {
        assertEquals(0.40, CommissionRate.fromPercentInput(40.0), delta)
        assertEquals(0.0, CommissionRate.fromPercentInput(0.0), delta)
        assertEquals(1.0, CommissionRate.fromPercentInput(100.0), delta)
    }

    @Test
    fun `kesir ekranda yuzde olarak gosterilir`() {
        assertEquals(40.0, CommissionRate.toPercentDisplay(0.40), delta)
        assertEquals(12.5, CommissionRate.toPercentDisplay(0.125), delta)
    }

    @Test
    fun `donusum gidis donus tutarlidir`() {
        listOf(0.0, 5.0, 12.5, 40.0, 100.0).forEach { percent ->
            val roundTrip = CommissionRate.toPercentDisplay(CommissionRate.fromPercentInput(percent))
            assertEquals("Yüzde: $percent", percent, roundTrip, delta)
        }
    }

    @Test
    fun `gecersiz girdiler guvenli araliga cekilir`() {
        assertEquals(0.0, CommissionRate.fromPercentInput(null), delta)
        assertEquals(0.0, CommissionRate.fromPercentInput(-10.0), delta)
        assertEquals(1.0, CommissionRate.fromPercentInput(500.0), delta)
        assertEquals(0.0, CommissionRate.fromPercentInput(Double.NaN), delta)
    }

    // ─── Hakediş hesabı ─────────────────────────────────────────────────────

    @Test
    fun `hakedis seans degeri ile oranin carpimidir`() {
        // 3000 TL / 12 seans = 250 TL/seans, %40 hakediş -> 100 TL
        val sessionValue = 3000.0 / 12
        assertEquals(100.0, CommissionRate.commissionFor(sessionValue, 0.40), delta)
    }

    @Test
    fun `oran sifirsa hakedis olusmaz`() {
        assertEquals(0.0, CommissionRate.commissionFor(250.0, 0.0), delta)
    }

    @Test
    fun `gecersiz seans degeri hakedis uretmez`() {
        assertEquals(0.0, CommissionRate.commissionFor(0.0, 0.40), delta)
        assertEquals(0.0, CommissionRate.commissionFor(-100.0, 0.40), delta)
        assertEquals(0.0, CommissionRate.commissionFor(Double.NaN, 0.40), delta)
    }
}
