package com.gymapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Para biçimlendirme — `commonTest`, yani JVM'de **ve Kotlin/Native'de** (iOS)
 * koşuyor. [TarihBicimiTest] ile aynı gerekçe: aynı tutarın iki platformda
 * aynı metne dönüştüğü kanıtlanmalı.
 */
class ParaBicimiTest {

    @Test
    fun `binlik ayirici ve kurus`() {
        assertEquals("₺1.234,56", ParaBicimi.tl(Money(123456)))
        assertEquals("₺9.999,99", ParaBicimi.tl(Money(999999)))
        assertEquals("₺1.000.000,00", ParaBicimi.tl(Money(100_000_000)))
    }

    /**
     * Küçük tutarlar.
     *
     * `₺0,05` ile `₺0,5` arasındaki fark beş kuruşla elli kuruş: kuruşun sıfır
     * dolgusu olmadan tutar ON KAT yanlış okunuyor.
     */
    @Test
    fun `kurus sifirla dolduruluyor`() {
        assertEquals("₺0,05", ParaBicimi.tl(Money(5)))
        assertEquals("₺0,99", ParaBicimi.tl(Money(99)))
        assertEquals("₺1,00", ParaBicimi.tl(Money(100)))
        assertEquals("₺0,00", ParaBicimi.tl(Money.ZERO))
    }

    /**
     * Eksi işaretinin yeri `₺`'den SONRA.
     *
     * Eski kod `"₺" + String.format(…)` yazıyordu ve negatif tutarda çıktı
     * `₺-1.234,56` oluyordu. Davranış bilinçli olarak korundu: burada "daha
     * doğru" bir yazım tercih etmek, ekranı sessizce değiştirmek olurdu.
     */
    @Test
    fun `negatif tutar eski davranisi koruyor`() {
        assertEquals("₺-1.234,56", ParaBicimi.tl(Money(-123456)))
    }

    /** Üç haneye kadar ayırıcı olmamalı — `₺100` doğru, `₺.100` değil. */
    @Test
    fun `ucyuz haneye kadar ayirici yok`() {
        assertEquals("₺100,00", ParaBicimi.tl(Money(10000)))
        assertEquals("₺999,00", ParaBicimi.tl(Money(99900)))
        assertEquals("₺1.000,00", ParaBicimi.tl(Money(100000)))
    }

    /**
     * Yuvarlama sıfırdan UZAĞA.
     *
     * Özet kartı gerçek tutarı küçük göstermemeli: `₺1.234,50` özet olarak
     * `₺1.235` yazıyor, `₺1.234` değil.
     */
    @Test
    fun `yuvarlak gosterim`() {
        assertEquals("₺1.235", ParaBicimi.tlYuvarlak(Money(123456)))
        assertEquals("₺1.234", ParaBicimi.tlYuvarlak(Money(123449)))
        assertEquals("₺1", ParaBicimi.tlYuvarlak(Money(50)))
        assertEquals("₺0", ParaBicimi.tlYuvarlak(Money(49)))
        assertEquals("₺-1.235", ParaBicimi.tlYuvarlak(Money(-123456)))
    }
}
