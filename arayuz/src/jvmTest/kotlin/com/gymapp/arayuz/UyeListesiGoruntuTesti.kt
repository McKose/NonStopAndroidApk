package com.gymapp.arayuz

import androidx.compose.runtime.Composable
import com.gymapp.arayuz.uyeler.UyeListesiEkrani
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.domain.MemberScope
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentState
import com.gymapp.domain.StaffRole
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Üye listesinin çizim testi.
 *
 * Ekranın iki gerçek kararı var: üyelik durumunun **bitiş tarihinden**
 * türetilmesi (kayıtlı kolondan değil) ve çekmecenin role göre süzülmesi.
 * İkisi de sessizce yanlış olabilecek türden.
 *
 * `simdiMs` sabit veriliyor. Ekran saati kendi okusaydı bu test her koşuda
 * farklı sonuç verirdi — parametreye çıkarmanın görünür faydalarından biri.
 */
class UyeListesiGoruntuTesti {

    /** 25 Ağustos 2026, 12:00 (İstanbul). */
    private val simdi = 1787649600000L

    private val gun = 86_400_000L

    private val uyeler = listOf(
        // Süresi devam eden, ödemesi tamam.
        uye("uye-1", "Ayşe Yılmaz", bitis = simdi + 30 * gun, odendi = true),
        // Süresi dolmuş: "Aktif" görünmemeli.
        uye("uye-2", "Mehmet Demir", bitis = simdi - 5 * gun, odendi = true),
        // Ödeme bekliyor: tahsilat rozetini çiziyor.
        uye("uye-3", "Zeynep Kaya", bitis = simdi + 10 * gun, odendi = false),
    )

    private fun ekran(
        uyeler: List<MemberEntity> = this.uyeler,
        rol: StaffRole = StaffRole.ADMIN,
        yukleniyor: Boolean = false,
        kapsam: MemberScope = MemberScope.ALL,
        kapsamSecilebilir: Boolean = false,
        baglantiYok: Boolean = false,
        tahsilatUyesi: MemberEntity? = null,
        tahsilatBorcu: Money? = null,
    ): @Composable () -> Unit = {
        UyeListesiEkrani(
            uyeler = uyeler,
            yukleniyor = yukleniyor,
            arama = "",
            rol = rol,
            kapsam = kapsam,
            kapsamSecilebilir = kapsamSecilebilir,
            personelBaglantisiYok = baglantiYok,
            simdiMs = simdi,
            tahsilatUyesi = tahsilatUyesi,
            tahsilatBorcu = tahsilatBorcu,
            onAramaDegisti = {}, onKapsamDegisti = {}, onUyeAc = {},
            onYeniUye = {}, onYenile = {}, onTahsilatIste = {},
            onTahsilatOnayla = {}, onTahsilatVazgec = {},
            onPaketler = {}, onFinans = {}, onMarket = {}, onAyarlar = {},
        )
    }

    @Test
    fun `dolu liste ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("uye-listesi", icerik = ekran()))
    }

    @Test
    fun `bos liste ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("uye-listesi-bos", icerik = ekran(uyeler = emptyList())))
    }

    @Test
    fun `kapsam secici ciziliyor`() {
        val dosya = ekraniCiz(
            "uye-listesi-kapsam",
            icerik = ekran(rol = StaffRole.TRAINER, kapsamSecilebilir = true, kapsam = MemberScope.MINE),
        )
        cizildiginiDogrula(dosya)
    }

    /**
     * Boş listenin sebebi kapsam mı.
     *
     * "Üye bulunamadı" ile "size atanmış randevusu olan üye yok" farklı
     * şeyler: ilki kullanıcıyı salonda hiç üye yok sanmaya iter. İki metin
     * aynı dala bağlanmış olsaydı bu ayrım sessizce kaybolurdu.
     */
    @Test
    fun `bos liste mesaji kapsama gore degisiyor`() {
        val tumu = ekraniCiz(
            "uye-listesi-bos",
            icerik = ekran(uyeler = emptyList(), kapsam = MemberScope.ALL),
        ).readBytes()
        val benim = ekraniCiz(
            "uye-listesi-bos-kendi",
            icerik = ekran(uyeler = emptyList(), kapsam = MemberScope.MINE),
        ).readBytes()

        assertTrue(
            !tumu.contentEquals(benim),
            "Boş liste mesajı kapsama göre değişmedi — iki durum aynı metni gösteriyor olabilir",
        )
    }

    /**
     * Üyelik durumu bitiş tarihinden türetiliyor mu.
     *
     * Aynı üyeler, ileri alınmış bir "şimdi" ile farklı çizilmeli: süresi
     * devam edenler "Süresi doldu"ya döner ve renkleri değişir. Değişmiyorsa
     * ekran kayıtlı durumu okuyor demektir ve süresi dolmuş üye "Aktif"
     * görünmeye devam eder — bu ekranın düzeltilmiş asıl hatası buydu.
     */
    @Test
    fun `uyelik durumu simdiye bagli`() {
        val bugun = ekraniCiz("uye-listesi", icerik = ekran()).readBytes()

        val gelecek = ekraniCiz("uye-listesi-gelecek") {
            UyeListesiEkrani(
                uyeler = uyeler,
                yukleniyor = false,
                arama = "",
                rol = StaffRole.ADMIN,
                kapsam = MemberScope.ALL,
                kapsamSecilebilir = false,
                personelBaglantisiYok = false,
                // Bir yıl sonrası: üç üyenin de süresi dolmuş olur.
                simdiMs = simdi + 365 * gun,
                tahsilatUyesi = null,
                tahsilatBorcu = null,
                onAramaDegisti = {}, onKapsamDegisti = {}, onUyeAc = {},
                onYeniUye = {}, onYenile = {}, onTahsilatIste = {},
                onTahsilatOnayla = {}, onTahsilatVazgec = {},
                onPaketler = {}, onFinans = {}, onMarket = {}, onAyarlar = {},
            )
        }.readBytes()

        assertTrue(
            !bugun.contentEquals(gelecek),
            "Üyelik durumu `simdiMs`'e tepki vermedi — kayıtlı durum okunuyor olabilir",
        )
    }

    private fun uye(id: String, ad: String, bitis: Long, odendi: Boolean) = MemberEntity(
        id = id,
        tenantId = "t",
        fullName = ad,
        phone = "05001112233",
        endDateMs = bitis,
        paymentStatus = if (odendi) PaymentState.PAID else PaymentState.PENDING,
        createdAtMs = 0,
        updatedAtMs = 0,
    )
}
