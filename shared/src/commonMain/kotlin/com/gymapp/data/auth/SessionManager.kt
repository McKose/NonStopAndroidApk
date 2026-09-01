package com.gymapp.data.auth

import com.gymapp.data.sync.AccessTokenProvider
import com.gymapp.domain.Now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Oturumun tek sahibi.
 *
 * Uygulamanın "kim giriş yapmış" sorusunun tek yanıtı burası: giriş, çıkış,
 * jeton yenileme ve hangi salonda çalışıldığı. Senkronizasyon tarafına
 * [AccessTokenProvider] olarak bağlanıyor, arayüz tarafına [session] akışıyla.
 *
 * ### Neden kilit var
 * [currentAccessToken] senkronizasyon turu sırasında art arda çağrılıyor ve
 * jetonun süresi dolmuşsa yenileme tetikliyor. Kilit olmasaydı aynı anda ilerleyen
 * iki gönderim iki yenileme isteği başlatırdı. Supabase yenileme jetonunu her
 * kullanımda döndürdüğü için ikinci istek birincinin aldığı jetonu geçersiz
 * kılabilir ve oturum kendi kendini düşürürdü.
 *
 * ### Süre dolmadan yenileniyor
 * Yenileme, jetonun bitimine [refreshMarginMs] kala yapılıyor. Tam bitiminde
 * yenilemek, yola çıkmış bir isteğin sunucuya vardığında süresi dolmuş bir jeton
 * taşıması demekti; sonuç 401 olurdu — kaybedilen bir tur ve kuyrukta bekleyen
 * kayıtlar.
 */
class SessionManager(
    private val authApi: AuthApi,
    private val store: SessionStore,
    private val now: () -> Long = { Now.epochMillis() },
    private val refreshMarginMs: Long = DEFAULT_REFRESH_MARGIN_MS,
) : AccessTokenProvider, TenantProvider {

    private val mutex = Mutex()
    private val _session = MutableStateFlow<Session?>(null)

    /** Güncel oturum; `null` ise giriş yapılmamış. */
    val session: StateFlow<Session?> = _session.asStateFlow()

    /**
     * Çalışılan salon.
     *
     * Depo katmanı bu değeri [TenantProvider] üzerinden okuyor. `null` dönmesi
     * "giriş yapılmamış" demek ve bu durumda hiçbir veri okunmamalı: oturum
     * yokken sabit bir kimliğe düşmek, bir kullanıcının verisini başka bir
     * kullanıcıya göstermenin en kolay yolu olurdu.
     */
    override fun currentTenantId(): String? = _session.value?.tenantId

    /** Saklanan oturumu geri yükler; uygulama açılışında bir kez çağrılır. */
    suspend fun restore() {
        mutex.withLock {
            if (_session.value == null) _session.value = store.load()
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        val result = authApi.signIn(email.trim(), password)
        if (result is AuthResult.Success) {
            mutex.withLock {
                _session.value = result.session
                store.save(result.session)
            }
        }
        return result
    }

    /**
     * Şifreyi değiştirir — **önce mevcut şifreyi doğrulayarak**.
     *
     * ### Neden yeniden giriş yapılıyor
     * Supabase'in şifre değiştirme ucu mevcut şifreyi sormuyor; geçerli bir
     * jeton yetiyor. Tek başına kullanılsaydı açık somut olurdu: salonun
     * tezgâhında açık unutulmuş bir telefonu eline alan biri şifreyi
     * değiştirip hesap sahibini kendi hesabından kilitleyebilirdi. Jeton
     * "bu kişi giriş yapmıştı" diyor, "bu kişi şu anda burada" demiyor.
     *
     * Doğrulama gerçek bir giriş denemesiyle yapılıyor — mevcut şifreyi
     * bilmenin başka bir kanıtı yok.
     *
     * ### Yan etkisi bilinçli: oturum tazeleniyor
     * Başarılı giriş yeni bir jeton çifti üretiyor ve saklanıyor. Eski jetonla
     * devam etmenin bir faydası yok; tazesi hem daha uzun ömürlü hem de
     * şifre değişiminden sonra kesinlikle geçerli.
     *
     * ### Sıra: önce doğrula, sonra yaz
     * Ters sırada, yanlış mevcut şifre girildiğinde şifre çoktan değişmiş
     * olurdu.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): PasswordChange {
        val current = _session.value
            ?: return PasswordChange.Failed("Oturum yok.", retryable = false)

        // Kimlik kanıtı: mevcut şifreyle giriş.
        val yenidenGiris = authApi.signIn(current.email, currentPassword)
        val tazeOturum = when (yenidenGiris) {
            is AuthResult.Success -> yenidenGiris.session
            is AuthResult.InvalidCredentials ->
                return PasswordChange.WrongPassword("Mevcut şifreniz yanlış.")
            is AuthResult.Failed ->
                return PasswordChange.Failed(yenidenGiris.reason, yenidenGiris.retryable)
            // Hesap doğrulandı ama salon bağlantısı bozulmuş: şifre
            // değiştirmenin bunu düzeltmesi mümkün değil ve devam etmek
            // kullanıcıya işe yaramaz bir "başarılı" mesajı gösterirdi.
            else -> return PasswordChange.Failed(
                "Hesabınızın salon bağlantısı okunamadı; yöneticinize başvurun.",
                retryable = false,
            )
        }

        mutex.withLock {
            _session.value = tazeOturum
            store.save(tazeOturum)
        }

        return authApi.updatePassword(tazeOturum.accessToken, newPassword)
    }

    suspend fun signOut() {
        mutex.withLock {
            _session.value = null
            store.clear()
        }
    }

    /**
     * Gönderimde kullanılacak jeton.
     *
     * Yenileme kalıcı olarak başarısız olduğunda oturum **temizleniyor**. Bozuk
     * bir oturumu tutmak, her senkronizasyon turunun sonsuza kadar başarısız
     * olması ve kullanıcının bunun sebebini hiç öğrenememesi demek olurdu;
     * temizlemek en azından giriş ekranına götürüyor.
     *
     * Geçici hatada (ağ yok) oturum korunuyor ve `null` dönüyor: motor bunu
     * "oturum yok" sayıp kaydı kuyrukta bırakıyor, sonraki tur tekrar deniyor.
     */
    override suspend fun currentAccessToken(): String? = mutex.withLock {
        val current = _session.value ?: return@withLock null
        if (now() < current.expiresAtMs - refreshMarginMs) return@withLock current.accessToken

        when (val result = authApi.refresh(current.refreshToken)) {
            is AuthResult.Success -> {
                _session.value = result.session
                store.save(result.session)
                result.session.accessToken
            }

            is AuthResult.Failed -> {
                if (!result.retryable) {
                    _session.value = null
                    store.clear()
                }
                null
            }

            // Yenileme jetonu reddedildi ya da kullanıcının salon bağlantısı
            // kalktı: ikisi de tekrar denemekle düzelmiyor, oturum düşürülüyor.
            else -> {
                _session.value = null
                store.clear()
                null
            }
        }
    }

    private companion object {
        /** Jeton bitimine bu kadar kala yenilenir. */
        const val DEFAULT_REFRESH_MARGIN_MS = 60_000L
    }
}
