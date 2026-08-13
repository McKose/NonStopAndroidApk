package com.gymapp.data.auth

/**
 * Giriş yapmış kullanıcının oturumu.
 *
 * [tenantId] uygulamanın her satırda taşıdığı salon kimliği ve **sunucudan**
 * geliyor, uygulamaya gömülmüyor. Nedeni: kullanıcı–salon eşlemesi sunucunun
 * bilgisi (`gym_users` tablosu) ve erişim kuralları da ona dayanıyor. Gömülü bir
 * değer, aynı APK ikinci bir salona kurulduğunda sessizce yanlış olurdu — yazma
 * denemeleri sunucuda reddedilirdi ama uygulama bunu ancak kuyruk dolduğunda
 * fark ederdi.
 */
data class Session(
    val accessToken: String,
    val refreshToken: String,

    /**
     * Erişim jetonunun geçersizleşeceği an (epoch ms).
     *
     * Sunucunun döndürdüğü mutlak `expires_at` yerine `expires_in` süresi yerel
     * saate eklenerek hesaplanıyor. Böylece karşılaştırmanın iki tarafı da aynı
     * saati kullanıyor: cihaz saati sunucudan sapmışsa mutlak değer jetonu ya
     * erken çöpe attırır ya da süresi dolmuş jetonla istek yaptırırdı.
     */
    val expiresAtMs: Long,

    val userId: String,
    val email: String,

    /** Kullanıcının bağlı olduğu salon; uygulamadaki `tenantId` bu değerdir. */
    val tenantId: String,
)

/**
 * Giriş ya da jeton yenileme denemesinin sonucu.
 *
 * Durumlar bilinçli olarak ayrı: hepsi tek bir "başarısız" altında toplansaydı
 * kullanıcıya gösterilecek mesaj da tek olurdu ve bu mesaj çoğu zaman yanlış
 * yönlendirirdi. Özellikle [NoGym] gerçek bir kurulum sırasında yaşandı — kimlik
 * doğrulama sorunsuz çalışıyordu, eksik olan tek şey `gym_users` satırıydı; "giriş
 * başarısız" demek kullanıcıyı şifresini aramaya gönderirdi.
 */
sealed interface AuthResult {

    data class Success(val session: Session) : AuthResult

    /** E-posta ya da şifre yanlış; hesap onaylanmamış da olabilir. */
    data class InvalidCredentials(val reason: String) : AuthResult

    /**
     * Kimlik doğrulandı ama kullanıcı hiçbir salona bağlı değil.
     *
     * Sunucu tarafında `gym_users` satırı eksik demek. Erişim kuralları
     * "bağlı olduğun salonun satırları" dediği için bu kullanıcı giriş yapabilir
     * ama hiçbir veri göremez — hata değil, eksik kurulum.
     */
    data class NoGym(val userId: String) : AuthResult

    /**
     * Kullanıcı birden fazla salona bağlı.
     *
     * Bugün desteklenmiyor: hangi salonda çalışıldığını sormak bir ekran
     * gerektiriyor ve tek salonlu kurulumda karşılığı yok. Sessizce ilkini
     * seçmek yanlış salona veri yazmakla sonuçlanabilirdi, o yüzden açıkça
     * bildiriliyor.
     */
    data class MultipleGyms(val gymIds: List<String>) : AuthResult

    /**
     * Diğer hatalar.
     *
     * @param retryable ağ ya da sunucu kaynaklıysa `true` — tekrar denemek
     *        anlamlı. Kullanıcının düzeltmesi gereken bir durumda `false`.
     */
    data class Failed(val reason: String, val retryable: Boolean) : AuthResult
}

/** Kimlik doğrulama ucu. Uygulaması [SupabaseAuthApi]. */
interface AuthApi {
    suspend fun signIn(email: String, password: String): AuthResult

    /** Erişim jetonunu yeniler; yenileme jetonu da tazelenir. */
    suspend fun refresh(refreshToken: String): AuthResult
}

/**
 * Oturumun cihazda saklandığı yer.
 *
 * Arayüz olarak duruyor çünkü saklama platforma özgü ve güvenlik açısından
 * hassas: Android'de şifreli tercih dosyası, iOS'ta Keychain. `expect`/`actual`
 * yerine arayüz tercih edildi — bu projede elle kurulan her kaynak kümesi kenarı
 * Kotlin'in varsayılan hiyerarşi şablonunu bozma riski taşıyor (bkz.
 * `shared/build.gradle.kts`), arayüz ise o riski hiç doğurmuyor.
 */
interface SessionStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

/**
 * Bellekte tutan saklama — testler ve kalıcı saklama gelene kadar varsayılan.
 *
 * Sonucu: uygulama kapanınca oturum kayboluyor ve tekrar giriş isteniyor. Bilinçli
 * bir ara durum; yenileme jetonunu düz metin bir tercih dosyasına yazmak, kalıcılığı
 * kazanmak için güvenliği kaybetmek olurdu.
 */
class InMemorySessionStore : SessionStore {
    private var session: Session? = null

    override suspend fun load(): Session? = session
    override suspend fun save(session: Session) { this.session = session }
    override suspend fun clear() { session = null }
}
