package com.gymapp.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Supabase (PostgREST) üzerinden değişiklik okuyan uç.
 *
 * ### Sorgunun şekli
 * `{delta}=gte.{since}` — su işaretine **eşit** olanlar da isteniyor. `gt`
 * olsaydı aynı milisaniyede yazılmış iki satırdan biri su işaretine eşit kalır
 * ve bir daha hiç gelmezdi.
 *
 * `order={delta}.asc,id.asc` — sayfalama zamana göre ilerliyor, dolayısıyla
 * sıralama zorunlu. `id` ikincil anahtar olarak ekli: aynı damgalı satırların
 * sırası sayfadan sayfaya değişmesin.
 *
 * Salon süzgeci sorguya **yazılmıyor**: erişim kuralları zaten yalnızca
 * kullanıcının salonunun satırlarını döndürüyor. Koşulu ayrıca yazmak,
 * yalıtımı sağlayan şeyin sorgu olduğu izlenimini verirdi.
 *
 * ### Silinen satırlar da geliyor
 * Tombstone'lar (`deleted_at_ms` dolu) süzülmüyor: silme de inmesi gereken bir
 * değişiklik. Süzülseydi bir cihazda silinen üye diğerinde sonsuza kadar
 * görünmeye devam ederdi.
 */
class SupabaseRemoteReader(
    private val config: SupabaseConfig,
    private val httpClient: HttpClient,
    private val tokens: AccessTokenProvider,
) : RemoteReader {

    override suspend fun fetchChanges(
        table: SyncTable,
        sinceMs: Long,
        limit: Int,
    ): FetchResult {
        val token = tokens.currentAccessToken()
            ?: return FetchResult.Retryable("Oturum yok")

        val url = buildString {
            append(config.tableEndpoint(table))
            append("?select=*")
            append("&${table.deltaColumn}=gte.$sinceMs")
            append("&order=${table.deltaColumn}.asc,id.asc")
            append("&limit=$limit")
        }

        val response = try {
            httpClient.get(url) {
                header("apikey", config.anonKey)
                header("Authorization", "Bearer $token")
            }
        } catch (e: Exception) {
            // Gönderim tarafındaki gerekçeyle aynı: her motor kendi istisna
            // tiplerini fırlatıyor, tek tek saymak yeni bir platformda sessizce
            // eksik kalırdı. İstisna tipi gerekçeye yazılıyor ki ağ dışı bir
            // hata buraya düştüğünde teşhis edilebilsin.
            return FetchResult.Retryable("Ağ hatası (${e::class.simpleName}): ${e.message ?: "-"}")
        }

        val status = response.status.value
        val body = runCatching { response.bodyAsText() }.getOrDefault("")

        if (status !in 200..299) {
            // Durum kodu eşlemesi gönderimle aynı mantıkta: 401 geçici (jeton
            // yenilenince düzelir), 403 kalıcı (erişim kuralları reddetti).
            return when (val sonuc = pushResultForStatus(status, body)) {
                is PushResult.Retryable -> FetchResult.Retryable(sonuc.reason)
                is PushResult.Permanent -> FetchResult.Permanent(sonuc.reason)
                PushResult.Success -> FetchResult.Permanent("Beklenmeyen durum ($status)")
            }
        }

        val rows = runCatching {
            Json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
        }.getOrElse {
            return FetchResult.Permanent("Sunucu yanıtı okunamadı: ${body.take(200)}")
        }

        return FetchResult.Rows(rows)
    }
}

/**
 * Sunucu ayarları verilmediğinde bağlanan okuyucu.
 *
 * Sonuç **geçici** hata: ayar eklendiğinde çekme kaldığı yerden devam etmeli.
 */
class DisabledRemoteReader(private val reason: String = SUNUCU_AYARI_EKSIK) : RemoteReader {
    override suspend fun fetchChanges(table: SyncTable, sinceMs: Long, limit: Int): FetchResult =
        FetchResult.Retryable(reason)
}
