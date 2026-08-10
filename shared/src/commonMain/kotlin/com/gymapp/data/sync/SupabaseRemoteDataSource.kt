package com.gymapp.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

/**
 * Satırın sunucuya gönderilecek hâli.
 *
 * Kuyruk kaydı yalnızca bir işaretçi; gönderim anında satırın **güncel** hâli
 * yerelden okunuyor. Okuma ve JSON'a çevirme ayrı bir arayüzde çünkü tablo
 * sayısı kadar eşleme var ve bu, gönderimin kendisiyle ilgisiz bir sorumluluk.
 *
 * `null` dönmesi "satır yok" demek. Tasarım gereği olmaması gereken bir durum:
 * silmeler tombstone ile yapıldığı için satırlar yerinde kalıyor.
 */
fun interface RowPayloadProvider {
    suspend fun payload(table: SyncTable, entityId: String): JsonObject?
}

/**
 * Supabase (PostgREST) üzerine yazan uzak uç.
 *
 * Gönderim **upsert**: aynı satır birden çok kez gönderilebilir ve sonuç
 * değişmez. Bu şart, kuyruğun doğası gereği — bir kayıt gönderilip yanıt
 * kaybolduğunda tekrar denenir; upsert olmasaydı ikinci deneme birincil anahtar
 * çakışmasıyla düşer ve kalıcı hata sayılırdı.
 *
 * `Prefer: resolution=merge-duplicates` PostgREST'in upsert anahtarı,
 * `return=minimal` ise yanıt gövdesini boş bırakıp gereksiz veri transferini
 * önlüyor.
 */
class SupabaseRemoteDataSource(
    private val config: SupabaseConfig,
    private val httpClient: HttpClient,
    private val tokens: AccessTokenProvider,
    private val payloads: RowPayloadProvider,
) : RemoteDataSource {

    override suspend fun push(table: SyncTable, entityId: String): PushResult {
        val token = tokens.currentAccessToken()
            ?: return PushResult.Retryable("Oturum yok")

        val payload = payloads.payload(table, entityId)
            ?: return PushResult.Permanent(
                "Satır yerelde bulunamadı: ${table.tableName}/$entityId"
            )

        return try {
            val response = httpClient.post(config.tableEndpoint(table)) {
                header("apikey", config.anonKey)
                header("Authorization", "Bearer $token")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            pushResultForStatus(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        } catch (e: Exception) {
            // Ağ yok, DNS çözülmedi, bağlantı koptu, zaman aşımı. Hepsi geçer;
            // kayıt kuyrukta kalıp sonraki turda tekrar denenir.
            //
            // `Exception` bilinçli olarak geniş: Ktor'un her motoru (OkHttp,
            // Darwin) kendi istisna tiplerini fırlatıyor ve bunları tek tek
            // saymak, yeni bir platform eklendiğinde sessizce eksik kalırdı.
            PushResult.Retryable("Ağ hatası: ${e.message ?: e::class.simpleName}")
        }
    }
}
