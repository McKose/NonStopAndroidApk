package com.gymapp.data.auth

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.StaffRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rol ve personel bağlantısı oturumdan **türetilmeli**, kopyalanmamalı.
 *
 * Düzeltilen hatalar sessizdi:
 *
 * - Rol girişte `SharedPreferences`'a kopyalanıyordu ve yalnızca orada
 *   yazılıyordu. Uygulama açılışında oturum `SessionManager.restore()` ile geri
 *   yükleniyor ama o yol tercihe hiç dokunmuyor; sunucuda rolü düşürülen bir
 *   kullanıcı, cihazda giriş ekranından geçmediği sürece eski yetkisiyle
 *   çalışmaya devam ediyordu.
 * - Personel bağlantısı da girişte bir kez kuruluyordu ve kurulamazsa sonucu
 *   **boş metin** oluyordu. Boş metin "bu kullanıcının hiç dersi yok" ile "bu
 *   kullanıcının kim olduğunu bilmiyoruz" arasındaki farkı siliyordu: eğitmen
 *   boş bir pano görüp uygulamanın verisini kaybettiğini sanıyordu.
 */
class CurrentUserTest {

    private val db: GymDatabase = createTestDatabase()
    private val session = MutableStateFlow<Session?>(null)
    private val currentUser = CurrentUser(session, db.staffDao())

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `oturum yokken en dar yetki`() = runTest {
        assertEquals(StaffRole.TRAINER, currentUser.role.first())
        assertEquals(StaffLink.NoSession, currentUser.staffLink.first())
    }

    @Test
    fun `rol oturumdan okunuyor`() = runTest {
        session.value = oturum(role = StaffRole.ADMIN)
        assertEquals(StaffRole.ADMIN, currentUser.role.first())
    }

    /**
     * Kopyanın sapabildiği asıl yer: oturum girişten değil, geri yüklemeden
     * geliyor. Buradaki akış girişi hiç görmüyor ve yine de doğru rolü veriyor.
     */
    @Test
    fun `rol degisince akis yeni degeri veriyor`() = runTest {
        session.value = oturum(role = StaffRole.ADMIN)
        assertEquals(StaffRole.ADMIN, currentUser.role.first())

        // Sunucu kullanıcıyı eğitmene düşürdü ve jeton yenilemesiyle yeni
        // oturum geldi. Cihazdaki tercihte bu değişiklik hiç görünmüyordu.
        session.value = oturum(role = StaffRole.TRAINER)
        assertEquals(StaffRole.TRAINER, currentUser.role.first())
    }

    @Test
    fun `personel kaydi yoksa baglanti kurulmamis sayiliyor`() = runTest {
        session.value = oturum(role = StaffRole.TRAINER)
        assertEquals(StaffLink.Unlinked, currentUser.staffLink.first())
    }

    @Test
    fun `personel kaydi varsa kimlik cozuluyor`() = runTest {
        db.staffDao().insertStaff(personel(id = "s1", authUserId = AUTH_USER))
        session.value = oturum(role = StaffRole.TRAINER)

        assertEquals(StaffLink.Linked("s1"), currentUser.staffLink.first())
    }

    /**
     * Bağlantı **akış**, tek seferlik okuma değil.
     *
     * Somut senaryo: eğitmen giriş yapıyor, personel kartında Supabase kimliği
     * henüz girilmemiş. Salon sahibi kartı dolduruyor ve satır senkronizasyon
     * turuyla iniyor. Eskiden eğitmenin çıkıp yeniden girmesi gerekiyordu.
     */
    @Test
    fun `personel karti sonradan baglaninca kendiliginden duzeliyor`() = runTest {
        session.value = oturum(role = StaffRole.TRAINER)
        assertEquals(StaffLink.Unlinked, currentUser.staffLink.first())

        db.staffDao().insertStaff(personel(id = "s7", authUserId = AUTH_USER))

        assertEquals(StaffLink.Linked("s7"), currentUser.staffLink.first())
    }

    /** Silinmiş personel bağlantı saymıyor; sorgu tombstone'ları eliyor. */
    @Test
    fun `silinmis personel baglanti saymiyor`() = runTest {
        db.staffDao().insertStaff(personel(id = "s9", authUserId = AUTH_USER))
        db.staffDao().softDelete("s9", nowMs = 10)
        session.value = oturum(role = StaffRole.TRAINER)

        assertEquals(StaffLink.Unlinked, currentUser.staffLink.first())
    }

    /** Çıkışta bağlantı da düşüyor; personel satırı cihazda kalsa bile. */
    @Test
    fun `cikista baglanti dusuyor`() = runTest {
        db.staffDao().insertStaff(personel(id = "s1", authUserId = AUTH_USER))
        session.value = oturum(role = StaffRole.ADMIN)
        assertEquals(StaffLink.Linked("s1"), currentUser.staffLink.first())

        session.value = null
        assertEquals(StaffLink.NoSession, currentUser.staffLink.first())
        assertEquals(StaffRole.TRAINER, currentUser.role.first())
    }

    private fun oturum(role: StaffRole) = Session(
        accessToken = "erisim",
        refreshToken = "yenileme",
        expiresAtMs = Long.MAX_VALUE,
        userId = AUTH_USER,
        email = "kullanici@ornek.com",
        tenantId = TEST_TENANT,
        role = role,
    )

    private fun personel(id: String, authUserId: String) = StaffEntity(
        id = id,
        tenantId = TEST_TENANT,
        fullName = "Ayşe Yılmaz",
        title = "Eğitmen",
        role = StaffRole.TRAINER,
        phone = "+905001112233",
        nickname = "ayse$id",
        authUserId = authUserId,
        createdAtMs = 1,
        updatedAtMs = 1,
    )

    private companion object {
        const val AUTH_USER = "458f1383-d7ef-474b-8e16-798bde768654"
    }
}
