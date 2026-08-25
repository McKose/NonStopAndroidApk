package com.gymapp.arayuz

import com.gymapp.arayuz.paketler.PaketFormu
import com.gymapp.arayuz.paketler.PaketFormuEkrani
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Paket formunun çizim testi.
 *
 * Ekranın üç hâli farklı dallara giriyor: yükleniyor (yalnızca çember),
 * yeni paket (boş form), düzenleme (dolu form + farklı başlık). Ayrıca
 * "sınırsız" anahtarı seans alanını tamamen gizliyor — o da ayrı bir dal.
 */
class PaketFormuGoruntuTesti {

    private val dolu = PaketFormu(
        sinirsiz = false,
        seansSayisi = "10",
        tur = TrainingType.REFORMER,
        kategori = PackageCategory.INDIVIDUAL,
        fiyat = "4500",
        gun = "60",
    )

    @Test
    fun `yeni paket formu ciziliyor`() {
        val dosya = ekraniCiz("paket-formu-yeni") {
            PaketFormuEkrani(
                baslangic = PaketFormu(),
                yukleniyor = false,
                onKaydet = {}, onGeri = {},
            )
        }
        cizildiginiDogrula(dosya)
    }

    @Test
    fun `duzenleme formu ciziliyor`() {
        val dosya = ekraniCiz("paket-formu-duzenleme") {
            PaketFormuEkrani(
                baslangic = dolu,
                yukleniyor = false,
                onKaydet = {}, onGeri = {},
            )
        }
        cizildiginiDogrula(dosya)
    }

    @Test
    fun `yukleniyor hali ciziliyor`() {
        val dosya = ekraniCiz("paket-formu-yukleniyor") {
            PaketFormuEkrani(
                baslangic = null,
                yukleniyor = true,
                onKaydet = {}, onGeri = {},
            )
        }
        // Yalnızca başlık çubuğu ve bir yükleme çemberi var — eşik düşük.
        cizildiginiDogrula(dosya, enAzRenk = 6)
    }

    /**
     * "Sınırsız" seans alanını gizliyor mu.
     *
     * Anahtar üç şeyi etkiliyor: seans alanının varlığı ve otomatik üretilen
     * paket adı. Ekrana hiç bağlanmamış olsaydı iki görüntü aynı çıkardı;
     * derleme bunu yakalayamaz.
     */
    @Test
    fun `sinirsiz secimi gorunumu degistiriyor`() {
        val sayili = ekraniCiz("paket-formu-duzenleme") {
            PaketFormuEkrani(
                baslangic = dolu, yukleniyor = false,
                onKaydet = {}, onGeri = {},
            )
        }.readBytes()

        val sinirsiz = ekraniCiz("paket-formu-sinirsiz") {
            PaketFormuEkrani(
                baslangic = dolu.copy(sinirsiz = true), yukleniyor = false,
                onKaydet = {}, onGeri = {},
            )
        }.readBytes()

        assertTrue(
            !sayili.contentEquals(sinirsiz),
            "Sınırsız anahtarı görüntüyü değiştirmedi — seans alanı gizlenmiyor olabilir",
        )
    }
}
