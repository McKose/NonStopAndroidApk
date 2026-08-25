package com.gymapp.presentation.market

import com.gymapp.arayuz.market.MarketDurumu
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sepet tutarları: ekranda gösterilen ile kaydedilen aynı olmalı.
 *
 * İki ayrı hata buradaydı ve ikisi de sessizdi:
 *
 * 1. **İskonto kırpılmıyordu.** Ekran `toplam - iskonto` diyordu, depo ise
 *    iskontoyu sepetle sınırlıyordu. 50 TL'lik sepete 80 TL iskonto girilince
 *    ekran "₺-30,00" gösteriyor, kaydedilen nihai tutar 0 oluyordu. Üstelik
 *    `processOrder` tahsilatı yalnızca tutar pozitifken yazdığı için "ÖDENDİ"
 *    işaretli o sipariş hiç tahsilat yazmıyordu: ürünler stoktan çıkıyor, gelir
 *    sıfır kalıyordu.
 *
 * 2. **Ekran `Double`, kayıt kuruş kullanıyordu.** İki ayrı aritmetik, iki ayrı
 *    yuvarlama.
 *
 * Buradaki ifadeler `ProductRepository.processOrder` ile birebir aynı olmalı;
 * sapmanın kaynağı tam olarak ikisinin ayrı yazılmasıydı.
 */
class MarketCartTotalsTest {

    private fun urun(id: String, kurus: Long) = ProductEntity(
        id = id,
        tenantId = "t",
        name = id,
        category = "içecek",
        priceMinor = kurus,
        imageUrl = null,
        isActive = true,
        createdAtMs = 0,
        updatedAtMs = 0,
        deletedAtMs = null,
    )

    private fun durum(
        urunler: List<ProductEntity>,
        sepet: Map<String, Int>,
        iskonto: Double = 0.0,
    ) = MarketDurumu(products = urunler, cart = sepet, discount = iskonto)

    @Test
    fun `sepet toplami adetle carpiliyor`() {
        val d = durum(
            urunler = listOf(urun("a", 1500), urun("b", 2000)),
            sepet = mapOf("a" to 3, "b" to 1),
        )
        assertEquals(Money(6500), d.cartTotal)
        assertEquals(Money(6500), d.cartFinal, "İskonto yokken nihai tutar toplamla aynı")
    }

    /**
     * Sepeti aşan iskonto kırpılıyor ve nihai tutar **negatife düşmüyor**.
     *
     * Asıl iddia: kırpılmasaydı sipariş sıfır tutarla kaydedilir ve tahsilat hiç
     * yazılmazdı.
     */
    @Test
    fun `sepeti asan iskonto kirpiliyor`() {
        val d = durum(
            urunler = listOf(urun("a", 5000)),
            sepet = mapOf("a" to 1),
            iskonto = 80.0,
        )
        assertEquals(Money(5000), d.cartDiscount, "İskonto sepeti aşamaz")
        assertEquals(Money.ZERO, d.cartFinal)
        assertTrue(d.cartFinal.minor >= 0, "Nihai tutar negatif olamaz")
    }

    /** Negatif iskonto tutarı yükseltmiyor. */
    @Test
    fun `negatif iskonto yok sayiliyor`() {
        val d = durum(
            urunler = listOf(urun("a", 5000)),
            sepet = mapOf("a" to 1),
            iskonto = -20.0,
        )
        assertEquals(Money.ZERO, d.cartDiscount)
        assertEquals(Money(5000), d.cartFinal, "Negatif iskonto tutarı artırmamalı")
    }

    @Test
    fun `normal iskonto dusuluyor`() {
        val d = durum(
            urunler = listOf(urun("a", 5000)),
            sepet = mapOf("a" to 1),
            iskonto = 20.0,
        )
        assertEquals(Money(2000), d.cartDiscount)
        assertEquals(Money(3000), d.cartFinal)
    }

    /**
     * Sepette olup listede olmayan ürün toplamı bozmuyor.
     *
     * Ürün senkronizasyonla silinmiş olabilir; `find` `null` döner. Eskiden bu
     * sessizce 0 sayılıyordu ve öyle kalıyor — ama artık bunun sınandığı bir yer
     * var.
     */
    @Test
    fun `listede olmayan urun toplami bozmuyor`() {
        val d = durum(
            urunler = listOf(urun("a", 5000)),
            sepet = mapOf("a" to 1, "kayip" to 3),
        )
        assertEquals(Money(5000), d.cartTotal)
    }

    @Test
    fun `bos sepet sifir`() {
        val d = durum(urunler = listOf(urun("a", 5000)), sepet = emptyMap())
        assertEquals(Money.ZERO, d.cartTotal)
        assertEquals(Money.ZERO, d.cartFinal)
    }

    /**
     * Kuruş aritmetiği: `Double` ile toplandığında sapan bir kurgu.
     *
     * 0,10 TL'lik üç kalem `Double` ile 0.30000000000000004 eder; kuruşta 30.
     * Tek başına zararsız görünür ama gösterilen ile çekilen tutarın ayrışması
     * tam olarak böyle başlar.
     */
    @Test
    fun `kurus aritmetigi sapmiyor`() {
        val d = durum(urunler = listOf(urun("a", 10)), sepet = mapOf("a" to 3))
        assertEquals(Money(30), d.cartTotal)
        assertEquals(0.30, d.cartTotal.asDouble, 0.0)
    }
}
