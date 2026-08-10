package com.gymapp.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Uzak ucun davranışı — ağ olmadan, sahte HTTP motoruyla.
 *
 * Buradaki asıl konu durum kodu → [PushResult] eşlemesi. Yanlış bir eşleme
 * sessizdir ve iki yönde de zarar verir: geçici bir hataya "kalıcı" demek kaydı
 * fiilen ölü bırakır, kalıcı bir hataya "geçici" demek turu her seferinde aynı
 * kayıtta durdurup arkasındaki her şeyi bekletir.
 *
 * İsteğin **şekli** (başlıklar, upsert tercihi) burada da kontrol ediliyor ama
 * gerçek bir sunucunun onu kabul ettiği ancak PostgREST'e karşı koşan test ile
 * kanıtlanır; ikisi birbirinin yerine geçmiyor.
 */
class SupabaseRemoteDataSourceTest {

    private val config = SupabaseConfig(url = "https://ornek.supabase.co", anonKey = "anon-anahtar")
    private val payload = JsonObject(mapOf("id" to JsonPrimitive("p-1")))

    private fun kaynak(
        status: HttpStatusCode,
        body: String = "",
        token: String? = "jwt-jeton",
        satirVar: Boolean = true,
        yakala: ((HttpRequestData) -> Unit)? = null,
    ): SupabaseRemoteDataSource {
        val engine = MockEngine { request ->
            yakala?.invoke(request)
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        return SupabaseRemoteDataSource(
            config = config,
            httpClient = HttpClient(engine),
            tokens = { token },
            payloads = { _, _ -> if (satirVar) payload else null },
        )
    }

    // ─── Durum kodu eşlemesi ────────────────────────────────────────────────

    @Test
    fun `basarili yanit basari doner`() = runTest {
        assertEquals(PushResult.Success, kaynak(HttpStatusCode.Created).push(SyncTable.PRODUCTS, "p-1"))
        assertEquals(PushResult.Success, kaynak(HttpStatusCode.OK).push(SyncTable.PRODUCTS, "p-1"))
        assertEquals(PushResult.Success, kaynak(HttpStatusCode.NoContent).push(SyncTable.PRODUCTS, "p-1"))
    }

    /**
     * 401 **geçici**: jeton yenilendikten sonra aynı istek başarılı olur, yani
     * kaydın kendisiyle ilgili kalıcı bir sorun yok. Kalıcı sayılsaydı oturumu
     * kısa süreliğine düşen bir cihazın bekleyen bütün değişiklikleri hatalı
     * işaretlenirdi.
     */
    @Test
    fun `oturum gecersizse gecici hata doner`() = runTest {
        val sonuc = kaynak(HttpStatusCode.Unauthorized).push(SyncTable.PRODUCTS, "p-1")
        assertIs<PushResult.Retryable>(sonuc)
        assertTrue(sonuc.reason.contains("401"))
    }

    /**
     * 403 **kalıcı**: erişim kuralları reddetti, aynı istek aynı sonucu verir.
     * Kayıt kuyrukta kalıp işaretleniyor ki "kullanıcı gym_users'a eklenmemiş"
     * teşhis edilebilsin — sessizce silmek hem veri hem teşhis kaybı olurdu.
     */
    @Test
    fun `erisim reddedilirse kalici hata doner`() = runTest {
        val sonuc = kaynak(
            HttpStatusCode.Forbidden,
            body = """{"message":"new row violates row-level security policy"}""",
        ).push(SyncTable.PRODUCTS, "p-1")
        assertIs<PushResult.Permanent>(sonuc)
        assertTrue(sonuc.reason.contains("403"))
        assertTrue(sonuc.reason.contains("row-level security"), "Teşhis için sunucu mesajı taşınmalı")
    }

    /**
     * Yalnızca tipe bakmak yetmiyor — gerekçe de kontrol ediliyor.
     *
     * İlk hâlleri `assertIs<Retryable>` ile yetiniyordu ve tam da bu yüzden
     * **yanlış sebeple geçtiler**: istek serileştirme hatasıyla motora hiç
     * ulaşmıyordu, dönen `Retryable` ağ hatası dalından geliyordu. Tip doğruydu,
     * yol tamamen yanlıştı. Gerekçede durum kodunu aramak bu yolu kapatıyor.
     */
    @Test
    fun `sunucu mesgulse gecici hata doner`() = runTest {
        gecici(HttpStatusCode.RequestTimeout, "408")
        gecici(HttpStatusCode.TooManyRequests, "429")
    }

    @Test
    fun `sunucu hatasi gecici sayilir`() = runTest {
        gecici(HttpStatusCode.InternalServerError, "500")
        gecici(HttpStatusCode.BadGateway, "502")
        gecici(HttpStatusCode.ServiceUnavailable, "503")
    }

    @Test
    fun `bozuk istek kalici sayilir`() = runTest {
        kalici(HttpStatusCode.BadRequest, "400")
        kalici(HttpStatusCode.NotFound, "404")
        kalici(HttpStatusCode.Conflict, "409")
    }

    private suspend fun gecici(status: HttpStatusCode, kod: String) {
        val sonuc = kaynak(status).push(SyncTable.PRODUCTS, "p-1")
        assertIs<PushResult.Retryable>(sonuc, "$kod geçici sayılmalı")
        assertTrue(
            sonuc.reason.contains(kod),
            "Gerekçe $kod içermeli, ağ hatası dalından gelmemeli — gelen: ${sonuc.reason}",
        )
    }

    private suspend fun kalici(status: HttpStatusCode, kod: String) {
        val sonuc = kaynak(status).push(SyncTable.PRODUCTS, "p-1")
        assertIs<PushResult.Permanent>(sonuc, "$kod kalıcı sayılmalı")
        assertTrue(
            sonuc.reason.contains(kod),
            "Gerekçe $kod içermeli — gelen: ${sonuc.reason}",
        )
    }

    // ─── Ön koşullar ────────────────────────────────────────────────────────

    /** Oturum yoksa istek hiç gönderilmemeli; kayıt kuyrukta kalır. */
    @Test
    fun `oturum yoksa istek gonderilmez`() = runTest {
        var istekGitti = false
        val sonuc = kaynak(HttpStatusCode.OK, token = null, yakala = { istekGitti = true })
            .push(SyncTable.PRODUCTS, "p-1")

        assertIs<PushResult.Retryable>(sonuc)
        assertTrue(!istekGitti, "Jeton yokken ağa çıkılmamalı")
    }

    /**
     * Satır yerelde yoksa kalıcı hata.
     *
     * Tasarım gereği olmaması gereken durum — silmeler tombstone ile yapılıyor.
     * Bu yüzden sessizce başarı saymak yerine işaretleniyor: gerçekleşirse bir
     * hatanın belirtisidir ve görünür olmalı.
     */
    @Test
    fun `satir yoksa kalici hata doner`() = runTest {
        val sonuc = kaynak(HttpStatusCode.OK, satirVar = false).push(SyncTable.PRODUCTS, "p-1")
        assertIs<PushResult.Permanent>(sonuc)
        assertTrue(sonuc.reason.contains("bulunamadı"))
    }

    // ─── İsteğin şekli ──────────────────────────────────────────────────────

    @Test
    fun `istek dogru uc noktaya ve basliklarla gider`() = runTest {
        var istek: HttpRequestData? = null
        kaynak(HttpStatusCode.Created, yakala = { istek = it }).push(SyncTable.MEMBERS, "u-1")

        val r = requireNotNull(istek)
        assertEquals("https://ornek.supabase.co/rest/v1/gym_members", r.url.toString())
        assertEquals("anon-anahtar", r.headers["apikey"])
        assertEquals("Bearer jwt-jeton", r.headers["Authorization"])
        // Upsert şart: aynı kayıt tekrar gönderilebilmeli. Olmasaydı ikinci
        // deneme birincil anahtar çakışmasıyla düşer ve kalıcı hata sayılırdı.
        assertTrue(r.headers["Prefer"]!!.contains("resolution=merge-duplicates"))
    }

    @Test
    fun `ag hatasi gecici sayilir`() = runTest {
        val kopukMotor = MockEngine { throw java.io.IOException("bağlantı koptu") }
        val kaynak = SupabaseRemoteDataSource(
            config = config,
            httpClient = HttpClient(kopukMotor),
            tokens = { "jwt" },
            payloads = { _, _ -> payload },
        )

        val sonuc = kaynak.push(SyncTable.PRODUCTS, "p-1")

        assertIs<PushResult.Retryable>(sonuc)
        assertTrue(sonuc.reason.contains("Ağ hatası"))
        // İstisna tipi gerekçede olmalı: ağ dışı bir hata buraya düştüğünde
        // teşhis edilebilmesinin tek yolu bu.
        assertTrue(sonuc.reason.contains("IOException"), "İstisna tipi taşınmalı: ${sonuc.reason}")
    }
}
