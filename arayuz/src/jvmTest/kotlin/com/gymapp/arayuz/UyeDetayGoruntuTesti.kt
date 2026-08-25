package com.gymapp.arayuz

import androidx.compose.runtime.Composable
import com.gymapp.arayuz.uyeler.UyeDetayEkrani
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.local.entity.MeasurementEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import com.gymapp.domain.TrainingType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Üye detayının çizim testi.
 *
 * Dört sekmenin dördü de ayrı ayrı çiziliyor; her biri eskiden kendi
 * ViewModel'ini çekiyordu ve o hâliyle hiç sınanamıyordu.
 *
 * `simdiMs` sabit: üyelik durumu bundan türetiliyor ve ekran saati kendi
 * okusaydı test her koşuda farklı sonuç verirdi.
 */
class UyeDetayGoruntuTesti {

    /** 25 Ağustos 2026, 12:00 (İstanbul). */
    private val simdi = 1787649600000L
    private val gun = 86_400_000L

    private val uye = MemberEntity(
        id = "uye-1",
        tenantId = "t",
        fullName = "Ayşe Yılmaz",
        phone = "05001112233",
        email = "ayse@example.com",
        endDateMs = simdi + 30 * gun,
        remainingSessions = 6,
        paymentStatus = PaymentState.PENDING,
        healthRisks = "Tansiyon",
        healthNotes = "Ağır yük yok",
        createdAtMs = 0,
        updatedAtMs = 0,
    )

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

    private val olcumler = listOf(
        MeasurementEntity(
            id = "olc-1", tenantId = "t", memberId = "uye-1",
            dateMs = simdi - 7 * gun,
            height = 168.0, weight = 62.5, shoulder = 40.0, chest = 88.0,
            waist = 70.0, hips = 95.0, leg = 55.0, arm = 27.0,
            notes = "İlk ölçüm",
            createdAtMs = 0, updatedAtMs = 0,
        ),
    )

    private val hareketler = listOf(
        defter("d1", LedgerType.CHARGE, 450_000, "10 Seans Reformer"),
        defter("d2", LedgerType.PAYMENT, 200_000, "Kısmi tahsilat"),
    )

    private fun ekran(
        sekme: Int = 0,
        uye: MemberEntity? = this.uye,
        kalanBorc: Money? = Money(250_000),
        silmeOnayiAcik: Boolean = false,
    ): @Composable () -> Unit = {
        UyeDetayEkrani(
            uye = uye,
            secilenSekme = sekme,
            silmeOnayiAcik = silmeOnayiAcik,
            siliniyor = false,
            simdiMs = simdi,
            kalanBorc = kalanBorc,
            olcumler = olcumler,
            aktifPaket = paket,
            hareketler = hareketler,
            onGeri = {}, onSekmeSec = {}, onSilIste = {}, onSilOnayla = {}, onSilVazgec = {},
            onTahsilat = {}, onSaglikKaydet = {},
            onOlcumEkle = { _, _, _, _, _, _, _, _, _ -> },
            onOlcumSil = {},
        )
    }

    @Test
    fun `genel sekmesi ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("uye-detay-genel", icerik = ekran(sekme = 0)))
    }

    @Test
    fun `saglik sekmesi ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("uye-detay-saglik", icerik = ekran(sekme = 1)))
    }

    @Test
    fun `olcum sekmesi ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("uye-detay-olcum", icerik = ekran(sekme = 2)))
    }

    @Test
    fun `paket sekmesi ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("uye-detay-paket", icerik = ekran(sekme = 3)))
    }

    @Test
    fun `uye okunmadan yukleme gosteriliyor`() {
        // Yalnızca başlık çubuğu ve bir yükleme çemberi var.
        cizildiginiDogrula(ekraniCiz("uye-detay-yukleniyor", icerik = ekran(uye = null)), enAzRenk = 6)
    }

    @Test
    fun `silme onayi ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("uye-detay-silme", icerik = ekran(silmeOnayiAcik = true)))
    }

    /**
     * Sekme seçimi gerçekten içeriği değiştiriyor mu.
     *
     * `secilenSekme` çizime bağlanmamış olsaydı dört sekme de aynı içeriği
     * gösterirdi — kullanıcı ölçümlere ya da paketlere hiç ulaşamazdı.
     */
    @Test
    fun `sekme secimi icerigi degistiriyor`() {
        val genel = ekraniCiz("uye-detay-genel", icerik = ekran(sekme = 0)).readBytes()
        val olcum = ekraniCiz("uye-detay-olcum", icerik = ekran(sekme = 2)).readBytes()

        assertTrue(
            !genel.contentEquals(olcum),
            "Sekme seçimi içeriği değiştirmedi",
        )
    }

    /**
     * Kalan borç okunmadan tahsilat düğmesi açılmıyor mu.
     *
     * Düğme `kalanBorc` gelene kadar pasif olmalı: tutarı bilmeden tahsilat
     * diyaloğu açmak, bu ekranda daha önce düzeltilen hatanın aynısı olurdu
     * (tek dokunuşla tarih sınırsız borcun tamamının tahsil edilmesi).
     */
    @Test
    fun `borc okunmadan dugme pasif`() {
        val borclu = ekraniCiz("uye-detay-genel", icerik = ekran(kalanBorc = Money(250_000))).readBytes()
        val bilinmeyen = ekraniCiz(
            "uye-detay-genel-borcsuz",
            icerik = ekran(kalanBorc = null),
        ).readBytes()

        assertTrue(
            !borclu.contentEquals(bilinmeyen),
            "Kalan borç görünümü değiştirmedi — düğme durumu borca bağlı değil",
        )
    }

    private fun defter(id: String, tur: LedgerType, kurus: Long, aciklama: String) =
        LedgerEntryEntity(
            id = id,
            tenantId = "t",
            memberId = "uye-1",
            type = tur,
            category = LedgerCategory.MEMBERSHIP,
            amountMinor = kurus,
            description = aciklama,
            paymentMethod = PaymentMethod.CASH,
            occurredAtMs = simdi - 3 * gun,
            createdAtMs = 0,
        )
}
