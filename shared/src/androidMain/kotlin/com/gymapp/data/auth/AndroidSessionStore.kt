package com.gymapp.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Oturumu cihazda şifreli saklar.
 *
 * ### Neden şifreleme
 * Saklanan şey yenileme jetonu: onu ele geçiren biri, şifreyi bilmeden
 * kullanıcının yerine geçebilir. Uygulamaya özel `SharedPreferences` zaten başka
 * uygulamalar tarafından okunamıyor, ama cihaz yedeği ya da root erişimiyle
 * çıkarılabiliyor. Anahtar Android Keystore'da tutulduğu için — donanım destekli
 * cihazlarda anahtar hiç dışarı çıkmıyor — kopyalanan dosya başka bir cihazda
 * işe yaramıyor.
 *
 * ### Neden `EncryptedSharedPreferences` değil
 * `androidx.security:security-crypto` kullanımdan kaldırıldı. Aynı işi yapan
 * yaklaşık kırk satırı burada yazmak, bakımı bırakılmış bir bağımlılığa
 * bağlanmaktan iyi.
 *
 * ### Çözülemeyen veri hata değil
 * Anahtar geçersizleşebiliyor (cihaz kilidi sıfırlanması, yedekten farklı bir
 * cihaza dönüş). O durumda saklanan metin okunamaz hâle geliyor ve burası
 * istisna fırlatmak yerine `null` dönüp kaydı siliyor: sonuç kullanıcının tekrar
 * giriş yapması — uygulamanın açılışta çökmesi değil.
 */
class AndroidSessionStore(context: Context) : SessionStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_ADI, Context.MODE_PRIVATE)

    override suspend fun load(): Session? = withContext(Dispatchers.IO) {
        val saklanan = prefs.getString(ANAHTAR, null) ?: return@withContext null
        val cozulmus = coz(saklanan)
        if (cozulmus == null) {
            // Okunamayan kayıt temizleniyor: her açılışta aynı hatayı tekrar
            // denemenin bir faydası yok.
            prefs.edit().remove(ANAHTAR).apply()
            return@withContext null
        }
        SessionCodec.decode(cozulmus)
    }

    override suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        val sifreli = sifrele(SessionCodec.encode(session))
        if (sifreli != null) {
            prefs.edit().putString(ANAHTAR, sifreli).apply()
        } else {
            // Şifrelenemiyorsa **düz metin yazılmıyor**. Bedeli: uygulama
            // kapandığında oturum kayboluyor. Alternatifi jetonu korumasız
            // bırakmak olurdu ve bu, kalıcılık uğruna güvenliği vermek demekti.
            prefs.edit().remove(ANAHTAR).apply()
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(ANAHTAR).apply()
    }

    // ─── Şifreleme ──────────────────────────────────────────────────────────

    private fun sifrele(duzMetin: String): String? = runCatching {
        val cipher = Cipher.getInstance(DONUSUM)
        cipher.init(Cipher.ENCRYPT_MODE, anahtar())
        val govde = cipher.doFinal(duzMetin.encodeToByteArray())
        // Başlangıç vektörü her şifrelemede yeniden üretiliyor ve gövdeyle
        // birlikte saklanıyor; gizli değil, ama tekrar kullanılmaması şart.
        base64(cipher.iv) + AYIRAC + base64(govde)
    }.getOrNull()

    private fun coz(saklanan: String): String? = runCatching {
        val parcalar = saklanan.split(AYIRAC)
        if (parcalar.size != 2) return null

        val cipher = Cipher.getInstance(DONUSUM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            anahtar(),
            GCMParameterSpec(ETIKET_BITI, base64Coz(parcalar[0])),
        )
        cipher.doFinal(base64Coz(parcalar[1])).decodeToString()
    }.getOrNull()

    /**
     * Keystore'daki anahtarı döndürür, yoksa üretir.
     *
     * `setUserAuthenticationRequired` bilinçli olarak **kapalı**: açık olsaydı
     * anahtar yalnızca cihaz kilidi açıldıktan sonra kullanılabilirdi ve
     * arkaplandaki senkronizasyon turu jetona erişemezdi.
     */
    private fun anahtar(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ANAHTAR_ADI, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }

        val uretici = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        uretici.init(
            KeyGenParameterSpec.Builder(
                ANAHTAR_ADI,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return uretici.generateKey()
    }

    private fun base64(veri: ByteArray): String = Base64.encodeToString(veri, Base64.NO_WRAP)
    private fun base64Coz(metin: String): ByteArray = Base64.decode(metin, Base64.NO_WRAP)

    private companion object {
        const val PREFS_ADI = "gym_session"
        const val ANAHTAR = "session_v1"
        const val ANAHTAR_ADI = "gym_session_key"
        const val KEYSTORE = "AndroidKeyStore"
        const val DONUSUM = "AES/GCM/NoPadding"
        const val ETIKET_BITI = 128
        const val AYIRAC = ":"
    }
}
