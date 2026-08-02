package com.gymapp.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * **Yarı açık** zaman aralığı: `[startInclusive, endExclusive)`.
 *
 * Projede daha önce gün/ay sınırları `23:59:59` kurgusuyla hesaplanıyor ve
 * `MILLISECOND` alanı temizlenmediği için sınırdaki kayıtlar rastgele içeride/dışarıda
 * kalıyordu. Yarı açık aralıkta bu hata sınıfı yapısal olarak imkânsızdır.
 */
data class MillisRange(val startInclusive: Long, val endExclusive: Long) {
    operator fun contains(epochMillis: Long): Boolean =
        epochMillis >= startInclusive && epochMillis < endExclusive
}

/**
 * Dönem hesapları. `java.util.Calendar` yerine `java.time` kullanılır
 * (minSdk 26 olduğu için ek bağımlılık gerekmez) — KMP'ye geçişte
 * `kotlinx-datetime` karşılıklarına bire bir çevrilebilir.
 */
object Periods {

    /** Verilen günün tamamı. */
    fun day(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): MillisRange =
        MillisRange(
            startInclusive = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusive = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

    /** Verilen anın içinde bulunduğu gün. */
    fun dayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): MillisRange =
        day(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(), zone)

    /**
     * Takvim ayı.
     *
     * @param monthIndex0 `Calendar.MONTH` ile uyumlu olacak şekilde **0 tabanlı** (Ocak = 0).
     */
    fun month(year: Int, monthIndex0: Int, zone: ZoneId = ZoneId.systemDefault()): MillisRange {
        val yearMonth = YearMonth.of(year, monthIndex0 + 1)
        return MillisRange(
            startInclusive = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            endExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    /**
     * Şu andan geriye doğru [months] aylık pencere.
     *
     * Üst sınırı **[nowMs]**'dir: ileri tarihli (yanlışlıkla girilmiş) kayıtlar ciroya girmez.
     */
    fun lastMonths(months: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): MillisRange {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        return MillisRange(
            startInclusive = now.minusMonths(months).toInstant().toEpochMilli(),
            endExclusive = nowMs,
        )
    }
}
