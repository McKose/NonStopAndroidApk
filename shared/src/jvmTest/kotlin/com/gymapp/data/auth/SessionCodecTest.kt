package com.gymapp.data.auth

import com.gymapp.domain.StaffRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Oturumun saklanıp geri okunması.
 *
 * Buradaki hatalar kullanıcıya tek bir belirtiyle ulaşır: "tekrar giriş yapmak
 * zorunda kaldım". Kimse bunu hata olarak raporlamaz, o yüzden test ediliyor.
 */
class SessionCodecTest {

    private val oturum = Session(
        accessToken = "erisim",
        refreshToken = "yenileme",
        expiresAtMs = 1_700_000_000_000L,
        userId = "458f1383-d7ef-474b-8e16-798bde768654",
        email = "personel@ornek.com",
        tenantId = "65409c76-0226-4d89-91a2-48c2ab0d1cab",
        role = StaffRole.ADMIN,
    )

    @Test
    fun `yazilip okunan oturum aynidir`() {
        assertEquals(oturum, SessionCodec.decode(SessionCodec.encode(oturum)))
    }

    /**
     * Alanların hepsi ayrı ayrı korunmalı.
     *
     * Yukarıdaki eşitlik testi bunu zaten kapsıyor ama bir alan unutulduğunda
     * hangisi olduğunu söylemiyor; bu test hata mesajını okunur kılıyor.
     */
    @Test
    fun `tum alanlar korunur`() {
        val geri = requireNotNull(SessionCodec.decode(SessionCodec.encode(oturum)))

        assertEquals(oturum.accessToken, geri.accessToken)
        assertEquals(oturum.refreshToken, geri.refreshToken)
        assertEquals(oturum.expiresAtMs, geri.expiresAtMs)
        assertEquals(oturum.userId, geri.userId)
        assertEquals(oturum.email, geri.email)
        assertEquals(oturum.tenantId, geri.tenantId)
        assertEquals(oturum.role, geri.role)
    }

    @Test
    fun `bozuk metin oturum uretmez`() {
        assertNull(SessionCodec.decode("bu JSON değil"))
        assertNull(SessionCodec.decode(""))
        assertNull(SessionCodec.decode("[]"))
    }

    /**
     * Eksik alan yarım oturum üretmemeli.
     *
     * Jetonu olan ama salon kimliği olmayan bir oturum, her isteği sessizce
     * başarısız kılardı: kullanıcı giriş yapmış görünür, hiçbir veri gelmez.
     */
    @Test
    fun `eksik alan oturum uretmez`() {
        val tam = SessionCodec.encode(oturum)

        for (alan in listOf("accessToken", "refreshToken", "expiresAtMs", "userId", "tenantId")) {
            val eksik = tam.replace("\"$alan\"", "\"kaldirildi_$alan\"")
            assertNull(SessionCodec.decode(eksik), "$alan eksikken oturum kurulmamalı")
        }
    }

    /** E-posta bilgi amaçlı; eksikliği oturumu geçersiz kılmamalı. */
    @Test
    fun `eposta eksikse oturum yine kurulur`() {
        val eksik = SessionCodec.encode(oturum).replace("\"email\"", "\"yok\"")

        val geri = requireNotNull(SessionCodec.decode(eksik))
        assertEquals("", geri.email)
        assertEquals(oturum.tenantId, geri.tenantId)
    }

    /** Tanınmayan rol en dar yetkiye düşer — girişteki kuralın aynısı. */
    @Test
    fun `taninmayan rol en dar yetkiye duser`() {
        val bozuk = SessionCodec.encode(oturum).replace("\"ADMIN\"", "\"SUPERADMIN\"")

        assertEquals(StaffRole.TRAINER, SessionCodec.decode(bozuk)?.role)
    }
}
