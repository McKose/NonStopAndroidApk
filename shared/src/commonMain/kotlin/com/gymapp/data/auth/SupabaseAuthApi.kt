package com.gymapp.data.auth

import com.gymapp.data.sync.SupabaseConfig
import com.gymapp.domain.Now
import com.gymapp.domain.StaffRole
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Supabase Auth (GoTrue) üzerine kimlik doğrulama.
 *
 * ### Bir giriş, iki istek
 * Giriş aslında iki sunucu çağrısı: önce jeton alınıyor, sonra kullanıcının hangi
 * salona bağlı olduğu `gym_users`'tan okunuyor. İkincisi dışarı ayrı bir adım
 * olarak sızdırılmıyor — çağıran için "giriş" tek bir işlem ve salonu bilinmeyen
 * bir oturumun hiçbir işe yaramadığı doğru: `tenantId` olmadan ne veri okunabilir
 * ne yazılabilir. Bu yüzden ikinci çağrı sonuçsuz kaldığında giriş de başarılı
 * sayılmıyor.
 *
 * ### JSON neden elle ayrıştırılıyor
 * Ktor'un içerik dönüştürücüsü (ContentNegotiation) bilinçli olarak kurulmuyor;
 * uzak uç da aynı tercihle yazıldı (bkz. `SupabaseRemoteDataSource`). Dönüştürücü
 * kurulmadığında ortaya çıkan hata serileştirme aşamasında patlıyor ve geniş bir
 * `catch` tarafından "ağ hatası" sanılıyor — bu tuzağa bu projede bir kez
 * düşüldü. Gövdeyi metin olarak alıp [Json.parseToJsonElement] ile okumak,
 * istemcinin nasıl kurulduğuna olan bağımlılığı tamamen kaldırıyor.
 */
class SupabaseAuthApi(
    private val config: SupabaseConfig,
    private val httpClient: HttpClient,
    private val now: () -> Long = { Now.epochMillis() },
) : AuthApi {

    // Uç noktalar tek yerde: hata mesajı hangi isteğin düştüğünü söyleyebilsin.
    //
    // Giriş iki istek ve ikisi de aynı hata biçimini üretiyordu. Gerçek bir
    // kurulumda "Beklenmeyen yanıt (404): Invalid path specified in request URL"
    // alındığında hangi çağrının 404 verdiği — jeton mu, salon araması mı —
    // mesajdan anlaşılamıyordu; ikisi farklı sebeplere işaret ediyor.
    private val jetonUcu = "${config.url}/auth/v1/token"
    private val salonUcu = "${config.url}/rest/v1/gym_users"

    override suspend fun signIn(email: String, password: String): AuthResult =
        token(
            grantType = "password",
            body = buildJsonObject {
                put("email", email)
                put("password", password)
            },
        )

    override suspend fun refresh(refreshToken: String): AuthResult =
        token(
            grantType = "refresh_token",
            body = buildJsonObject { put("refresh_token", refreshToken) },
        )

    private suspend fun token(grantType: String, body: JsonObject): AuthResult {
        val response = try {
            httpClient.post("${config.url}/auth/v1/token?grant_type=$grantType") {
                header("apikey", config.anonKey)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (e: Exception) {
            return networkFailure(e)
        }

        val status = response.status.value
        val text = runCatching { response.bodyAsText() }.getOrDefault("")

        if (status !in 200..299) return failureFor(status, text, jetonUcu)

        val json = text.asJsonObject()
            ?: return AuthResult.Failed("Sunucu yanıtı okunamadı.", retryable = false)

        val accessToken = json.string("access_token")
            ?: return AuthResult.Failed("Sunucu yanıtında erişim jetonu yok.", retryable = false)
        val refreshToken = json.string("refresh_token")
            ?: return AuthResult.Failed("Sunucu yanıtında yenileme jetonu yok.", retryable = false)
        val user = json["user"]?.asJsonObject()
            ?: return AuthResult.Failed("Sunucu yanıtında kullanıcı bilgisi yok.", retryable = false)
        val userId = user.string("id")
            ?: return AuthResult.Failed("Sunucu yanıtında kullanıcı kimliği yok.", retryable = false)

        // Süre okunamazsa sıfır kabul ediliyor: jeton ilk kullanımda yenilenmeye
        // çalışılır. Alternatif — süresiz geçerli saymak — her isteğin 401
        // almasıyla ve hiç düzelmeyen bir oturumla sonuçlanırdı.
        val expiresInSec = json.string("expires_in")?.toLongOrNull() ?: 0L

        return when (val gym = resolveGym(accessToken)) {
            is GymLookup.Found -> AuthResult.Success(
                Session(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMs = now() + expiresInSec * 1000,
                    userId = userId,
                    email = user.string("email").orEmpty(),
                    tenantId = gym.gymId,
                    role = gym.role,
                )
            )

            GymLookup.None -> AuthResult.NoGym(userId)
            is GymLookup.Many -> AuthResult.MultipleGyms(gym.gymIds)
            is GymLookup.Error -> gym.result
        }
    }

    /**
     * Kullanıcının salonunu okur.
     *
     * Sorguda `user_id` koşulu **yok**: `gym_users` üzerindeki erişim kuralı
     * zaten "yalnızca kendi bağlılıkların" diyor ve sunucu başka satır
     * döndürmüyor. Koşulu ayrıca yazmak, yalıtımı sağlayan şeyin sorgu olduğu
     * izlenimini verirdi; sağlayan şey kural. Sorgu unutulsa bile sızıntı olmaz,
     * kural unutulsa koşul kurtarmaz.
     */
    private suspend fun resolveGym(accessToken: String): GymLookup {
        val response = try {
            httpClient.get("${config.url}/rest/v1/gym_users?select=gym_id,role") {
                header("apikey", config.anonKey)
                header("Authorization", "Bearer $accessToken")
            }
        } catch (e: Exception) {
            return GymLookup.Error(networkFailure(e))
        }

        val status = response.status.value
        val text = runCatching { response.bodyAsText() }.getOrDefault("")

        if (status !in 200..299) return GymLookup.Error(failureFor(status, text, salonUcu))

        val rows = runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
                val row = element.jsonObject
                val gymId = row.string("gym_id") ?: return@mapNotNull null
                // Tanınmayan rol en dar yetkiye düşüyor: bir yazım hatasının
                // yönetici yetkisi vermesi, yetki vermemesinden çok daha kötü.
                val role = runCatching { StaffRole.valueOf(row.string("role").orEmpty()) }
                    .getOrDefault(StaffRole.TRAINER)
                gymId to role
            }
        }.getOrElse {
            return GymLookup.Error(
                AuthResult.Failed("Salon bilgisi okunamadı: ${text.take(200)}", retryable = false)
            )
        }

        return when (rows.size) {
            0 -> GymLookup.None
            1 -> GymLookup.Found(rows.single().first, rows.single().second)
            else -> GymLookup.Many(rows.map { it.first })
        }
    }

    private sealed interface GymLookup {
        data class Found(val gymId: String, val role: StaffRole) : GymLookup
        data object None : GymLookup
        data class Many(val gymIds: List<String>) : GymLookup
        data class Error(val result: AuthResult) : GymLookup
    }

    private fun networkFailure(e: Exception) = AuthResult.Failed(
        "Ağ hatası (${e::class.simpleName}): ${e.message ?: "-"}",
        retryable = true,
    )

    /**
     * Durum kodunu sonuca çevirir.
     *
     * `400` ve `401` kimlik bilgisi hatası sayılıyor: GoTrue yanlış şifreye de,
     * onaylanmamış hesaba da bu kodları döndürüyor. Ayrımı sunucunun mesajı
     * taşıdığı için mesaj olduğu gibi aktarılıyor — kullanıcı açısından
     * "Invalid login credentials" ile "Email not confirmed" tamamen farklı iki iş:
     * biri şifreyi aratır, diğeri panelden hesap onaylatır.
     *
     * ### İstek adresi neden mesaja giriyor
     * Kimlik hatası dışındaki durumlarda [uc] mesaja ekleniyor. Sebebi somut:
     * giriş iki ayrı isteğe dayanıyor ve ikisi de aynı biçimde hata üretiyordu,
     * dolayısıyla bir 404 alındığında hangi çağrının düştüğü bilinemiyordu.
     * Adres gizli bir şey değil (anahtar ve başlıklar mesaja **girmiyor**), ama
     * yanlış yapılandırılmış bir sunucu adresini tek bakışta görünür kılıyor.
     *
     * Kimlik hatasında (400/401) adres bilinçli olarak eklenmiyor: orada sorun
     * neredeyse her zaman şifre ve teknik ayrıntı yalnızca gürültü olurdu.
     */
    private fun failureFor(status: Int, raw: String, uc: String): AuthResult {
        val json = raw.asJsonObject()
        val message = json?.let {
            it.string("error_description")
                ?: it.string("msg")
                ?: it.string("message")
                ?: it.string("error")
        } ?: raw.take(200).ifBlank { "-" }

        // 404 bu iki uç noktada neredeyse her zaman tek bir şey demek: sunucu
        // adresi yanlış. Proje adresi yerine panonun (dashboard) adresini ya da
        // sonuna bir yol eklenmiş bir değeri girmek en sık karşılaşılanı.
        val ipucu = if (status == 404) {
            " — sunucu adresi yanlış olabilir: yalnızca `https://<proje>.supabase.co` " +
                "olmalı, sonunda yol olmadan."
        } else {
            ""
        }

        return when {
            status == 400 || status == 401 -> AuthResult.InvalidCredentials(message)
            status == 403 ->
                AuthResult.Failed("Erişim reddedildi: $message [$uc]", retryable = false)
            status == 408 || status == 429 ->
                AuthResult.Failed("Sunucu meşgul ($status): $message [$uc]", retryable = true)
            status in 500..599 ->
                AuthResult.Failed("Sunucu hatası ($status): $message [$uc]", retryable = true)
            else -> AuthResult.Failed(
                "Beklenmeyen yanıt ($status): $message [$uc]$ipucu",
                retryable = false,
            )
        }
    }
}

// ─── Yanıt okuma yardımcıları ──────────────────────────────────────────────
//
// Hepsi hataya dayanıklı: beklenmeyen bir gövde istisna fırlatmak yerine `null`
// üretiyor. Sunucunun yanıt biçimi bizim denetimimizde değil ve bir alanın
// eksikliği çökmeye değil, anlaşılır bir hata mesajına dönüşmeli.

private fun String.asJsonObject(): JsonObject? =
    runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull()

private fun JsonElement.asJsonObject(): JsonObject? =
    runCatching { jsonObject }.getOrNull()

/** Alanı metin olarak okur; sayılar da metne çevrilir. Boş değer `null` sayılır. */
private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
