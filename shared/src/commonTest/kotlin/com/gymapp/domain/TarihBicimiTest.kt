package com.gymapp.domain

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tarih biçimlendirmesi — `commonTest`, yani hem JVM'de hem **Kotlin/Native'de**
 * (iOS) koşuyor.
 *
 * Ortak kaynak kümesinde olması bilinçli ve testin asıl değeri bu: aynı anın
 * iki platformda aynı metne dönüştüğünü kanıtlıyor. Yalnızca JVM'de koşsaydı,
 * iOS'ta ay adının boş çıkması ya da saatin kayması ancak telefonda görülürdü.
 *
 * Saat dilimi testlerde **açıkça** veriliyor. Varsayılan (cihazın yerel dilimi)
 * kullanılsaydı test, koştuğu makinenin ayarına göre farklı sonuç verirdi —
 * CI'da UTC, geliştiricide UTC+3.
 */
class TarihBicimiTest {

    /** Salonun dilimi. Beklenen çıktılar buna göre yazıldı. */
    private val istanbul = TimeZone.of("Europe/Istanbul")

    /**
     * 19 Ağustos 2026, 14:30 (İstanbul).
     *
     * Bu dosyadaki bütün zaman damgaları `zoneinfo` ile hesaplanıp geri
     * çevrilerek doğrulandı, elle yazılmadı: ilk denemede hepsi üç saat
     * kaymıştı (UTC ile İstanbul karıştırılmıştı) ve testler "geçiyor" diye
     * yanlış biçimi onaylayacaktı.
     */
    private val an = 1787139000000L

    @Test
    fun `gun ay yil`() {
        assertEquals("19.08.2026", TarihBicimi.gunAyYil(an, istanbul))
    }

    @Test
    fun `gun ay adi yil`() {
        assertEquals("19 Ağustos 2026", TarihBicimi.gunAyAdiYil(an, istanbul))
    }

    @Test
    fun `gun kisa ay yil saat`() {
        assertEquals("19 Ağu 2026, 14:30", TarihBicimi.gunKisaAyYilSaat(an, istanbul))
    }

    @Test
    fun `gun kisa ay saat`() {
        assertEquals("19 Ağu, 14:30", TarihBicimi.gunKisaAySaat(an, istanbul))
    }

    @Test
    fun `saat`() {
        assertEquals("14:30", TarihBicimi.saat(an, istanbul))
    }

    /**
     * Sıfır dolgusu.
     *
     * Ayrı test çünkü en kolay gözden kaçan ve en görünür hata: `1.9.2026` ya da
     * `9:5` gibi bir çıktı hem okunmaz hem sıralanamaz.
     */
    @Test
    fun `tek haneli deger sifirla dolduruluyor`() {
        // 5 Eylül 2026, 09:05 (İstanbul)
        val tekHaneli = 1788588300000L
        assertEquals("05.09.2026", TarihBicimi.gunAyYil(tekHaneli, istanbul))
        assertEquals("09:05", TarihBicimi.saat(tekHaneli, istanbul))
    }

    /**
     * On iki ayın adı da doğru mu.
     *
     * Tek tek yazılan iki dizide (tam ve kısa adlar) en olası hata sıra kayması;
     * tek bir örnekle sınamak onu yakalamaz. Kayma olsaydı ekranda "Mart"
     * yerine "Şubat" yazardı ve kimse fark etmeyebilirdi.
     */
    @Test
    fun `on iki ayin adi dogru`() {
        val beklenen = listOf(
            "Ocak" to "Oca", "Şubat" to "Şub", "Mart" to "Mar", "Nisan" to "Nis",
            "Mayıs" to "May", "Haziran" to "Haz", "Temmuz" to "Tem", "Ağustos" to "Ağu",
            "Eylül" to "Eyl", "Ekim" to "Eki", "Kasım" to "Kas", "Aralık" to "Ara",
        )
        // Her ayın 15'i, öğlen — ay sınırlarından uzak. (Türkiye 2016'dan beri
        // kalıcı UTC+3; yaz saati geçişi yok, ama öğlen seçmek yine de bu
        // testi olası bir ilke değişikliğine karşı duyarsız kılıyor.)
        val gunler = listOf(
            1768467600000L, 1771146000000L, 1773565200000L, 1776243600000L,
            1778835600000L, 1781514000000L, 1784106000000L, 1786784400000L,
            1789462800000L, 1792054800000L, 1794733200000L, 1797325200000L,
        )
        gunler.forEachIndexed { i, ms ->
            val (tam, kisa) = beklenen[i]
            assertEquals(
                "15 $tam 2026",
                TarihBicimi.gunAyAdiYil(ms, istanbul),
                "${i + 1}. ayın tam adı",
            )
            assertEquals(
                kisa,
                TarihBicimi.gunKisaAySaat(ms, istanbul).substringAfter(" ").substringBefore(","),
                "${i + 1}. ayın kısa adı",
            )
        }
    }
}
