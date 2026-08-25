package com.gymapp.arayuz

import androidx.compose.runtime.Composable
import com.gymapp.arayuz.uyeler.UyeKayitEkrani
import com.gymapp.arayuz.uyeler.UyeKayitFormu
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.SessionCarryOver
import com.gymapp.domain.TrainingType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Üye kayıt / paket yenileme ekranının çizim testi.
 *
 * Ekranın koşullu dört bölümü var ve hepsi sessizce kaybolabilir:
 * sağlık bilgileri (yalnızca yeni kayıtta), kalan seans devri (yalnızca
 * yenilemede ve devredecek hak varken), taksit seçimi (yalnızca kartta) ve
 * fiyat kartı (yalnızca paket seçiliyken).
 */
class UyeKayitGoruntuTesti {

    private val paket = PackageEntity(
        id = "pkt-1",
        tenantId = "t",
        name = "10 Seans Reformer",
        type = TrainingType.REFORMER,
        validityDays = 60,
        sessionCount = 10,
        basePriceMinor = 450_000,
        createdAtMs = 0,
        updatedAtMs = 0,
    )

    private val taksitler = listOf(1, 3, 6, 9, 12)

    private fun ekran(
        form: UyeKayitFormu = UyeKayitFormu(),
        yenileme: Boolean = false,
    ): @Composable () -> Unit = {
        UyeKayitEkrani(
            form = form,
            paketler = listOf(paket),
            taksitSecenekleri = taksitler,
            yenileme = yenileme,
            onGeri = {}, onAdSoyad = {}, onTelefon = {}, onEposta = {},
            onSaglikRiskleri = {}, onSaglikNotlari = {}, onPaketSecildi = {},
            onDevir = {}, onIskonto = {}, onOdemeTuru = {}, onOdemeDurumu = {},
            onTaksit = {}, onNotlar = {}, onKaydet = {},
        )
    }

    @Test
    fun `bos kayit formu ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("uye-kayit", icerik = ekran()))
    }

    @Test
    fun `paket secili form ciziliyor`() {
        val form = UyeKayitFormu(
            fullName = "Ayşe Yılmaz",
            phone = "5001112233",
            selectedPackage = paket,
        )
        cizildiginiDogrula(ekraniCiz("uye-kayit-paketli", icerik = ekran(form)))
    }

    /**
     * Fiyat kartı yalnızca paket seçiliyken çiziliyor mu.
     *
     * Koşul ters bağlansaydı kart paket seçilmeden görünür ve "₺0" gösterirdi;
     * ya da hiç görünmez ve kullanıcı ödeyeceği tutarı kaydetmeden önce
     * göremezdi.
     */
    @Test
    fun `fiyat karti pakete bagli`() {
        val paketsiz = ekraniCiz("uye-kayit", icerik = ekran()).readBytes()
        val paketli = ekraniCiz(
            "uye-kayit-paketli",
            icerik = ekran(UyeKayitFormu(selectedPackage = paket)),
        ).readBytes()

        assertTrue(
            !paketsiz.contentEquals(paketli),
            "Fiyat kartı paket seçimine tepki vermedi",
        )
    }

    /**
     * Kartla ödemede taksit seçimi açılıyor mu.
     *
     * Nakitte taksit sorulmamalı; sorulsaydı kullanıcı olmayan bir seçeneği
     * seçebilir ve vade farkı hesabı sebepsiz devreye girerdi.
     */
    @Test
    fun `taksit secimi odeme turune bagli`() {
        val nakit = ekraniCiz(
            "uye-kayit-nakit",
            icerik = ekran(UyeKayitFormu(selectedPackage = paket, paymentType = PaymentMethod.CASH)),
        ).readBytes()
        val kart = ekraniCiz(
            "uye-kayit-kart",
            icerik = ekran(UyeKayitFormu(selectedPackage = paket, paymentType = PaymentMethod.CARD)),
        ).readBytes()

        assertTrue(
            !nakit.contentEquals(kart),
            "Taksit seçimi ödeme türüne tepki vermedi",
        )
    }

    /**
     * Yenilemede kalan seans devri soruluyor mu.
     *
     * Yalnızca devredecek SAYILABİLİR hak varken sorulmalı. Koşul kaybolsaydı
     * ya her yenilemede anlamsız bir karar dayatılır ya da üyenin ödediği
     * seanslar sessizce yanardı.
     */
    @Test
    fun `devir secimi yenilemede ve kalan seans varken cikiyor`() {
        val devirsiz = ekraniCiz(
            "uye-kayit-yenileme-devirsiz",
            icerik = ekran(
                UyeKayitFormu(
                    selectedPackage = paket,
                    isRenewal = true,
                    currentRemainingSessions = 0,
                ),
                yenileme = true,
            ),
        ).readBytes()

        val devirli = ekraniCiz(
            "uye-kayit-yenileme-devirli",
            icerik = ekran(
                UyeKayitFormu(
                    selectedPackage = paket,
                    isRenewal = true,
                    currentRemainingSessions = 4,
                    carryOver = SessionCarryOver.CARRY,
                ),
                yenileme = true,
            ),
        ).readBytes()

        assertTrue(
            !devirsiz.contentEquals(devirli),
            "Kalan seans devri bölümü çizilmedi",
        )
    }

    /**
     * Paket fiyatını aşan iskonto uyarısı görünüyor mu.
     *
     * Kırpma zaten yapılıyordu ama görünmüyordu: kart "1.000 − 5.000 = 0"
     * gibi kendi içinde tutarsız bir aritmetik gösteriyordu.
     */
    @Test
    fun `asiri iskonto uyarisi ciziliyor`() {
        val normal = ekraniCiz(
            "uye-kayit-iskonto-normal",
            icerik = ekran(UyeKayitFormu(selectedPackage = paket, discount = "500")),
        ).readBytes()

        val asiri = ekraniCiz(
            "uye-kayit-iskonto-asiri",
            icerik = ekran(UyeKayitFormu(selectedPackage = paket, discount = "99999")),
        ).readBytes()

        assertTrue(
            !normal.contentEquals(asiri),
            "Aşırı iskonto uyarısı çizilmedi — kırpma görünmez kalıyor",
        )
    }
}
