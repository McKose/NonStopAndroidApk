// `Instant` artık `kotlin.time.Instant`'a takma ad (kotlinx-datetime 0.7);
// o tip henüz deneysel işaretli olduğu için opt-in gerekiyor.
@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.gymapp.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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
 * Dönem hesapları.
 *
 * API bilinçli olarak **epoch millis** üzerinden konuşur: her platform kendi tarih
 * tipini (Android'de `java.time`, iOS'ta `Foundation`) yalnızca kendi UI sınırında
 * kullanır, ortak kod tek bir sayısal temsille çalışır.
 */
object Periods {

    /** Bir günün milisaniye karşılığı. */
    private const val DAY_MS: Long = 24L * 60 * 60 * 1000

    /**
     * Gün sayısını milisaniyeye çevirir.
     *
     * `java.util.concurrent.TimeUnit` JVM'e özgü olduğu için ortak kodda kullanılamaz.
     * Not: sabit gün uzunluğu kullanılır — üyelik süresi takvim değil **süre** olarak
     * tanımlı, dolayısıyla yaz saati geçişleri süreyi kısaltıp uzatmaz.
     */
    fun daysInMillis(days: Int): Long = days * DAY_MS

    /** Verilen anın içinde bulunduğu gün. */
    fun dayOf(
        epochMillis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): MillisRange {
        val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
        return dayRange(date, zone)
    }

    /** Verilen takvim gününün tamamı. */
    fun day(
        date: LocalDate,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): MillisRange = dayRange(date, zone)

    private fun dayRange(date: LocalDate, zone: TimeZone): MillisRange = MillisRange(
        startInclusive = date.atStartOfDayIn(zone).toEpochMilliseconds(),
        endExclusive = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds(),
    )

    /**
     * Takvim ayı.
     *
     * @param monthIndex0 **0 tabanlı** ay (Ocak = 0) — çağıran taraftaki eski
     *        `Calendar.MONTH` alışkanlığıyla uyumlu.
     */
    fun month(
        year: Int,
        monthIndex0: Int,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): MillisRange {
        val first = LocalDate(year, monthIndex0 + 1, 1)
        return MillisRange(
            startInclusive = first.atStartOfDayIn(zone).toEpochMilliseconds(),
            endExclusive = first.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(zone).toEpochMilliseconds(),
        )
    }

    /**
     * Şu andan geriye doğru [months] aylık pencere.
     *
     * Üst sınırı **[nowMs]**'dir: ileri tarihli (yanlışlıkla girilmiş) kayıtlar ciroya girmez.
     */
    fun lastMonths(
        months: Int,
        nowMs: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): MillisRange {
        val now = Instant.fromEpochMilliseconds(nowMs)
        return MillisRange(
            startInclusive = now.minus(months, DateTimeUnit.MONTH, zone).toEpochMilliseconds(),
            endExclusive = nowMs,
        )
    }
}
