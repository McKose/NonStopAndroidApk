package com.gymapp.arayuz

import com.gymapp.arayuz.paketler.PaketListesiEkrani
import com.gymapp.data.local.entity.PackageEntity
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Paket listesi ekranının çizim testi.
 *
 * Ekranın üç ayrı hâli var ve ikisi kolayca gözden kaçar: boş liste ve
 * yetkisiz görünüm. Üçü de ayrı ayrı çiziliyor — bir hâlin çizilmesi
 * diğerlerinin çizildiğini kanıtlamıyor, çünkü her biri farklı dala giriyor.
 */
class PaketListesiGoruntuTesti {

    private fun paket(
        id: String,
        ad: String,
        kurus: Long,
        gun: Int,
        seans: Int?,
    ) = PackageEntity(
        id = id,
        tenantId = "t",
        name = ad,
        validityDays = gun,
        sessionCount = seans,
        basePriceMinor = kurus,
        createdAtMs = 0,
        updatedAtMs = 0,
    )

    private val ornekler = listOf(
        // Dört haneli fiyat bilinçli: `Money.toString()` binlik ayırıcı
        // koymuyordu ve bu satır `₺12000,00` diye okunuyordu. Artık
        // `ParaBicimi` üzerinden geçiyor.
        paket("1", "Aylık Sınırsız", 1_200_000, 30, null),
        paket("2", "10 Seans Reformer", 450_000, 60, 10),
        paket("3", "Deneme Dersi", 25_000, 7, 1),
    )

    @Test
    fun `dolu liste ciziliyor`() {
        val dosya = ekraniCiz("paket-listesi") {
            PaketListesiEkrani(
                paketler = ornekler,
                yazabilir = true,
                onEkle = {}, onDuzenle = {}, onSil = {}, onGeri = {},
            )
        }
        cizildiginiDogrula(dosya)
    }

    @Test
    fun `bos liste ciziliyor`() {
        // Boş hâl ayrı bir dal: liste yerine ortalanmış bir metin çiziliyor.
        // Dolu hâlin geçmesi bunun çalıştığını göstermez.
        val dosya = ekraniCiz("paket-listesi-bos") {
            PaketListesiEkrani(
                paketler = emptyList(),
                yazabilir = true,
                onEkle = {}, onDuzenle = {}, onSil = {}, onGeri = {},
            )
        }
        // Eşik düşük: boş ekranda yalnızca başlık çubuğu, bir cümle ve düğme
        // var. Yüksek tutmak bu geçerli hâli yalancı kırmızı yapardı.
        cizildiginiDogrula(dosya, enAzRenk = 8)
    }

    /**
     * Yetkisiz görünüm dolu görünümden FARKLI çizilmeli.
     *
     * Yetki bayrağı üç yeri etkiliyor: ekleme düğmesi, silme simgesi ve
     * uyarı şeridi. Üçü de aynı bayrağa bağlı olduğu için tek bir görüntü
     * karşılaştırması yeterli — ama karşılaştırma şart: bayrak hiç
     * bağlanmamış olsaydı iki görüntü aynı çıkardı ve derleme bunu
     * yakalayamazdı.
     */
    @Test
    fun `yetkisiz gorunum farkli`() {
        val yazabilir = ekraniCiz("paket-listesi") {
            PaketListesiEkrani(
                paketler = ornekler, yazabilir = true,
                onEkle = {}, onDuzenle = {}, onSil = {}, onGeri = {},
            )
        }.readBytes()

        val saltOkunur = ekraniCiz("paket-listesi-salt-okunur") {
            PaketListesiEkrani(
                paketler = ornekler, yazabilir = false,
                onEkle = {}, onDuzenle = {}, onSil = {}, onGeri = {},
            )
        }.readBytes()

        assertTrue(
            !yazabilir.contentEquals(saltOkunur),
            "Yetki bayrağı görüntüyü değiştirmedi — ekrana hiç bağlanmamış olabilir",
        )
    }
}
