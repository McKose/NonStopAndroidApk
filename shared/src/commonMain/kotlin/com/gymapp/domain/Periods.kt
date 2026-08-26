// `Instant` kotlinx-datetime 0.7'den beri `kotlin.time.Instant`'a takma ad.
// Takma ad yerine asıl tip yazılıyor: kullanım aynı, ama kullanımdan kaldırma
// uyarısı üretmiyor. Tip bu Kotlin sürümünde henüz deneysel işaretli.
@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.gymapp.domain

import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
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
 * Bir takvim ayının kimliği.
 *
 * @property ayIndeksi0 **0 tabanlı** ay (Ocak = 0) — [Periods.month] ile aynı
 *           gelenek, böylece ikisi arasında dönüştürme gerekmiyor.
 */
data class Donem(val ayIndeksi0: Int, val yil: Int)

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
     * Verilen anın içinde bulunduğu takvim ayı.
     *
     * Finans ekranı açılırken hangi dönemi göstereceğini buradan öğreniyor.
     * Önceden `LocalDate.now().monthValue - 1` ve `LocalDate.now().year` diye
     * **iki ayrı** çağrıyla hesaplanıyordu; ikisi JVM'e özgü olmasının yanında
     * saati iki kez okuduğu için 31 Aralık gece yarısında araya girildiğinde
     * "Aralık 2027" gibi var olmayan bir dönem üretebiliyordu. Tek `epochMillis`
     * girdisiyle o sınıf hata yapısal olarak imkânsız.
     */
    fun donemOf(
        epochMillis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Donem {
        val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
        return Donem(ayIndeksi0 = t.monthNumber - 1, yil = t.year)
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
