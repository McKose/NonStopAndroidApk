package com.gymapp.domain

import kotlin.test.assertFalse
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
}
