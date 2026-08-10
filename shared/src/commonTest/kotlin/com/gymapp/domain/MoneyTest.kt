package com.gymapp.domain

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class MoneyTest {

    @Test
    fun `toplama ve cikarma kurus hassasiyetinde calisir`() {
        assertEquals(Money(30), Money(10) + Money(20))
        assertEquals(Money(-10), Money(10) - Money(20))
    }

    /**
     * Regresyon: para `Double` ile tutulduğunda bu toplam 0.30000000000000004 çıkıyordu.
     */
    @Test
    fun `ondalik sapma olusmaz`() {
        val total = Money.ofMajor(0.1) + Money.ofMajor(0.2)
        assertEquals(Money.ofMajor(0.3), total)
        assertEquals(30L, total.minor)
    }

    @Test
    fun `bin kez toplama sapma biriktirmez`() {
        var total = Money.ZERO
        repeat(1000) { total += Money.ofMajor(0.01) }
        assertEquals(Money.ofMajor(10.0), total)
    }

    // ─── Bölme ve yuvarlama ─────────────────────────────────────────────────

    @Test
    fun `esit bolunen paket seans basina tam deger verir`() {
        // 3000,00 TL / 12 seans = 250,00 TL
        assertEquals(Money.ofMajor(250.0), Money.ofMajor(3000.0) / 12)
    }

    @Test
    fun `bolme yariyi yukari yuvarlar`() {
        // 10,00 / 3 = 3,3333... -> 3,33
        assertEquals(Money(333), Money.ofMajor(10.0) / 3)
        // 0,05 / 2 = 0,025 -> 0,03 (yarı yukarı)
        assertEquals(Money(3), Money(5) / 2)
    }

    @Test
    fun `negatif bolmede yuvarlama simetriktir`() {
        assertEquals(Money(-3), Money(-5) / 2)
        assertEquals(Money(-333), Money.ofMajor(-10.0) / 3)
    }

    @Test
    fun `sifira bolme reddedilir`() {
        val error = runCatching { Money.ofMajor(10.0) / 0 }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    // ─── Oran ───────────────────────────────────────────────────────────────

    @Test
    fun `oran uygulamasi hakedisi dogru hesaplar`() {
        // 250,00 TL seans değeri, %40 hakediş -> 100,00 TL
        assertEquals(Money.ofMajor(100.0), Money.ofMajor(250.0).applyRate(Rate.ofPercent(40.0)))
    }

    @Test
    fun `oran yuvarlamasi yariyi yukari yapar`() {
        // 0,01 * %50 = 0,005 -> 0,01
        assertEquals(Money(1), Money(1).applyRate(Rate.ofPercent(50.0)))
    }

    @Test
    fun `sifir oran sifir hakedis uretir`() {
        assertEquals(Money.ZERO, Money.ofMajor(250.0).applyRate(Rate.ZERO))
    }

    @Test
    fun `sifir matrah hakedis uretmez`() {
        assertEquals(Money.ZERO, Money.ZERO.applyRate(Rate.ofPercent(40.0)))
    }

    /**
     * Regresyon bağlamı: oran bir dönem `Double` kesir (0.40) olarak saklanıyor,
     * personel ekranı ise **yüzde** (40) alıyordu. İki birimin karışması hakedişi
     * 100 kat yanlış hesaplıyordu. Birim artık [Rate]'in kendisinde kodlu ve tam
     * bölünmeyen oranlarda da tek bir yuvarlama noktasından geçiyor.
     */
    @Test
    fun `kusuratli oran kurusa yuvarlanir`() {
        // 100,00 * %33,33 = 33,33 TL
        assertEquals(Money(3_333), Money.ofMajor(100.0).applyRate(Rate.ofPercent(33.33)))
    }

    // ─── Rate birimi ────────────────────────────────────────────────────────

    @Test
    fun `yuzde baz puana cevrilir`() {
        assertEquals(4000, Rate.ofPercent(40.0).basisPoints)
        assertEquals(1250, Rate.ofPercent(12.5).basisPoints)
        assertEquals(10_000, Rate.ofPercent(100.0).basisPoints)
    }

    @Test
    fun `oran gidis donus tutarlidir`() {
        listOf(0.0, 5.0, 12.5, 40.0, 100.0).forEach { percent ->
            assertEquals(percent, Rate.ofPercent(percent).asPercent, 0.001)
        }
    }

    @Test
    fun `gecersiz oranlar guvenli araliga cekilir`() {
        assertEquals(Rate.ZERO, Rate.ofPercent(-10.0))
        assertEquals(10_000, Rate.ofPercent(500.0).basisPoints)
        assertEquals(Rate.ZERO, Rate.ofPercent(Double.NaN))
        assertEquals(Rate.ZERO, Rate.ofPercentOrZero(null))
    }

    // ─── Kısıtlar ───────────────────────────────────────────────────────────

    @Test
    fun `negatif tutar sifira cekilebilir`() {
        assertEquals(Money.ZERO, Money(-500).coerceNonNegative())
        assertEquals(Money(500), Money(500).coerceNonNegative())
    }

    @Test
    fun `ust sinir uygulanabilir`() {
        assertEquals(Money(100), Money(500).coerceAtMost(Money(100)))
        assertEquals(Money(50), Money(50).coerceAtMost(Money(100)))
    }

    // ─── Ayrıştırma ve gösterim ─────────────────────────────────────────────

    @Test
    fun `kullanici girdisi ayristirilir`() {
        assertEquals(Money.ofMajor(1234.56), Money.parseOrNull("1.234,56"))
        assertEquals(Money.ofMajor(1234.56), Money.parseOrNull("1234,56"))
        assertEquals(Money.ofMajor(1234.0), Money.parseOrNull("1234"))
        assertEquals(Money.ofMajor(0.5), Money.parseOrNull(" 0,50 "))
    }

    /**
     * Nokta ondalık ayıracı olarak da kabul edilir.
     *
     * Eski ayrıştırıcı **her** noktayı binlik ayıracı sayıp siliyordu: "1234.56"
     * yazan kullanıcı 123.456,00 TL kaydediyordu. Finans ekranındaki tutar alanı
     * bu ayrıştırıcıyı kullandığı ve Android'in sayı klavyesi cihaz diline göre
     * nokta üretebildiği için gerçekten ulaşılabilir bir yoldu.
     */
    @Test
    fun `nokta ondalik ayiraci olarak kabul edilir`() {
        assertEquals(Money.ofMajor(1234.56), Money.parseOrNull("1234.56"))
        assertEquals(Money.ofMajor(0.5), Money.parseOrNull("0.50"))
        assertEquals(Money.ofMajor(99.9), Money.parseOrNull("99.9"))
        // İngilizce biçim: son ayıraç ondalıktır.
        assertEquals(Money.ofMajor(1234.56), Money.parseOrNull("1,234.56"))
    }

    /**
     * Tek başına nokta belirsiz: "1.500" hem 1500 hem 1,5 olabilir.
     *
     * Tam üç haneli son grup Türkçe binlik okumasıyla çözülür; tam kısım sıfırla
     * başlıyorsa gruplama imkânsız olduğu için ondalık okunur.
     */
    @Test
    fun `belirsiz nokta turkce binlik olarak okunur`() {
        assertEquals(Money.ofMajor(1500.0), Money.parseOrNull("1.500"))
        assertEquals(Money.ofMajor(10500.0), Money.parseOrNull("10.500"))
        assertEquals(Money.ofMajor(1234567.0), Money.parseOrNull("1.234.567"))
        // Sıfırla başlayan tam kısım gruplanamaz.
        assertEquals(Money.ofMajor(0.5), Money.parseOrNull("0.500"))
    }

    @Test
    fun `gecersiz girdi null doner`() {
        assertEquals(null, Money.parseOrNull(""))
        assertEquals(null, Money.parseOrNull("abc"))
        assertEquals(null, Money.parseOrNull("--"))
    }

    @Test
    fun `gosterim iki ondalik hane icerir`() {
        assertEquals("250,00", Money.ofMajor(250.0).toString())
        assertEquals("0,05", Money(5).toString())
        assertEquals("-12,34", Money(-1234).toString())
    }
}
