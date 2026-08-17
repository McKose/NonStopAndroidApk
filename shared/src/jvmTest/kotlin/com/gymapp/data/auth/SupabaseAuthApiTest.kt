package com.gymapp.data.auth

import com.gymapp.data.sync.SupabaseConfig
import com.gymapp.domain.StaffRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Girişin davranışı — ağ olmadan, sahte HTTP motoruyla.
 *
 * Asıl konu sunucu yanıtı → [AuthResult] eşlemesi. Yanlış bir eşleme burada
 * doğrudan kullanıcıya yansıyor: "giriş başarısız" diyen bir mesaj, sorun aslında
 * eksik bir `gym_users` satırıyken kullanıcıyı şifresini aramaya gönderir. Bu tam
 * olarak gerçek bir kurulumda yaşandı, o yüzden her durum ayrı sınanıyor.
 */
class SupabaseAuthApiTest {

    private val config = SupabaseConfig(url = "https://ornek.supabase.co", anonKey = "anon-anahtar")

    private val gecerliJetonYaniti = """
        {
          "access_token": "erisim-jetonu",
          "refresh_token": "yenileme-jetonu",
          "expires_in": 3600,
          "user": { "id": "kullanici-1", "email": "personel@ornek.com" }
        }
    """.trimIndent()

    /**
     * İki uç noktaya farklı yanıt veren motor.
     *
     * Giriş tek bir işlem gibi görünse de iki istek: jeton ve salon araması.
     * İkisini ayrı ayrı yönlendirmek, "jeton alındı ama salon bulunamadı"
     * durumunu sınamayı mümkün kılan şey.
     */
    private fun api(
        jetonDurumu: HttpStatusCode = HttpStatusCode.OK,
        jetonGovdesi: String = gecerliJetonYaniti,
        salonDurumu: HttpStatusCode = HttpStatusCode.OK,
        salonGovdesi: String = """[{"gym_id":"salon-1","role":"MANAGER"}]""",
        simdi: Long = 1_000_000L,
        yakala: ((HttpRequestData) -> Unit)? = null,
    ): SupabaseAuthApi {
        val engine = MockEngine { request ->
            yakala?.invoke(request)
            val salonIstegi = request.url.encodedPath.contains("gym_users")
            respond(
                content = ByteReadChannel(if (salonIstegi) salonGovdesi else jetonGovdesi),
                status = if (salonIstegi) salonDurumu else jetonDurumu,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        return SupabaseAuthApi(config, HttpClient(engine), now = { simdi })
    }

    // ─── Başarılı giriş ─────────────────────────────────────────────────────

    @Test
    fun `basarili giriste oturum salon kimligini tasir`() = runTest {
        val sonuc = api().signIn("personel@ornek.com", "sifre")

        val oturum = assertIs<AuthResult.Success>(sonuc).session
        assertEquals("erisim-jetonu", oturum.accessToken)
        assertEquals("yenileme-jetonu", oturum.refreshToken)
        assertEquals("kullanici-1", oturum.userId)
        assertEquals("personel@ornek.com", oturum.email)
        assertEquals("salon-1", oturum.tenantId)
        assertEquals(StaffRole.MANAGER, oturum.role)
    }

    /**
     * Tanınmayan rol **en dar** yetkiye düşüyor.
     *
     * Ters kurgu — bilinmeyeni yönetici saymak — sunucudaki tek harflik bir
     * yazım hatasının herkese yönetici yetkisi vermesi demek olurdu.
     */
    @Test
    fun `taninmayan rol en dar yetkiye duser`() = runTest {
        val sonuc = api(salonGovdesi = """[{"gym_id":"salon-1","role":"SUPERADMIN"}]""")
            .signIn("personel@ornek.com", "sifre")

        assertEquals(StaffRole.TRAINER, assertIs<AuthResult.Success>(sonuc).session.role)
    }

    /** Rol alanı hiç gelmezse de en dar yetki. */
    @Test
    fun `rol alani yoksa en dar yetkiye duser`() = runTest {
        val sonuc = api(salonGovdesi = """[{"gym_id":"salon-1"}]""")
            .signIn("personel@ornek.com", "sifre")

        assertEquals(StaffRole.TRAINER, assertIs<AuthResult.Success>(sonuc).session.role)
    }

    /**
     * Geçerlilik anı **yerel** saatten hesaplanıyor.
     *
     * Sunucunun döndürdüğü mutlak `expires_at` kullanılsaydı, karşılaştırmanın
     * iki tarafı farklı saatlerden gelirdi: cihaz saati geride olan bir telefon
     * jetonu süresi dolmuş sanıp durmadan yeniler, ileride olan bir telefon ise
     * ölü jetonla istek yapardı.
     */
    @Test
    fun `gecerlilik ani yerel saate gore hesaplanir`() = runTest {
        val sonuc = api(simdi = 1_000_000L).signIn("personel@ornek.com", "sifre")

        val oturum = assertIs<AuthResult.Success>(sonuc).session
        assertEquals(1_000_000L + 3_600_000L, oturum.expiresAtMs)
    }

    @Test
    fun `istek dogru uc noktaya ve anahtarla gider`() = runTest {
        val istekler = mutableListOf<HttpRequestData>()
        api(yakala = { istekler += it }).signIn("personel@ornek.com", "sifre")

        assertEquals(2, istekler.size, "Giriş jeton + salon olmak üzere iki istek")

        val jeton = istekler.first()
        assertTrue(jeton.url.toString().contains("/auth/v1/token"))
        assertTrue(jeton.url.toString().contains("grant_type=password"))
        assertEquals("anon-anahtar", jeton.headers["apikey"])

        // Salon araması jetonla yapılmalı: anon anahtarı tek başına hiçbir satır
        // görmüyor, sorgu boş dönerdi ve kullanıcı "salona bağlı değilsiniz"
        // hatası alırdı — sebebi hiç anlaşılmayacak bir hata.
        val salon = istekler.last()
        assertTrue(salon.url.toString().contains("/rest/v1/gym_users"))
        assertEquals("Bearer erisim-jetonu", salon.headers["Authorization"])
    }

    // ─── Başarısız durumlar ─────────────────────────────────────────────────

    /**
     * Yanlış şifre ile onaylanmamış hesap aynı durum kodunu döndürüyor; ayrımı
     * sunucunun mesajı taşıyor. Bu yüzden mesaj olduğu gibi aktarılıyor: biri
     * şifreyi aratır, diğeri panelden hesap onaylatır.
     */
    @Test
    fun `yanlis sifre kimlik hatasi doner`() = runTest {
        val sonuc = api(
            jetonDurumu = HttpStatusCode.BadRequest,
            jetonGovdesi = """{"error":"invalid_grant","error_description":"Invalid login credentials"}""",
        ).signIn("personel@ornek.com", "yanlis")

        val hata = assertIs<AuthResult.InvalidCredentials>(sonuc)
        assertTrue(hata.reason.contains("Invalid login credentials"), "Sunucu mesajı taşınmalı: ${hata.reason}")
    }

    @Test
    fun `onaylanmamis hesabin mesaji korunur`() = runTest {
        val sonuc = api(
            jetonDurumu = HttpStatusCode.BadRequest,
            jetonGovdesi = """{"code":400,"error_code":"email_not_confirmed","msg":"Email not confirmed"}""",
        ).signIn("personel@ornek.com", "sifre")

        val hata = assertIs<AuthResult.InvalidCredentials>(sonuc)
        assertTrue(hata.reason.contains("Email not confirmed"), "Sunucu mesajı taşınmalı: ${hata.reason}")
    }

    /**
     * Kimlik doğrulandı ama `gym_users` satırı yok.
     *
     * Bu, kurulumda gerçekten yaşanan durum. Ayrı bir sonuç olmasaydı kullanıcıya
     * "giriş başarısız" denirdi ve eksik olanın sunucudaki tek bir satır olduğu
     * hiçbir yerden anlaşılmazdı.
     */
    @Test
    fun `salona bagli olmayan kullanici ayri sonuc doner`() = runTest {
        val sonuc = api(salonGovdesi = "[]").signIn("personel@ornek.com", "sifre")

        assertEquals(AuthResult.NoGym("kullanici-1"), sonuc)
    }

    /**
     * Birden fazla salon: sessizce ilki seçilmiyor.
     *
     * Seçmek, kullanıcının o an hangi salonda çalıştığını **tahmin etmek** olurdu
     * ve yanlış tahminin sonucu verinin yanlış salona yazılması. Geri alınması
     * elle temizlik gerektirir.
     */
    @Test
    fun `birden fazla salonda calisan kullanici icin secim gerekir`() = runTest {
        val sonuc = api(salonGovdesi = """[{"gym_id":"salon-1","role":"ADMIN"},{"gym_id":"salon-2","role":"TRAINER"}]""")
            .signIn("personel@ornek.com", "sifre")

        val cok = assertIs<AuthResult.MultipleGyms>(sonuc)
        assertEquals(listOf("salon-1", "salon-2"), cok.gymIds)
    }

    @Test
    fun `sunucu hatasi tekrar denenebilir sayilir`() = runTest {
        val sonuc = api(jetonDurumu = HttpStatusCode.ServiceUnavailable, jetonGovdesi = "")
            .signIn("personel@ornek.com", "sifre")

        val hata = assertIs<AuthResult.Failed>(sonuc)
        assertTrue(hata.retryable, "5xx tekrar denenebilir olmalı")
        assertTrue(hata.reason.contains("503"), "Gerekçe durum kodunu taşımalı: ${hata.reason}")
    }

    @Test
    fun `cok fazla istek tekrar denenebilir sayilir`() = runTest {
        val sonuc = api(jetonDurumu = HttpStatusCode.TooManyRequests, jetonGovdesi = "")
            .signIn("personel@ornek.com", "sifre")

        val hata = assertIs<AuthResult.Failed>(sonuc)
        assertTrue(hata.retryable)
        assertTrue(hata.reason.contains("429"), "Gerekçe durum kodunu taşımalı: ${hata.reason}")
    }

    /**
     * Ağ hatası tekrar denenebilir — ve gerekçe istisna tipini taşıyor.
     *
     * Tip olmadan, ağ dışı bir hatanın (ör. yanlış yapılandırma) buraya düşmesi
     * teşhis edilemez hâle gelirdi; aynı durum uzak uçta bir kez yaşandı.
     */
    @Test
    fun `ag hatasi tekrar denenebilir sayilir`() = runTest {
        val kopuk = MockEngine { throw java.io.IOException("bağlantı koptu") }
        val sonuc = SupabaseAuthApi(config, HttpClient(kopuk)).signIn("a@b.c", "sifre")

        val hata = assertIs<AuthResult.Failed>(sonuc)
        assertTrue(hata.retryable)
        assertTrue(hata.reason.contains("IOException"), "İstisna tipi taşınmalı: ${hata.reason}")
    }

    /** Jeton alındı ama salon araması düştü: giriş başarılı sayılmamalı. */
    @Test
    fun `salon aramasi duserse giris basarisiz sayilir`() = runTest {
        val sonuc = api(salonDurumu = HttpStatusCode.InternalServerError, salonGovdesi = "")
            .signIn("personel@ornek.com", "sifre")

        val hata = assertIs<AuthResult.Failed>(sonuc)
        assertTrue(hata.retryable)
    }

    /** Beklenmeyen gövde çökmemeli, anlaşılır bir hataya dönüşmeli. */
    @Test
    fun `bozuk yanit anlasilir hataya donusur`() = runTest {
        val sonuc = api(jetonGovdesi = "bu JSON değil").signIn("personel@ornek.com", "sifre")

        val hata = assertIs<AuthResult.Failed>(sonuc)
        assertTrue(!hata.retryable, "Bozuk yanıtı tekrar denemek aynı sonucu verir")
    }

    // ─── Hangi istek düştü ──────────────────────────────────────────────────
    //
    // Giriş iki isteğe dayanıyor ve ikisi de aynı biçimde hata üretiyordu.
    // Gerçek bir kurulumda "Beklenmeyen yanıt (404): Invalid path specified in
    // request URL" alındı ve hangi çağrının 404 verdiği mesajdan anlaşılamadı —
    // oysa ikisi tamamen farklı sebeplere işaret ediyor (yanlış sunucu adresi mi,
    // eksik `gym_users` tablosu mu). Bu yüzden uç nokta mesaja giriyor.

    @Test
    fun `jeton ucundaki 404 hangi adresin dustugunu soyler`() = runTest {
        val sonuc = api(
            jetonDurumu = HttpStatusCode.NotFound,
            jetonGovdesi = """{"message":"Invalid path specified in request URL"}""",
        ).signIn("personel@ornek.com", "sifre")

        val hata = assertIs<AuthResult.Failed>(sonuc)
        assertTrue(hata.reason.contains("404"), hata.reason)
        assertTrue(
            hata.reason.contains("https://ornek.supabase.co/auth/v1/token"),
            "Düşen uç nokta mesajda olmalı: ${hata.reason}",
        )
        assertTrue(
            hata.reason.contains("sunucu adresi yanlış olabilir"),
            "404 için adres ipucu verilmeli: ${hata.reason}",
        )
        assertTrue(!hata.retryable, "Yanlış adresi tekrar denemek aynı sonucu verir")
    }

    @Test
    fun `salon ucundaki 404 jeton ucundan ayirt edilebilir`() = runTest {
        val sonuc = api(
            salonDurumu = HttpStatusCode.NotFound,
            salonGovdesi = """{"message":"Invalid path specified in request URL"}""",
        ).signIn("personel@ornek.com", "sifre")

        val hata = assertIs<AuthResult.Failed>(sonuc)
        assertTrue(
            hata.reason.contains("https://ornek.supabase.co/rest/v1/gym_users"),
            "Salon araması düştüğünde o uç nokta yazmalı: ${hata.reason}",
        )
        assertTrue(
            !hata.reason.contains("/auth/v1/token"),
            "İki uç nokta karışmamalı: ${hata.reason}",
        )
    }

    /**
     * Kimlik hatasında adres eklenmiyor.
     *
     * Orada sorun neredeyse her zaman şifre; teknik ayrıntı yalnızca gürültü
     * olur ve kullanıcıyı yanlış yere bakmaya gönderir.
     */
    @Test
    fun `kimlik hatasi teknik ayrinti tasimaz`() = runTest {
        val sonuc = api(
            jetonDurumu = HttpStatusCode.BadRequest,
            jetonGovdesi = """{"error_description":"Invalid login credentials"}""",
        ).signIn("personel@ornek.com", "yanlis")

        val hata = assertIs<AuthResult.InvalidCredentials>(sonuc)
        assertEquals("Invalid login credentials", hata.reason)
        assertTrue(!hata.reason.contains("supabase.co"), "Adres kimlik hatasına girmemeli")
    }

    /** Anahtar ve başlıklar hata mesajına ASLA girmemeli. */
    @Test
    fun `hata mesaji anahtari sizdirmaz`() = runTest {
        val sonuc = api(
            jetonDurumu = HttpStatusCode.NotFound,
            jetonGovdesi = """{"message":"Invalid path specified in request URL"}""",
        ).signIn("personel@ornek.com", "sifre")

        val hata = assertIs<AuthResult.Failed>(sonuc)
        assertTrue(!hata.reason.contains("anon-anahtar"), "Anahtar mesaja girmiş: ${hata.reason}")
    }

    // ─── Jeton yenileme ─────────────────────────────────────────────────────

    @Test
    fun `yenileme dogru grant_type ile gider`() = runTest {
        val istekler = mutableListOf<HttpRequestData>()
        val sonuc = api(yakala = { istekler += it }).refresh("eski-yenileme-jetonu")

        assertIs<AuthResult.Success>(sonuc)
        assertTrue(istekler.first().url.toString().contains("grant_type=refresh_token"))
    }
}
