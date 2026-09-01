package com.gymapp.data.sync

import com.gymapp.data.auth.AuthApi
import com.gymapp.data.auth.AuthResult
import com.gymapp.data.auth.PasswordChange

/**
 * Sunucu ayarları verilmediğinde bağlanan uçlar.
 *
 * Proje adresi ve `anon` anahtarı `local.properties`ten okunuyor ve depoya
 * işlenmiyor; projeyi ilk kez klonlayan birinde bu değerler boş. O durumda
 * uygulamanın açılışta çökmesi ya da "bağlanılamadı" gibi ağ kaynaklıymış
 * izlenimi veren bir hata göstermesi yanlış olurdu — eksik olan şey ağ değil,
 * iki satır ayar.
 *
 * Bu uçlar aynı arayüzleri karşılıyor ama her çağrıda **ne yapılması gerektiğini
 * söyleyen** bir hata döndürüyor.
 */
const val SUNUCU_AYARI_EKSIK: String =
    "Sunucu ayarları eksik: local.properties dosyasına supabase.url ve " +
        "supabase.anonKey satırlarını ekleyip uygulamayı yeniden derleyin."

/** Giriş denemesi ağa hiç çıkmadan, açıklayıcı bir hatayla döner. */
class MissingConfigAuthApi(private val reason: String = SUNUCU_AYARI_EKSIK) : AuthApi {

    override suspend fun signIn(email: String, password: String): AuthResult =
        AuthResult.Failed(reason, retryable = false)

    override suspend fun refresh(refreshToken: String): AuthResult =
        AuthResult.Failed(reason, retryable = false)

    override suspend fun updatePassword(
        accessToken: String,
        newPassword: String,
    ): PasswordChange = PasswordChange.Failed(reason, retryable = false)
}

/**
 * Gönderim yapılmayan uzak uç.
 *
 * Sonuç **geçici** hata: ayar eklenip uygulama yeniden derlendiğinde kuyrukta
 * bekleyen kayıtlar olduğu gibi gönderilebilmeli. Kalıcı sayılsaydı her kayıt
 * hatalı işaretlenir ve `attemptCount` boş yere şişerdi.
 */
class DisabledRemoteDataSource(private val reason: String = SUNUCU_AYARI_EKSIK) : RemoteDataSource {

    override suspend fun push(table: SyncTable, entityId: String): PushResult =
        PushResult.Retryable(reason)
}
