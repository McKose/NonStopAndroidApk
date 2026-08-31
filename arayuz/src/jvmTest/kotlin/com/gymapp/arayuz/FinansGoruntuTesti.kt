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
        secimModu: Boolean = false,
        secilenKayitlar: Set<String> = emptySet(),
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
            secimModu = secimModu,
            secilenKayitlar = secilenKayitlar,
            onGeri = {}, onDonemDegisti = { _, _ -> },
            onTurSuzgeci = {}, onYontemSuzgeci = {},
            onEklemeAc = {}, onEklemeKapat = {},
            onKayitEkle = { _, _, _, _, _ -> },
            onSecimModu = {}, onSecimDegis = {}, onIptalEt = { _, _ -> },
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

    // ─── Düzeltme kipi ──────────────────────────────────────────────────────

    @Test
    fun `duzeltme kipi ciziliyor`() {
        cizildiginiDogrula(
            ekraniCiz(
                "finans-duzeltme",
                icerik = ekran(secimModu = true, secilenKayitlar = setOf("k1")),
            )
        )
    }

    /**
     * Düzeltme kipi görünümü gerçekten değiştiriyor mu.
     *
     * `secimModu` çizime bağlanmamış olsaydı kutular hiç belirmez, alt şerit
     * çıkmaz ve kullanıcı hatalı kaydı iptal edecek hiçbir yol bulamazdı —
     * özellik kodda var, ekranda yok olurdu.
     */
    @Test
    fun `duzeltme kipi goruntuyu degistiriyor`() {
        val normal = ekraniCiz("finans", icerik = ekran()).readBytes()
        val duzeltme = ekraniCiz("finans-duzeltme", icerik = ekran(secimModu = true)).readBytes()

        assertTrue(
            !normal.contentEquals(duzeltme),
            "Düzeltme kipi görüntüyü değiştirmedi — seçim kutuları çizilmiyor olabilir",
        )
    }

    /**
     * Seçim işaretlenmiş görünüyor mu.
     *
     * Kutu çizilip `secili` bağlanmasaydı kullanıcı hiçbir işaret göremeden
     * "3 kayıt seçildi" yazısını okurdu ve hangilerini iptal ettiğini ancak
     * işlemden sonra öğrenirdi.
     */
    @Test
    fun `secili kayit isaretli goruniyor`() {
        val secimsiz = ekraniCiz("finans-duzeltme-bos", icerik = ekran(secimModu = true))
            .readBytes()
        val secili = ekraniCiz(
            "finans-duzeltme",
            icerik = ekran(secimModu = true, secilenKayitlar = setOf("k1")),
        ).readBytes()

        assertTrue(!secimsiz.contentEquals(secili), "Seçim çizime yansımadı")
    }

    /**
     * İptal edilmiş kayıt işaretleniyor mu.
     *
     * `isVoided` hesaplanıyordu ama hiç çizilmiyordu: iptal edilen kayıt ile
     * onu iptal eden ters kayıt listede yan yana, aynı tutarla ve hiçbir
     * ayrım olmadan duruyordu. Kullanıcı tahsilatın iki kez yazıldığını
     * sanabiliyordu.
     */
    @Test
    fun `iptal edilmis kayit isaretleniyor`() {
        val normal = ekraniCiz(
            "finans-tek",
            icerik = ekran(kayitlar = listOf(kayit("k1", true, 450_000, "MEMBERSHIP", "Ayşe"))),
        ).readBytes()
        val iptalli = ekraniCiz(
            "finans-tek-iptal",
            icerik = ekran(
                kayitlar = listOf(
                    kayit("k1", true, 450_000, "MEMBERSHIP", "Ayşe", iptalEdilmis = true),
                ),
            ),
        ).readBytes()

        assertTrue(
            !normal.contentEquals(iptalli),
            "İptal edilmiş kayıt normal kayıtla aynı çiziliyor",
        )
    }

    private fun kayit(
        id: String,
        gelirMi: Boolean,
        kurus: Long,
        kategori: String,
        aciklama: String,
        bekleyen: Boolean = false,
        iptalEdilmis: Boolean = false,
    ) = FinansKaydi(
        id = id,
        isIncome = gelirMi,
        amount = Money(kurus),
        category = kategori,
        description = aciklama,
        paymentMethod = if (gelirMi) PaymentMethod.CARD else PaymentMethod.CASH,
        occurredAtMs = anMs,
        isPending = bekleyen,
        isVoided = iptalEdilmis,
        note = null,
    )
}
