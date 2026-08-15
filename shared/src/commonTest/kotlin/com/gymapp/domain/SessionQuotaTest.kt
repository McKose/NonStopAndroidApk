package com.gymapp.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Seans kotası artık `-1` sihirli sayısıyla değil `null` ile ifade ediliyor.
 *
 * Eski kurguda sınırsız paketler ekranda "-1 Seans" olarak görünüyordu ve
 * `-1` değeri kimi yerde "sınırsız", kimi yerde "tanımsız" anlamına geliyordu.
 *
 * KALDIRILDI: `consume`/`restore` testleri. O iki fonksiyonu kimse çağırmıyordu —
 * seans düşümü ve iadesi `MemberDao` içinde atomik SQL olarak yapılıyor. Yani bu
 * testler üretimde koşan kuralı değil, onun kullanılmayan bir kopyasını
 * doğruluyordu; geçmeleri kuralın doğru olduğu anlamına gelmiyordu. Gerçek
 * kuralı doğrulamanın yolu veritabanı testi yazmak.
 */
class SessionQuotaTest {

    @Test
    fun `null kota sinirsiz demektir`() {
        assertTrue(SessionQuota.isUnlimited(null))
        assertFalse(SessionQuota.isUnlimited(0))
        assertFalse(SessionQuota.isUnlimited(10))
    }

    @Test
    fun `sinirsiz kotada her zaman seans hakki vardir`() {
        assertTrue(SessionQuota.hasSessionsLeft(null))
    }

    @Test
    fun `kota bitince seans hakki kalmaz`() {
        assertTrue(SessionQuota.hasSessionsLeft(1))
        assertFalse(SessionQuota.hasSessionsLeft(0))
    }

    // ─── Yenilemede devir ───────────────────────────────────────────────────
    //
    // Yenileme önceden kalan seansları KOŞULSUZ siliyordu ve bu, aynı işlemin
    // tarih yarısıyla çelişiyordu: üyeliği bitmemiş birinin kalan günleri
    // devrediyor, kalan seansları siliniyordu. Karar artık kullanıcının.

    @Test
    fun `devirde kalan seanslar yeni paketin ustune eklenir`() {
        assertEquals(13, SessionQuota.onRenewal(10, 3, SessionCarryOver.CARRY))
    }

    @Test
    fun `iptalde kalan seanslar silinir`() {
        assertEquals(10, SessionQuota.onRenewal(10, 3, SessionCarryOver.DISCARD))
    }

    /**
     * İki seçim gerçekten farklı sonuç veriyor.
     *
     * Ayrı ayrı bakan iki test, seçimin hiç okunmadığı bir uygulamada da
     * geçebilirdi — ikisi de aynı sayıyı döndürse fark edilmezdi.
     */
    @Test
    fun `iki secim farkli sonuc veriyor`() {
        assertNotEquals(
            SessionQuota.onRenewal(8, 5, SessionCarryOver.CARRY),
            SessionQuota.onRenewal(8, 5, SessionCarryOver.DISCARD),
        )
    }

    /**
     * Yeni paket sınırsızsa kota yok — seçim ne olursa olsun.
     *
     * Devredilen seansı "sınırsız"a eklemenin karşılığı yok; sonuç yine
     * sınırsız. `10 + sınırsız = 10` gibi bir sayı üretmek, sınırsız paketi
     * sessizce sınırlı hâle getirirdi.
     */
    @Test
    fun `sinirsiz yeni pakette kota yok`() {
        assertNull(SessionQuota.onRenewal(null, 7, SessionCarryOver.CARRY))
        assertNull(SessionQuota.onRenewal(null, 7, SessionCarryOver.DISCARD))
    }

    /**
     * Eski paket sınırsızsa devredecek **sayılabilir** hak yok.
     *
     * "Sınırsız"ın bir sayı karşılığı olmadığı için sıfır sayılıyor;
     * alternatifi uydurulmuş bir sayı olurdu.
     */
    @Test
    fun `sinirsiz eski paketten devredecek sayi yok`() {
        assertEquals(10, SessionQuota.onRenewal(10, null, SessionCarryOver.CARRY))
    }

    @Test
    fun `sifir kalan seansta iki secim de ayni`() {
        assertEquals(10, SessionQuota.onRenewal(10, 0, SessionCarryOver.CARRY))
        assertEquals(10, SessionQuota.onRenewal(10, 0, SessionCarryOver.DISCARD))
    }

    /**
     * Negatif kalan seans kotayı azaltmıyor.
     *
     * `MemberDao.decrementSession` sıfırın altına inmiyor, yani bu değer
     * normalde oluşmuyor. Yine de: bozuk bir satır senkronizasyonla inseydi
     * devir, üyenin yeni paketinden seans **çalardı** — hem de sessizce.
     */
    @Test
    fun `negatif kalan seans yeni paketi azaltmiyor`() {
        assertEquals(10, SessionQuota.onRenewal(10, -5, SessionCarryOver.CARRY))
    }
}
