package com.gymapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PeriodsTest {

    private val istanbul: ZoneId = ZoneId.of("Europe/Istanbul")

    @Test
    fun `gun araligi 24 saattir`() {
        val day = Periods.day(LocalDate.of(2026, 3, 15), istanbul)
        assertEquals(24 * 60 * 60 * 1000L, day.endExclusive - day.startInclusive)
    }

    /**
     * Regresyon: gün sonu `23:59:59` olarak hesaplanıyor ve `MILLISECOND` temizlenmiyordu;
     * 23:59:59.500'de oluşan bir kayıt aralığın dışında kalabiliyordu.
     */
    @Test
    fun `gunun son milisaniyesi araliga dahildir`() {
        val date = LocalDate.of(2026, 3, 15)
        val day = Periods.day(date, istanbul)
        val lastMillisOfDay = day.endExclusive - 1

        assertTrue(lastMillisOfDay in day)
        // Ertesi günün ilk milisaniyesi dahil DEĞİL (çift sayım olmaz).
        assertFalse(day.endExclusive in day)
    }

    @Test
    fun `gun basi araliga dahildir`() {
        val day = Periods.day(LocalDate.of(2026, 3, 15), istanbul)
        assertTrue(day.startInclusive in day)
        assertFalse(day.startInclusive - 1 in day)
    }

    @Test
    fun `ardisik gunler ne bosluk ne cakisma birakir`() {
        val first = Periods.day(LocalDate.of(2026, 3, 15), istanbul)
        val second = Periods.day(LocalDate.of(2026, 3, 16), istanbul)
        assertEquals(first.endExclusive, second.startInclusive)
    }

    @Test
    fun `ay araligi ayin tamamini kapsar`() {
        // monthIndex0 = 1 -> Şubat (Calendar.MONTH ile uyumlu)
        val february = Periods.month(2026, 1, istanbul)
        val expectedDays = 28L // 2026 artık yıl değil
        assertEquals(expectedDays * 24 * 60 * 60 * 1000L, february.endExclusive - february.startInclusive)
    }

    @Test
    fun `artik yil subat 29 gun surer`() {
        val february = Periods.month(2028, 1, istanbul)
        assertEquals(29L * 24 * 60 * 60 * 1000L, february.endExclusive - february.startInclusive)
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
        val now = LocalDate.of(2026, 6, 15).atStartOfDay(istanbul).toInstant().toEpochMilli()
        val window = Periods.lastMonths(1, now, istanbul)

        val future = now + 1
        assertFalse(future in window)
        assertTrue(now - 1 in window)
    }

    @Test
    fun `ciro penceresi tam olarak N ay geriye gider`() {
        val now = LocalDate.of(2026, 6, 15).atStartOfDay(istanbul).toInstant().toEpochMilli()
        val window = Periods.lastMonths(3, now, istanbul)

        val threeMonthsAgo = LocalDate.of(2026, 3, 15).atStartOfDay(istanbul).toInstant().toEpochMilli()
        assertEquals(threeMonthsAgo, window.startInclusive)
        assertEquals(now, window.endExclusive)
    }
}
