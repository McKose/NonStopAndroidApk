package com.gymapp.arayuz

import com.gymapp.arayuz.market.SiparisGecmisiEkrani
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.domain.DeliveryStatus
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Sipariş geçmişi ekranının çizim testi.
 *
 * Üye adı üç ayrı yoldan geliyor — misafir (kimlik yok), bilinen üye, silinmiş
 * üye (kimlik var ama eşlemede yok) — ve üçü de aynı `when` dalında karar
 * veriliyor. Örnek veri üçünü de içeriyor: yalnızca birini vermek, diğer iki
 * dalın hiç çalıştırılmadığı anlamına gelirdi.
 */
class SiparisGecmisiGoruntuTesti {

    private fun siparis(
        id: String,
        uyeId: String?,
        kurus: Long,
        odendi: Boolean,
        yontem: PaymentMethod = PaymentMethod.CASH,
    ) = OrderEntity(
        id = id,
        tenantId = "t",
        memberId = uyeId,
        totalPriceMinor = kurus,
        finalPriceMinor = kurus,
        paymentMethod = yontem,
        paymentStatus = if (odendi) PaymentState.PAID else PaymentState.PENDING,
        deliveryStatus = DeliveryStatus.POST_DELIVERY,
        dateMs = 1787139000000L, // 19 Ağustos 2026, 14:30 (İstanbul)
        createdAtMs = 0,
        updatedAtMs = 0,
    )

    private val ornekler = listOf(
        siparis("aaaaaaaa-1", "uye-1", 45_000, odendi = true),
        siparis("bbbbbbbb-2", null, 12_500, odendi = false, yontem = PaymentMethod.CARD),
        // Kimliği var ama eşlemede yok: silinmiş üye dalı.
        siparis("cccccccc-3", "uye-silinmis", 1_250_000, odendi = true,
            yontem = PaymentMethod.MULTISPORT),
    )

    private val adlar = mapOf("uye-1" to "Ayşe Yılmaz")

    @Test
    fun `dolu liste ciziliyor`() {
        val dosya = ekraniCiz("siparis-gecmisi") {
            SiparisGecmisiEkrani(siparisler = ornekler, uyeAdlari = adlar, onGeri = {})
        }
        cizildiginiDogrula(dosya)
    }

    @Test
    fun `bos liste ciziliyor`() {
        val dosya = ekraniCiz("siparis-gecmisi-bos") {
            SiparisGecmisiEkrani(siparisler = emptyList(), uyeAdlari = emptyMap(), onGeri = {})
        }
        // Boş ekranda başlık çubuğu ve tek bir gri cümle var.
        cizildiginiDogrula(dosya, enAzRenk = 6)
    }

    /**
     * Üye adı eşlemesi çizime gerçekten bağlı mı.
     *
     * Aynı siparişler, farklı ad eşlemesiyle farklı görüntü vermeli. Vermiyorsa
     * `uyeAdlari` hiç okunmuyor demektir ve ekran herkesi "Silinmiş üye" diye
     * gösteriyor olabilir — derleme bunu yakalayamaz.
     */
    @Test
    fun `uye adi goruntuyu degistiriyor`() {
        val adli = ekraniCiz("siparis-gecmisi") {
            SiparisGecmisiEkrani(siparisler = ornekler, uyeAdlari = adlar, onGeri = {})
        }.readBytes()

        val adsiz = ekraniCiz("siparis-gecmisi-adsiz") {
            SiparisGecmisiEkrani(siparisler = ornekler, uyeAdlari = emptyMap(), onGeri = {})
        }.readBytes()

        assertTrue(
            !adli.contentEquals(adsiz),
            "Üye adı eşlemesi görüntüyü değiştirmedi — ekrana hiç bağlanmamış olabilir",
        )
    }
}
