package com.gymapp.arayuz

import androidx.compose.runtime.Composable
import com.gymapp.arayuz.finans.FinansEkrani
import com.gymapp.arayuz.finans.FinansKaydi
import com.gymapp.arayuz.finans.FinansSuzgeci
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Finans ekranının çizim testi.
 *
 * En pahalı hata yetkide: `gorebilir` yanlış bağlanırsa eğitmen salonun
 * bütün cirosunu ve maaş kalemlerini görür. Ekran bu durumda defteri hiç
 * çizmiyor, yerine sebebi yazan bir ekran geliyor — boş liste göstermek
 * daha kötü olurdu, çünkü eğitmen salonun hiç geliri olmadığını sanardı.
 */
class FinansGoruntuTesti {

    /** 19 Ağustos 2026 — UTC 11:00, İstanbul 14:00 (ikisi de aynı gün). */
    private val anMs = 1787137200000L

    private val kayitlar = listOf(
        kayit("k1", gelirMi = true, kurus = 450_000, kategori = "MEMBERSHIP", aciklama = "Ayşe Yılmaz - 10 Seans"),
        kayit("k2", gelirMi = false, kurus = 1_200_000, kategori = "RENT", aciklama = "Ağustos kirası"),
        // Tahakkuk: tahsil edilmemiş, "BEKLEYEN" rozetiyle çiziliyor.
        kayit("k3", gelirMi = true, kurus = 250_000, kategori = "MARKET", aciklama = "Protein bar", bekleyen = true),
    )

    private fun ekran(
        gorebilir: Boolean = true,
        kayitlar: List<FinansKaydi> = this.kayitlar,
        eklemeAcik: Boolean = false,
    ): @Composable () -> Unit = {
        FinansEkrani(
            gorebilir = gorebilir,
            ay = 7,
            yil = 2026,
            aylikCiro = Money(4_500_000),
            ucAylikCiro = Money(12_000_000),
            altiAylikCiro = Money(23_000_000),
            yillikCiro = Money(41_000_000),
            gelir = Money(4_500_000),
            gider = Money(1_200_000),
            netKar = Money(3_300_000),
            turSuzgeci = FinansSuzgeci.TUMU,
            yontemSuzgeci = "ALL",
            kayitlar = kayitlar,
            eklemeAcik = eklemeAcik,
            onGeri = {}, onDonemDegisti = { _, _ -> },
            onTurSuzgeci = {}, onYontemSuzgeci = {},
            onEklemeAc = {}, onEklemeKapat = {},
            onKayitEkle = { _, _, _, _, _ -> },
        )
    }

    @Test
    fun `defter ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("finans", icerik = ekran()))
    }

    @Test
    fun `bos donem ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("finans-bos", icerik = ekran(kayitlar = emptyList())))
    }

    @Test
    fun `yetkisiz ekran ciziliyor`() {
        // Yalnızca başlık çubuğu ve tek bir açıklama şeridi var.
        cizildiginiDogrula(ekraniCiz("finans-yetki-yok", icerik = ekran(gorebilir = false)), enAzRenk = 6)
    }

    /**
     * Yetki defteri gerçekten gizliyor mu.
     *
     * `gorebilir` bağlanmamış olsaydı iki görüntü aynı çıkardı ve eğitmen
     * salonun cirosunu, kira ve maaş kalemlerini görürdü. Sunucu tarafı
     * okumayı engellemiyor — bu ekran salonun kendi verisini gösteriyor;
     * kısıt tamamen burada.
     */
    @Test
    fun `yetki defteri gizliyor`() {
        val yetkili = ekraniCiz("finans", icerik = ekran(gorebilir = true)).readBytes()
        val yetkisiz = ekraniCiz("finans-yetki-yok", icerik = ekran(gorebilir = false)).readBytes()

        assertTrue(
            !yetkili.contentEquals(yetkisiz),
            "Yetki görünümü değiştirmedi — defter yetkisize de çiziliyor olabilir",
        )
    }

    /**
     * Kayıt listesi çizime bağlı mı.
     *
     * Dolu dönem ile boş dönem aynı çıkıyorsa liste ekrana hiç bağlanmamış
     * demektir ve kullanıcı her dönemi boş görür.
     */
    @Test
    fun `kayitlar goruntuyu degistiriyor`() {
        val dolu = ekraniCiz("finans", icerik = ekran()).readBytes()
        val bos = ekraniCiz("finans-bos", icerik = ekran(kayitlar = emptyList())).readBytes()

        assertTrue(
            !dolu.contentEquals(bos),
            "Kayıt listesi görüntüyü değiştirmedi",
        )
    }

    private fun kayit(
        id: String,
        gelirMi: Boolean,
        kurus: Long,
        kategori: String,
        aciklama: String,
        bekleyen: Boolean = false,
    ) = FinansKaydi(
        id = id,
        isIncome = gelirMi,
        amount = Money(kurus),
        category = kategori,
        description = aciklama,
        paymentMethod = if (gelirMi) PaymentMethod.CARD else PaymentMethod.CASH,
        occurredAtMs = anMs,
        isPending = bekleyen,
        isVoided = false,
        note = null,
    )
}
