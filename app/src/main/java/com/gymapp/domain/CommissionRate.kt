package com.gymapp.domain

import kotlin.math.roundToInt

/**
 * Personel hakediş oranı birim dönüşümleri.
 *
 * `StaffEntity.commissionRate` bir **kesir**dir (0.40 = %40), ancak personel ekranındaki
 * alan kullanıcıdan **yüzde** (40) alıyor. Bu iki birimin karıştırılması hakedişi
 * 100 kat yanlış hesaplar; dönüşüm bu yüzden tek bir yerde toplandı.
 */
object CommissionRate {

    /** Kullanıcının girdiği yüzdeyi (0..100) saklanacak kesre çevirir. */
    fun fromPercentInput(percent: Double?): Double {
        val safe = percent?.takeIf { it.isFinite() } ?: 0.0
        // İki ondalık hane hassasiyetinde yuvarla: %12.345 -> 0.1234
        return (safe.coerceIn(0.0, 100.0) * 100).roundToInt() / 10_000.0
    }

    /** Saklanan kesri ekranda gösterilecek yüzdeye çevirir. */
    fun toPercentDisplay(fraction: Double): Double {
        val safe = if (fraction.isFinite()) fraction.coerceIn(0.0, 1.0) else 0.0
        return (safe * 10_000).roundToInt() / 100.0
    }

    /**
     * Bir seansın personele düşen hakedişi.
     *
     * @param sessionValue seansın parasal karşılığı (paket ücreti / toplam seans)
     * @param rateFraction personelin hakediş kesri (0.40 = %40)
     */
    fun commissionFor(sessionValue: Double, rateFraction: Double): Double {
        if (!sessionValue.isFinite() || sessionValue <= 0.0) return 0.0
        val safeRate = if (rateFraction.isFinite()) rateFraction.coerceIn(0.0, 1.0) else 0.0
        return sessionValue * safeRate
    }
}
