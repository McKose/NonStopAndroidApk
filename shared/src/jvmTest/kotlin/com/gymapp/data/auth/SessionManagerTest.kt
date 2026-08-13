package com.gymapp.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Oturum sahibinin davranışı.
 *
 * Buradaki kararlar sessizce yanlış olabilecek türden: jeton ne zaman yenilenir,
 * yenileme düştüğünde oturum korunur mu düşürülür mü, aynı anda gelen iki istek
 * kaç yenileme tetikler. Üçü de yanlış olduğunda uygulama çalışmaya devam eder;
 * yalnızca senkronizasyon sessizce durur.
 */
class SessionManagerTest {

    private fun oturum(
        erisim: String = "jeton-1",
        yenileme: String = "yenileme-1",
        bitisMs: Long = 10_000_000L,
    ) = Session(
        accessToken = erisim,
        refreshToken = yenileme,
        expiresAtMs = bitisMs,
        userId = "kullanici-1",
        email = "personel@ornek.com",
        tenantId = "salon-1",
    )

    private class SahteAuthApi(
        private val giris: (String, String) -> AuthResult = { _, _ -> AuthResult.NoGym("x") },
        private val yenileme: (String) -> AuthResult = { AuthResult.Failed("-", retryable = true) },
        private val gecikmeMs: Long = 0,
    ) : AuthApi {
        var girisSayisi = 0
        var yenilemeSayisi = 0
        var sonYenilemeJetonu: String? = null

        override suspend fun signIn(email: String, password: String): AuthResult {
            girisSayisi++
            if (gecikmeMs > 0) delay(gecikmeMs)
            return giris(email, password)
        }

        override suspend fun refresh(refreshToken: String): AuthResult {
            yenilemeSayisi++
            sonYenilemeJetonu = refreshToken
            if (gecikmeMs > 0) delay(gecikmeMs)
            return yenileme(refreshToken)
        }
    }

    // ─── Giriş / çıkış ──────────────────────────────────────────────────────

    @Test
    fun `basarili giris oturumu saklar`() = runTest {
        val hedef = oturum()
        val store = InMemorySessionStore()
        val yonetici = SessionManager(
            authApi = SahteAuthApi(giris = { _, _ -> AuthResult.Success(hedef) }),
            store = store,
            now = { 0L },
        )

        assertIs<AuthResult.Success>(yonetici.signIn("personel@ornek.com", "sifre"))

        assertEquals(hedef, yonetici.session.value)
        assertEquals("salon-1", yonetici.tenantId)
        assertEquals(hedef, store.load(), "Oturum saklanmalı ki uygulama açılışında geri yüklensin")
    }

    /**
     * Başarısız giriş oturumu değiştirmemeli.
     *
     * Aksi hâlde yanlış şifreyle bir deneme, açık olan oturumu düşürürdü.
     */
    @Test
    fun `basarisiz giris mevcut oturuma dokunmaz`() = runTest {
        val mevcut = oturum()
        val store = InMemorySessionStore().apply { save(mevcut) }
        val yonetici = SessionManager(
            authApi = SahteAuthApi(giris = { _, _ -> AuthResult.InvalidCredentials("yanlış") }),
            store = store,
            now = { 0L },
        )
        yonetici.restore()

        assertIs<AuthResult.InvalidCredentials>(yonetici.signIn("personel@ornek.com", "yanlis"))

        assertEquals(mevcut, yonetici.session.value)
    }

    @Test
    fun `cikis oturumu hem bellekten hem saklamadan siler`() = runTest {
        val store = InMemorySessionStore().apply { save(oturum()) }
        val yonetici = SessionManager(SahteAuthApi(), store, now = { 0L })
        yonetici.restore()

        yonetici.signOut()

        assertNull(yonetici.session.value)
        assertNull(store.load(), "Saklamadan silinmezse uygulama açılışında geri gelirdi")
    }

    @Test
    fun `saklanan oturum geri yuklenir`() = runTest {
        val store = InMemorySessionStore().apply { save(oturum()) }
        val yonetici = SessionManager(SahteAuthApi(), store, now = { 0L })

        assertNull(yonetici.session.value)
        yonetici.restore()

        assertNotNull(yonetici.session.value)
        assertEquals("salon-1", yonetici.tenantId)
    }

    // ─── Jeton yenileme ─────────────────────────────────────────────────────

    @Test
    fun `oturum yokken jeton da yok`() = runTest {
        val api = SahteAuthApi()
        val yonetici = SessionManager(api, InMemorySessionStore(), now = { 0L })

        assertNull(yonetici.currentAccessToken())
        assertEquals(0, api.yenilemeSayisi, "Oturum yokken yenileme denenmemeli")
    }

    @Test
    fun `suresi dolmamis jeton oldugu gibi doner`() = runTest {
        val api = SahteAuthApi()
        val store = InMemorySessionStore().apply { save(oturum(bitisMs = 10_000_000L)) }
        val yonetici = SessionManager(api, store, now = { 1_000L })
        yonetici.restore()

        assertEquals("jeton-1", yonetici.currentAccessToken())
        assertEquals(0, api.yenilemeSayisi)
    }

    /**
     * Yenileme, süre **dolmadan** yapılıyor.
     *
     * Tam bitiminde yenilemek, yola çıkmış bir isteğin sunucuya vardığında süresi
     * dolmuş jeton taşıması demekti; sonuç 401, yani kaybedilmiş bir gönderim
     * turu ve kuyrukta bekleyen kayıtlar.
     */
    @Test
    fun `bitise az kala jeton yenilenir`() = runTest {
        val yeni = oturum(erisim = "jeton-2", yenileme = "yenileme-2", bitisMs = 200_000L)
        val api = SahteAuthApi(yenileme = { AuthResult.Success(yeni) })
        val store = InMemorySessionStore().apply { save(oturum(bitisMs = 100_000L)) }

        // Bitime 30 sn kaldı; varsayılan pay 60 sn.
        val yonetici = SessionManager(api, store, now = { 70_000L })
        yonetici.restore()

        assertEquals("jeton-2", yonetici.currentAccessToken())
        assertEquals(1, api.yenilemeSayisi)
        assertEquals("yenileme-1", api.sonYenilemeJetonu, "Yenileme, eski yenileme jetonuyla yapılmalı")
        assertEquals(yeni, store.load(), "Yenilenen oturum saklanmalı")
    }

    /**
     * Geçici hatada oturum **korunuyor**.
     *
     * Ağ yokken oturumu düşürmek, uçakta telefonu açan kullanıcıyı giriş ekranına
     * atmak olurdu. Motor `null` jetonu "oturum yok" sayıp kaydı kuyrukta bırakır;
     * ağ gelince kaldığı yerden devam eder.
     */
    @Test
    fun `gecici yenileme hatasinda oturum korunur`() = runTest {
        val api = SahteAuthApi(yenileme = { AuthResult.Failed("ağ yok", retryable = true) })
        val store = InMemorySessionStore().apply { save(oturum(bitisMs = 100_000L)) }
        val yonetici = SessionManager(api, store, now = { 99_000L })
        yonetici.restore()

        assertNull(yonetici.currentAccessToken())
        assertNotNull(yonetici.session.value, "Geçici hata oturumu düşürmemeli")
        assertNotNull(store.load())
    }

    /**
     * Kalıcı hatada oturum düşürülüyor.
     *
     * Yenileme jetonu iptal edildiğinde (şifre değişti, oturum sonlandırıldı)
     * oturumu tutmak, her turu sonsuza kadar başarısız kılar ve kullanıcı sebebini
     * hiç öğrenemez. Düşürmek en azından giriş ekranına götürüyor.
     */
    @Test
    fun `kalici yenileme hatasinda oturum dusurulur`() = runTest {
        val api = SahteAuthApi(yenileme = { AuthResult.Failed("jeton iptal", retryable = false) })
        val store = InMemorySessionStore().apply { save(oturum(bitisMs = 100_000L)) }
        val yonetici = SessionManager(api, store, now = { 99_000L })
        yonetici.restore()

        assertNull(yonetici.currentAccessToken())
        assertNull(yonetici.session.value)
        assertNull(store.load())
    }

    /** Kimlik hatası da kalıcı: yenileme jetonu reddedilmiş demektir. */
    @Test
    fun `yenilemede kimlik hatasi oturumu dusurur`() = runTest {
        val api = SahteAuthApi(yenileme = { AuthResult.InvalidCredentials("reddedildi") })
        val store = InMemorySessionStore().apply { save(oturum(bitisMs = 100_000L)) }
        val yonetici = SessionManager(api, store, now = { 99_000L })
        yonetici.restore()

        assertNull(yonetici.currentAccessToken())
        assertNull(yonetici.session.value)
    }

    /**
     * Aynı anda gelen iki istek tek yenileme tetikliyor.
     *
     * Supabase yenileme jetonunu her kullanımda değiştiriyor: iki eşzamanlı
     * yenileme olsaydı ikincisi birincinin aldığı jetonu geçersiz kılabilir ve
     * oturum kendi kendini düşürürdü. Senkronizasyon turu gönderimleri art arda
     * yaptığı için bu teorik bir durum değil.
     */
    @Test
    fun `es zamanli iki istek tek yenileme yapar`() = runTest {
        val yeni = oturum(erisim = "jeton-2", bitisMs = 10_000_000L)
        val api = SahteAuthApi(yenileme = { AuthResult.Success(yeni) }, gecikmeMs = 100)
        val store = InMemorySessionStore().apply { save(oturum(bitisMs = 100_000L)) }
        val yonetici = SessionManager(api, store, now = { 99_000L })
        yonetici.restore()

        val ilk = async { yonetici.currentAccessToken() }
        val ikinci = async { yonetici.currentAccessToken() }

        assertEquals("jeton-2", ilk.await())
        assertEquals("jeton-2", ikinci.await())
        assertEquals(1, api.yenilemeSayisi, "İkinci istek yenilenmiş jetonu beklemeli, yenisini istememeli")
    }
}
