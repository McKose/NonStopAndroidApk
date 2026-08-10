package com.gymapp.domain

// `kotlin.jvm.*` yalnızca JVM hedeflerinde varsayılan import; ortak kodda
// açıkça alınması gerekiyor. Android tarafında sessizce çalışıyordu, hatayı
// Kotlin/Native derlemesi yakaladı.
import kotlin.jvm.JvmInline
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Parasal tutar — **kuruş** (minor unit) cinsinden tam sayı.
 *
 * `Double` ile para tutmak bir muhasebe uygulamasında kabul edilemez:
 * `0.1 + 0.2 != 0.3` ve toplamlar zamanla sapar. KMP'de `BigDecimal` stdlib'de
 * bulunmadığı için taşınabilir çözüm tam sayı minor unit'tir.
 *
 * Tüm bölme/oran işlemleri **tek bir yuvarlama noktasından** (yarıyı yukarı) geçer.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    /** Görüntüleme için TL cinsinden değer. Hesapta **kullanılmamalıdır**. */
    val asDouble: Double get() = minor / 100.0

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0L
    val isNegative: Boolean get() = minor < 0L

    operator fun plus(other: Money): Money = Money(minor + other.minor)
    operator fun minus(other: Money): Money = Money(minor - other.minor)
    operator fun times(quantity: Int): Money = Money(minor * quantity)
    operator fun unaryMinus(): Money = Money(-minor)

    /**
     * Oran uygulaması (ör. hakediş payı).
     *
     * Bilinçli olarak `operator times` değil: `Rate` de bir value class olduğundan
     * `times(Int)` ile JVM imza çakışması riski doğuyor.
     */
    fun applyRate(rate: Rate): Money =
        Money(divideRoundHalfUp(minor * rate.basisPoints, Rate.SCALE.toLong()))

    /** Eşit paylara bölme (ör. paket ücreti / seans sayısı). */
    operator fun div(divisor: Int): Money {
        require(divisor != 0) { "Sıfıra bölünemez." }
        return Money(divideRoundHalfUp(minor, divisor.toLong()))
    }

    /** Negatif tutarları sıfıra çeker — gelir/gider kayıtları negatif olmamalı. */
    fun coerceNonNegative(): Money = if (minor < 0) ZERO else this

    fun coerceAtMost(limit: Money): Money = if (minor > limit.minor) limit else this

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    override fun toString(): String {
        val sign = if (minor < 0) "-" else ""
        val abs = minor.absoluteValue
        return "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

    companion object {
        val ZERO = Money(0)

        fun ofMinor(minor: Long): Money = Money(minor)

        /** TL cinsinden değeri kuruşa çevirir (yarıyı yukarı yuvarlar). */
        fun ofMajor(amount: Double): Money =
            if (!amount.isFinite()) ZERO else Money((amount * 100).roundToLong())

        /**
         * Kullanıcı girdisini ayrıştırır: "1.234,56", "1234,56", "1234.56",
         * "1,234.56", "1234" hepsi kabul edilir.
         *
         * Ayıraç kuralları [Decimals] içinde; para ile ölçüm alanlarının aynı
         * girdiyi farklı okuması mümkün olmasın diye tek yerde tutuluyor.
         */
        fun parseOrNull(input: String): Money? =
            Decimals.parseOrNull(input)?.let { ofMajor(it) }
    }
}

/**
 * Taşma riski olmayan "yarıyı yukarı" bölme.
 *
 * `(2 * n + d) / (2 * d)` biçimi büyük değerlerde taşabildiği için
 * bölüm/kalan üzerinden hesaplanır.
 */
private fun divideRoundHalfUp(numerator: Long, denominator: Long): Long {
    require(denominator != 0L) { "Sıfıra bölünemez." }

    val negative = (numerator < 0) != (denominator < 0)
    val n = numerator.absoluteValue
    val d = denominator.absoluteValue

    var quotient = n / d
    val remainder = n % d
    if (remainder * 2 >= d) quotient++

    return if (negative) -quotient else quotient
}

/**
 * Oran — **baz puan** (basis point) cinsinden. `4000` = %40.
 *
 * Yüzde mi kesir mi olduğu belirsiz `Double` alanlar hakediş hesabında
 * 100 kat hataya yol açmıştı; birim artık tipin kendisinde kodlu.
 */
@JvmInline
value class Rate(val basisPoints: Int) : Comparable<Rate> {

    val asPercent: Double get() = basisPoints / 100.0
    val asFraction: Double get() = basisPoints / SCALE.toDouble()

    override fun compareTo(other: Rate): Int = basisPoints.compareTo(other.basisPoints)

    override fun toString(): String = "%${asPercent}"

    companion object {
        /** Baz puan ölçeği: %100 = 10.000 bp. */
        const val SCALE: Int = 10_000

        val ZERO = Rate(0)

        /** Kullanıcının girdiği yüzdeyi (0..100) baz puana çevirir. */
        fun ofPercent(percent: Double): Rate {
            if (!percent.isFinite()) return ZERO
            return Rate((percent.coerceIn(0.0, 100.0) * 100).roundToInt())
        }

        fun ofPercentOrZero(percent: Double?): Rate =
            percent?.let { ofPercent(it) } ?: ZERO
    }
}
