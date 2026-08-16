package com.gymapp.data.sync

import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.domain.PaymentState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tahsilat durumu artık enum; saklanan ve gönderilen metin ise değişmedi.
 *
 * ### Neden bu test var
 * `paymentStatus` serbest metindi. `"PAID"` dışındaki her değer — büyük/küçük
 * harf sapması, sunucudan gelen bozuk bir satır, ileride eklenecek bir
 * `"PARTIAL"` — tahsilatı **sessizce** atlıyordu; ekranlar da iki farklı
 * karşılaştırma kullanıyordu (`== "PENDING"` ve `!= "PAID"`), yani ikisinden de
 * olmayan bir değer listede uyarı verirken panelde hatırlatmayı kaybediyordu.
 *
 * Enum'a çevirmek Kotlin tarafını kapatıyor. Bu testin işi **çevirinin sessizce
 * bir şey kırmadığını** göstermek: veritabanında ve sunucuda duran metin
 * eskisiyle birebir aynı kalmalı. Aynı kalmasaydı göç gerekirdi, panel
 * (`web/`) eşleşmeyi kaybederdi ve sunucudaki `payment_status text` kolonu iki
 * farklı sözlükle yazılır olurdu.
 */
class PaymentStateWireTest {

    private val db: GymDatabase = createTestDatabase()

    @AfterTest
    fun kapat() {
        db.close()
    }

    // ─── Sözleşme ───────────────────────────────────────────────────────────

    /**
     * Sabit adları sunucu ve panelle aynı.
     *
     * Bu iddia sıradan görünüyor ama tam olarak "göç gerekmedi" cümlesini
     * tutan şey bu. Sabitler yeniden adlandırılırsa (`PAID` → `SETTLED` gibi)
     * derleme sorunsuz geçer, testlerin geri kalanı da geçer — çünkü hepsi
     * enum'un kendisiyle karşılaştırır. Kıyaslamanın metinle yapıldığı tek yer
     * cihazın dışıdır: `supabase/migrations/0002_data_tables.sql` varsayılanı
     * `'PENDING'` ve panel `web/` aynı metni bekler. Kırılan yer orası olurdu
     * ve burada durmazsa hiçbir yerde durmaz.
     */
    @Test
    fun `sabit adlari sunucudaki metinle ayni`() {
        assertEquals("PAID", PaymentState.PAID.name)
        assertEquals("PENDING", PaymentState.PENDING.name)
        assertEquals(2, PaymentState.entries.size, "Yeni bir durum sunucu tarafını da ilgilendirir")
    }

    /** Gönderilen yük hâlâ düz metin taşıyor. */
    @Test
    fun `gonderilen yuk metin tasiyor`() {
        val yuk = RowPayloads.of(SampleRows.order.copy(paymentStatus = PaymentState.PENDING))

        assertEquals(JsonPrimitive("PENDING"), yuk["payment_status"])
    }

    // ─── Veritabanı ─────────────────────────────────────────────────────────

    /**
     * Gerçek SQLite'a yazılıp geri okunuyor.
     *
     * Dönüştürücü (`Converters.paymentStateToName`) veritabanına kayıtlı
     * olmasaydı Room derleme anında şikâyet ederdi; ama derleme bu ortamda
     * yerelde koşmuyor. Bu test aynı şeyi çalışma anında söylüyor.
     */
    @Test
    fun `veritabani gidis donuste durum korunuyor`() = runTest {
        val dao = db.memberDao()
        // Telefonlar farklı: (tenantId, phone) benzersiz indeksli.
        dao.insertMember(
            SampleRows.member.copy(
                id = "odendi", phone = "+905001110001", paymentStatus = PaymentState.PAID,
            )
        )
        dao.insertMember(
            SampleRows.member.copy(
                id = "odenmedi", phone = "+905001110002", paymentStatus = PaymentState.PENDING,
            )
        )

        assertEquals(PaymentState.PAID, dao.getMemberById("odendi")?.paymentStatus)
        assertEquals(PaymentState.PENDING, dao.getMemberById("odenmedi")?.paymentStatus)
    }

    // ─── Çekme tarafı ───────────────────────────────────────────────────────

    /** "Ödenmedi" satırı gidiş-dönüşte korunuyor; örnek satır yalnızca ödenmişi kapsıyordu. */
    @Test
    fun `odenmemis satir gidis donuste korunur`() {
        val uye = SampleRows.member.copy(paymentStatus = PaymentState.PENDING)
        val siparis = SampleRows.order.copy(paymentStatus = PaymentState.PENDING)

        assertEquals(uye, RowParsers.member(RowPayloads.of(uye)))
        assertEquals(siparis, RowParsers.order(RowPayloads.of(siparis)))
    }

    /**
     * Tanınmayan değer PENDING'e düşüyor — PAID'e değil.
     *
     * Yön burada bilinçli: olmayan bir tahsilatı varmış saymak, olanı yokmuş
     * saymaktan çok daha pahalı. İlki parayı sessizce kaybettirir, ikincisi en
     * fazla fazladan bir hatırlatma üretir.
     */
    @Test
    fun `taninmayan deger odenmedi sayiliyor`() {
        val bozuk = RowPayloads.of(SampleRows.order) // PAID
            .bozul("payment_status", JsonPrimitive("PARTIAL"))

        assertEquals(PaymentState.PENDING, RowParsers.order(bozuk)?.paymentStatus)
    }

    /**
     * Alan hiç yoksa satır yine de okunuyor.
     *
     * Davranış değişti ve sebebi doğrudan 0.1'de düzeltilen hata: sipariş
     * ayrıştırıcısı eskiden `payment_status` yoksa `null` dönüyordu, yani satırı
     * "okunamaz" sayıyordu. Okunamayan satır artık tablonun tamamını durduruyor
     * (`PullStop.BLOCKED`), dolayısıyla tek bir bozuk alan **bütün siparişlerin**
     * inmesini engellerdi. Zorunlu alanlar (`id`, `final_price_minor`, `date_ms`)
     * hâlâ satırı reddediyor — onlar olmadan satırın anlamı yok; tahsilat
     * durumunun ise güvenli bir varsayılanı var.
     */
    @Test
    fun `alan eksikken satir yine okunuyor`() {
        val eksik = JsonObject(RowPayloads.of(SampleRows.order) - "payment_status")

        val geri = assertNotNull(RowParsers.order(eksik), "Eksik tahsilat durumu satırı düşürmemeli")
        assertEquals(PaymentState.PENDING, geri.paymentStatus)
        assertEquals(SampleRows.order.finalPriceMinor, geri.finalPriceMinor, "Kalan alanlar korunmalı")
    }

    /**
     * Karşı kontrol: gerçekten zorunlu bir alan hâlâ satırı düşürüyor.
     *
     * Yukarıdaki test tek başına, ayrıştırıcının artık **hiçbir şeyi**
     * reddetmediği bir dünyada da geçerdi.
     */
    @Test
    fun `gercekten zorunlu alan eksikken satir dusuyor`() {
        val eksik = JsonObject(RowPayloads.of(SampleRows.order) - "final_price_minor")

        assertNull(RowParsers.order(eksik))
    }

    private fun JsonObject.bozul(anahtar: String, deger: JsonPrimitive): JsonObject =
        JsonObject(this + (anahtar to deger))
}
