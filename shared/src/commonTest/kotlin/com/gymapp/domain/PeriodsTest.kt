@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.gymapp.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeriodsTest {

    private val istanbul: TimeZone = TimeZone.of("Europe/Istanbul")
    private val dayMs = 24L * 60 * 60 * 1000

    private fun startOf(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atStartOfDayIn(istanbul).toEpochMilliseconds()

    @Test
    fun `gun araligi 24 saattir`() {
        val day = Periods.day(LocalDate(2026, 3, 15), istanbul)
        assertEquals(dayMs, day.endExclusive - day.startInclusive)
    }

    /**
     * Regresyon: gün sonu `23:59:59` olarak hesaplanıyor ve `MILLISECOND` temizlenmiyordu;
     * 23:59:59.500'de oluşan bir kayıt aralığın dışında kalabiliyordu.
     */
    @Test
    fun `gunun son milisaniyesi araliga dahildir`() {
        val day = Periods.day(LocalDate(2026, 3, 15), istanbul)
        assertTrue(day.endExclusive - 1 in day)
        // Ertesi günün ilk milisaniyesi dahil DEĞİL (çift sayım olmaz).
        assertFalse(day.endExclusive in day)
    }

    @Test
    fun `gun basi araliga dahildir`() {
        val day = Periods.day(LocalDate(2026, 3, 15), istanbul)
        assertTrue(day.startInclusive in day)
        assertFalse(day.startInclusive - 1 in day)
    }

    @Test
    fun `ardisik gunler ne bosluk ne cakisma birakir`() {
        val first = Periods.day(LocalDate(2026, 3, 15), istanbul)
        val second = Periods.day(LocalDate(2026, 3, 16), istanbul)
        assertEquals(first.endExclusive, second.startInclusive)
    }

    /** `dayOf` ile `day` aynı günü vermeli: biri andan, diğeri tarihten türetiyor. */
    @Test
    fun `dayOf gun icindeki herhangi bir ani ayni araliga esler`() {
        val date = LocalDate(2026, 3, 15)
        val expected = Periods.day(date, istanbul)
        val middayMs = expected.startInclusive + dayMs / 2

        assertEquals(expected, Periods.dayOf(middayMs, istanbul))
    }

    @Test
    fun `ay araligi ayin tamamini kapsar`() {
        // monthIndex0 = 1 -> Şubat
        val february = Periods.month(2026, 1, istanbul)
        assertEquals(28L * dayMs, february.endExclusive - february.startInclusive)
    }

    @Test
    fun `artik yil subat 29 gun surer`() {
        val february = Periods.month(2028, 1, istanbul)
        assertEquals(29L * dayMs, february.endExclusive - february.startInclusive)
    }

    @Test
    fun `ardisik aylar ne bosluk ne cakisma birakir`() {
        val december = Periods.month(2026, 11, istanbul)
        val january = Periods.month(2027, 0, istanbul)
        assertEquals(december.endExclusive, january.startInclusive)
    }

    /** Regresyon: ciro penceresinin üst sınırı yoktu; ileri tarihli kayıtlar sayılıyordu. */
    @Test
    fun `ciro penceresi ileri tarihli kayitlari icermez`() {
        val now = startOf(2026, 6, 15)
        val window = Periods.lastMonths(1, now, istanbul)

        assertFalse(now + 1 in window)
        assertTrue(now - 1 in window)
    }

    @Test
    fun `ciro penceresi tam olarak N ay geriye gider`() {
        val now = startOf(2026, 6, 15)
        val window = Periods.lastMonths(3, now, istanbul)

        assertEquals(startOf(2026, 3, 15), window.startInclusive)
        assertEquals(now, window.endExclusive)
    }
}
