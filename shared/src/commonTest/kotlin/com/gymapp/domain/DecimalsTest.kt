package com.gymapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecimalsTest {

    /**
     * Virgülle yazılan sayı sessizce sıfıra düşmemeli.
     *
     * Ekranlar `toDoubleOrNull() ?: 0.0` kullanıyordu: Türkçe klavyeyle "75,5"
     * yazan kullanıcının kilosu 0, iskontosu 0 kaydediliyordu — hiçbir uyarı
     * çıkmadan. Bu testin varlık sebebi o sessiz sıfırlama.
     */
    @Test
    fun `virgullu girdi sifira dusmez`() {
        assertEquals(75.5, Decimals.parseOrNull("75,5"))
        assertEquals(75.5, Decimals.parseOrDefault("75,5"))
        assertEquals(0.5, Decimals.parseOrNull("0,5"))
        assertEquals(-3.25, Decimals.parseOrNull("-3,25"))
    }

    @Test
    fun `nokta da ondalik ayiraci olarak kabul edilir`() {
        assertEquals(75.5, Decimals.parseOrNull("75.5"))
        assertEquals(1234.56, Decimals.parseOrNull("1234.56"))
        assertEquals(1234.56, Decimals.parseOrNull("1.234,56"))
        assertEquals(1234.56, Decimals.parseOrNull("1,234.56"))
    }

    @Test
    fun `belirsiz nokta turkce binlik olarak okunur`() {
        assertEquals(1500.0, Decimals.parseOrNull("1.500"))
        assertEquals(1234567.0, Decimals.parseOrNull("1.234.567"))
        // Tam kısım sıfırla başlıyorsa gruplama imkânsız.
        assertEquals(0.5, Decimals.parseOrNull("0.500"))
    }

    @Test
    fun `anlamsiz girdi null doner`() {
        assertNull(Decimals.parseOrNull(""))
        assertNull(Decimals.parseOrNull("   "))
        assertNull(Decimals.parseOrNull("abc"))
        assertNull(Decimals.parseOrNull("--"))
        assertNull(Decimals.parseOrNull("Infinity"))
        assertNull(Decimals.parseOrNull("NaN"))
    }

    /** Varsayılan yalnızca gerçekten geçersiz girdide devreye girer. */
    @Test
    fun `varsayilan yalnizca gecersiz girdide kullanilir`() {
        assertEquals(0.0, Decimals.parseOrDefault("abc"))
        assertEquals(30.0, Decimals.parseOrDefault("", fallback = 30.0))
        assertEquals(12.0, Decimals.parseOrDefault("12", fallback = 30.0))
        // Sıfır geçerli bir girdi; varsayılana düşmemeli.
        assertEquals(0.0, Decimals.parseOrDefault("0", fallback = 30.0))
    }
}
