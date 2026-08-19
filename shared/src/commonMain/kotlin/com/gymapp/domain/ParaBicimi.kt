package com.gymapp.domain

import kotlin.math.absoluteValue

/**
 * Para biçimlendirme — **platformdan bağımsız**.
 *
 * ### Neden gerekti
 * Ekranlar tutarları `String.format(Locale.getDefault(), "%,.2f", …)` ile
 * yazıyordu. `String.format` JVM'e özgü; Kotlin/Native'de (iOS) **yok**.
 * [TarihBicimi] ile aynı hikâye, aynı çözüm.
 *
 * ### Neden `Money.toString()` yetmiyor
 * O, ayırıcısız ve işaretsiz bir gösterim üretiyor (`1234,56`) ve testlerle
 * sabitlenmiş durumda — sözleşmesi bu, değiştirilmemeli. Ekranların istediği
 * ise binlik ayırıcılı, ₺ işaretli hâli: `₺1.234,56`. İki farklı ihtiyaç, iki
 * ayrı işlev.
 *
 * ### Yan kazanç: cihaza göre değişen tutarlar
 * `Locale.getDefault()` yüzünden telefonu İngilizce olan personel
 * `₺1,234.56`, Türkçe olan `₺1.234,56` görüyordu — ayırıcılar yer değişiyordu.
 * Aynı salonun aynı kasası, cihaza göre farklı okunuyordu. Türkçe biçim artık
 * burada sabit.
 *
 * ### Neden `Double` almıyor
 * Girdi hep [Money], yani kuruş tam sayısı. Ekranlar eskiden `asDouble`
 * üzerinden geçiyordu; bu gereksiz bir kayan nokta turu ve projede daha önce
 * gösterilen tutarla çekilen tutarın sapmasına yol açan hata sınıfı tam olarak
 * buydu. Biçimlendirme tam sayı üzerinde yapılıyor.
 */
object ParaBicimi {

    /** `₺1.234,56` — kuruşlu tam gösterim. */
    fun tl(tutar: Money): String = "₺" + sayi(tutar.minor, kurusGoster = true)

    /**
     * `₺1.235` — kuruşsuz, yuvarlanmış.
     *
     * Özet kartlarında kullanılıyor: orada okunabilirlik kuruş hassasiyetinden
     * önemli. Yuvarlama sıfırdan UZAĞA yapılıyor (`0,5` → `1`), yani gösterilen
     * özet gerçek tutarı küçültmüyor.
     */
    fun tlYuvarlak(tutar: Money): String {
        val isaret = if (tutar.minor < 0) "-" else ""
        val mutlak = tutar.minor.absoluteValue
        val lira = (mutlak + 50) / 100
        return "₺$isaret${binlik(lira)}"
    }

    private fun sayi(minor: Long, kurusGoster: Boolean): String {
        val isaret = if (minor < 0) "-" else ""
        val mutlak = minor.absoluteValue
        val lira = mutlak / 100
        val kurus = mutlak % 100
        val govde = binlik(lira)
        return if (kurusGoster) {
            "$isaret$govde,${if (kurus < 10) "0$kurus" else "$kurus"}"
        } else {
            "$isaret$govde"
        }
    }

    /**
     * Binlik ayırıcı — Türkçe biçimde nokta (`1.234.567`).
     *
     * Elle yazılıyor çünkü `kotlinx` tarafında yerel ayara duyarlı sayı
     * biçimlendirme yok; [TarihBicimi]'ndeki ay adlarıyla aynı gerekçe.
     */
    private fun binlik(deger: Long): String {
        val metin = deger.toString()
        if (metin.length <= 3) return metin
        val sonuc = StringBuilder()
        // Sondan başa üçerli gruplama: baştan gitmek, ilk grubun kaç haneli
        // olacağını önceden hesaplamayı gerektirirdi.
        for ((sayac, i) in (metin.length - 1 downTo 0).withIndex()) {
            if (sayac > 0 && sayac % 3 == 0) sonuc.append('.')
            sonuc.append(metin[i])
        }
        return sonuc.reverse().toString()
    }
}
