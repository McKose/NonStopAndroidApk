// `Instant` kotlinx-datetime 0.7'den beri `kotlin.time.Instant`'a takma ad.
// Takma ad yerine asıl tip yazılıyor: kullanım aynı, ama kullanımdan kaldırma
// uyarısı üretmiyor. Tip bu Kotlin sürümünde henüz deneysel işaretli.
@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.gymapp.domain

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Tarih ve saat biçimlendirme — **platformdan bağımsız**.
 *
 * ### Neden gerekti
 * Ekranlar `SimpleDateFormat` ve `java.time` kullanıyordu; ikisi de JVM'e özgü
 * ve Kotlin/Native'de (iOS) **yok**. Ekranlar ortak modüle taşınırken bu
 * çağrıların gidecek bir yeri olması gerekiyordu. `Periods.kt` bu sınırı zaten
 * öngörmüş ("her platform kendi tarih tipini yalnızca kendi UI sınırında
 * kullanır") — ama artık UI'ın kendisi ortak, dolayısıyla sınır buraya kaydı.
 *
 * ### Neden elle yazılmış ay adları
 * `kotlinx.datetime` yerel ayara (locale) duyarlı biçimlendirme SUNMUYOR;
 * `Locale("tr")` karşılığı yok. Ay adları bu yüzden burada, açıkça duruyor.
 *
 * Bu aslında bir kayıp değil, düzeltme: eski kodun iki yerinde
 * `Locale.getDefault()` kullanılıyordu, yani telefonu İngilizce olan personel
 * "19 August 2026", Türkçe olan "19 Ağustos 2026" görüyordu. Aynı salonun aynı
 * ekranı cihaza göre değişiyordu. Salon Türkiye'de ve arayüz baştan sona
 * Türkçe; tarih de öyle olmalı.
 *
 * ### Saat dilimi
 * Hepsi cihazın yerel saat dilimini kullanıyor — eski davranışın aynısı
 * (`ZoneId.systemDefault()` / `SimpleDateFormat`'ın varsayılanı). Randevu
 * saatleri salonun bulunduğu yere göre okunmalı; UTC göstermek personel için
 * anlamsız olurdu.
 */
object TarihBicimi {

    /** Tam ay adları — "19 Ağustos 2026" biçiminde kullanılıyor. */
    private val AYLAR = arrayOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
    )

    /**
     * Kısa ay adları — "19 Ağu 2026, 14:30" biçiminde.
     *
     * Türkçede kısaltmalar İngilizcedeki gibi düzenli ilk-üç-harf değil:
     * "Haziran"/"Temmuz" ayrımı için dördüncü harf gerekiyor (Haz/Tem çakışmaz
     * ama Ocak/Oca gibi tek heceliler farklı davranıyor). Bu yüzden liste
     * türetilmiyor, tek tek yazılıyor.
     */
    private val KISA_AYLAR = arrayOf(
        "Oca", "Şub", "Mar", "Nis", "May", "Haz",
        "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
    )

    /** `19.08.2026` */
    fun gunAyYil(epochMs: Long, zaman: TimeZone = TimeZone.currentSystemDefault()): String {
        val t = yerel(epochMs, zaman)
        return "${iki(t.dayOfMonth)}.${iki(t.monthNumber)}.${t.year}"
    }

    /** `19 Ağustos 2026` */
    fun gunAyAdiYil(epochMs: Long, zaman: TimeZone = TimeZone.currentSystemDefault()): String {
        val t = yerel(epochMs, zaman)
        return "${iki(t.dayOfMonth)} ${AYLAR[t.monthNumber - 1]} ${t.year}"
    }

    /** `19 Ağu 2026, 14:30` */
    fun gunKisaAyYilSaat(epochMs: Long, zaman: TimeZone = TimeZone.currentSystemDefault()): String {
        val t = yerel(epochMs, zaman)
        return "${iki(t.dayOfMonth)} ${KISA_AYLAR[t.monthNumber - 1]} ${t.year}, " +
            "${iki(t.hour)}:${iki(t.minute)}"
    }

    /** `19 Ağu, 14:30` — yıl yok; finans ekranı cari dönemi gösterdiği için gereksiz. */
    fun gunKisaAySaat(epochMs: Long, zaman: TimeZone = TimeZone.currentSystemDefault()): String {
        val t = yerel(epochMs, zaman)
        return "${iki(t.dayOfMonth)} ${KISA_AYLAR[t.monthNumber - 1]}, " +
            "${iki(t.hour)}:${iki(t.minute)}"
    }

    /** `14:30` */
    fun saat(epochMs: Long, zaman: TimeZone = TimeZone.currentSystemDefault()): String {
        val t = yerel(epochMs, zaman)
        return "${iki(t.hour)}:${iki(t.minute)}"
    }

    /**
     * Yerel saatin **sayı** olarak kendisi (0–23).
     *
     * Biçimlendirme değil, gruplama için: takvim ekranı randevuları saat
     * satırlarına dağıtıyor ve bunun için metne değil sayıya ihtiyacı var.
     * Önceden `Instant.ofEpochMilli(...).atZone(ZoneId.systemDefault()).hour`
     * ile hesaplanıyordu; o çağrı JVM'e özgü ve ekranın taşınmasını
     * engelliyordu.
     */
    fun saatSayisi(epochMs: Long, zaman: TimeZone = TimeZone.currentSystemDefault()): Int =
        yerel(epochMs, zaman).hour

    private fun yerel(epochMs: Long, zaman: TimeZone) =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zaman)

    /**
     * İki haneye tamamlar.
     *
     * Sıfır dolgusu şart: `9:5` gibi bir saat okunmaz ve `1.9.2026` sıralanabilir
     * değil. `padStart` yerine tek karşılaştırma — sıcak yolda (uzun listelerde
     * her satır için birkaç kez) çağrılıyor.
     */
    private fun iki(deger: Int): String = if (deger < 10) "0$deger" else deger.toString()
}
