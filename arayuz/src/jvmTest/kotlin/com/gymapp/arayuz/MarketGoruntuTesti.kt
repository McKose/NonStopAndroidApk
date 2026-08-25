package com.gymapp.arayuz

import androidx.compose.runtime.Composable
import com.gymapp.arayuz.market.MarketDurumu
import com.gymapp.arayuz.market.MarketEkrani
import com.gymapp.data.local.entity.ProductEntity
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Market / POS ekranının çizim testi.
 *
 * İki ayrı karar sınanıyor: ürün yönetimi yetkisi (satışı etkilemiyor,
 * yalnızca tanım girişlerini) ve sepetin varlığı (alttaki ödeme şeridi).
 *
 * Ödeme sayfası `ModalBottomSheet` kullandığı için sınanmıyor — kendi
 * penceresini ve animasyonunu getirdiği için ekransız Skia sahnesinde
 * güvenilir çizilmiyor (takvimde de aynı sebeple atlanmıştı). Ürün
 * diyaloğu düz `AlertDialog` olduğu için sınanıyor.
 */
class MarketGoruntuTesti {

    private val urunler = listOf(
        urun("u1", "Protein Bar", 15_000),
        urun("u2", "Su 0.5L", 1_500),
        urun("u3", "İzotonik", 4_000),
    )

    /** `u2` stokta az kaldı: kart uyarı rengiyle çiziliyor. */
    private val stok = mapOf("u1" to 40, "u2" to 2, "u3" to 12)

    private fun ekran(
        sepet: Map<String, Int> = emptyMap(),
        urunYonetebilir: Boolean = true,
        duzenlenenUrun: ProductEntity? = null,
        urunEklemeAcik: Boolean = false,
    ): @Composable () -> Unit = {
        MarketEkrani(
            durum = MarketDurumu(products = urunler, cart = sepet, stockByProduct = stok),
            urunYonetebilir = urunYonetebilir,
            urunEklemeAcik = urunEklemeAcik,
            duzenlenenUrun = duzenlenenUrun,
            odemeAcik = false,
            onGeri = {}, onSiparisGecmisi = {},
            onUrunEklemeAc = {}, onUrunEklemeKapat = {}, onUrunDuzenle = {},
            onSepeteEkle = {}, onSepettenCikar = {}, onUrunSil = {},
            onUrunKaydet = { _, _, _, _, _ -> },
            onOdemeAc = {}, onOdemeKapat = {},
            onUyeSec = {}, onOdemeTuru = {}, onOdemeDurumu = {},
            onTeslimDurumu = {}, onIskonto = {}, onNotlar = {}, onOdemeOnayla = {},
        )
    }

    @Test
    fun `bos sepetle urun izgarasi ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("market", icerik = ekran()))
    }

    @Test
    fun `dolu sepet ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("market-sepetli", icerik = ekran(sepet = mapOf("u1" to 3))))
    }

    @Test
    fun `urun diyalogu ciziliyor`() {
        val dosya = ekraniCiz(
            "market-urun-diyalogu",
            yukseklik = 1600,
            icerik = ekran(duzenlenenUrun = urunler[0]),
        )
        cizildiginiDogrula(dosya)
    }

    /**
     * Sepet şeridi yalnızca sepet doluyken çiziliyor mu.
     *
     * Şerit "ÖDEME AL" düğmesini taşıyor. Koşul ters bağlansaydı ya boş
     * sepetle ödemeye gidilebilir ya da dolu sepette ödeme düğmesi hiç
     * görünmezdi — ikincisi satışı tamamen engeller.
     */
    @Test
    fun `sepet seridi sepete bagli`() {
        val bos = ekraniCiz("market", icerik = ekran()).readBytes()
        val dolu = ekraniCiz("market-sepetli", icerik = ekran(sepet = mapOf("u1" to 3))).readBytes()

        assertTrue(
            !bos.contentEquals(dolu),
            "Sepet şeridi sepete tepki vermedi",
        )
    }

    /**
     * Ürün yönetimi yetkisi görünümü değiştiriyor mu.
     *
     * Yetki yalnızca tanım girişlerini (ekleme, düzenleme, silme) etkiliyor;
     * satış herkese açık. Bağlanmamış olsaydı eğitmen ürün fiyatını
     * değiştirebilir görünürdü.
     */
    @Test
    fun `urun yonetimi yetkisi goruntuyu degistiriyor`() {
        val yonetebilen = ekraniCiz("market", icerik = ekran(urunYonetebilir = true)).readBytes()
        val yonetemeyen = ekraniCiz(
            "market-yetkisiz",
            icerik = ekran(urunYonetebilir = false),
        ).readBytes()

        assertTrue(
            !yonetebilen.contentEquals(yonetemeyen),
            "Ürün yönetimi yetkisi görünümü değiştirmedi",
        )
    }

    private fun urun(id: String, ad: String, kurus: Long) = ProductEntity(
        id = id,
        tenantId = "t",
        name = ad,
        category = "Atıştırmalık",
        priceMinor = kurus,
        createdAtMs = 0,
        updatedAtMs = 0,
    )
}
