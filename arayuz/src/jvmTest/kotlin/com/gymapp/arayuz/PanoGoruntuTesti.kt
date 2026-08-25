package com.gymapp.arayuz

import androidx.compose.runtime.Composable
import com.gymapp.arayuz.pano.PanoEkrani
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.StaffRole
import com.gymapp.domain.TrainingType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Panonun çizim testi.
 *
 * Panonun asıl kararı yetki: hangi kısayolun çizileceğini [StaffRole]
 * belirliyor. Bu, ekranda görünmeyen ama yanlış olduğunda eğitmene
 * görmemesi gereken bir kapıyı açan türden bir hata — ve derleme bunu
 * yakalayamaz, çünkü yanlış rol de derlenir.
 */
class PanoGoruntuTesti {

    private val uyeler = listOf(
        uye("uye-1", "Ayşe Yılmaz"),
        uye("uye-2", "Mehmet Demir"),
    )

    private val personeller = listOf(personel("per-1", "Can Öz"))

    private val randevular = listOf(
        randevu("ran-1", "uye-1", "per-1", 1787133600000L), // 13:00 (İstanbul)
        // Üyesi ve personeli eşlemede YOK: "Bilinmeyen Üye" / "Eğitmen
        // atanmadı" dalları da çizilsin.
        randevu("ran-2", "uye-silinmis", "per-yok", 1787139000000L), // 14:30
    )

    private fun ekran(
        rol: StaffRole = StaffRole.ADMIN,
        randevular: List<AppointmentEntity> = this.randevular,
        uyarilar: List<String> = listOf("3 üyenin paketi bu hafta bitiyor."),
        baglantiYok: Boolean = false,
    ): @Composable () -> Unit = {
        PanoEkrani(
            rol = rol,
            aktifUye = 42,
            gunlukRandevular = randevular,
            uyeler = uyeler,
            personeller = personeller,
            kritikUyarilar = uyarilar,
            personelBaglantisiYok = baglantiYok,
            onUyeler = {}, onFinans = {}, onMarket = {},
            onTakvim = {}, onPaketler = {}, onAyarlar = {},
        )
    }

    @Test
    fun `yonetici panosu ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("pano-yonetici", icerik = ekran()))
    }

    @Test
    fun `bos pano ciziliyor`() {
        val dosya = ekraniCiz(
            "pano-bos",
            icerik = ekran(randevular = emptyList(), uyarilar = emptyList()),
        )
        cizildiginiDogrula(dosya)
    }

    /**
     * Rol kısayolları gerçekten süzüyor mu.
     *
     * `rol` ekrana bağlanmamış olsaydı iki görüntü aynı çıkardı ve eğitmen
     * panosunda Finans/Market kısayolları görünürdü. Ekranın en pahalı
     * hatası bu: yetki kararı görünmez şekilde yanlış olur.
     */
    @Test
    fun `rol kisayollari suzuyor`() {
        val yonetici = ekraniCiz("pano-yonetici", icerik = ekran(rol = StaffRole.ADMIN)).readBytes()
        val egitmen = ekraniCiz("pano-egitmen", icerik = ekran(rol = StaffRole.TRAINER)).readBytes()

        assertTrue(
            !yonetici.contentEquals(egitmen),
            "Rol görüntüyü değiştirmedi — kısayol süzgeci ekrana bağlanmamış olabilir",
        )
    }

    /**
     * Personel bağlantısı uyarısı çiziliyor mu.
     *
     * Bayrak bağlanmamış olsaydı eğitmen boş bir pano görür ve sebebini
     * öğrenemezdi — yapılacak iş salon sahibinde olduğu için bu uyarı
     * kaybolduğunda sorun kendiliğinden çözülmez.
     */
    @Test
    fun `personel baglantisi uyarisi goruntuyu degistiriyor`() {
        val normal = ekraniCiz("pano-yonetici", icerik = ekran()).readBytes()
        val uyarili = ekraniCiz("pano-baglanti-yok", icerik = ekran(baglantiYok = true)).readBytes()

        assertTrue(
            !normal.contentEquals(uyarili),
            "Personel bağlantısı uyarısı çizilmedi",
        )
    }

    // ─── Örnek veri ─────────────────────────────────────────────────────────

    private fun uye(id: String, ad: String) = MemberEntity(
        id = id, tenantId = "t", fullName = ad, phone = "05000000000",
        createdAtMs = 0, updatedAtMs = 0,
    )

    private fun personel(id: String, ad: String) = StaffEntity(
        id = id, tenantId = "t", fullName = ad, title = "Eğitmen",
        phone = "05000000000", nickname = ad.substringBefore(' '),
        createdAtMs = 0, updatedAtMs = 0,
    )

    private fun randevu(id: String, uyeId: String, personelId: String, baslangic: Long) =
        AppointmentEntity(
            id = id, tenantId = "t", memberId = uyeId, staffId = personelId,
            trainingType = TrainingType.REFORMER,
            startTimeMs = baslangic, endTimeMs = baslangic + 3_600_000,
            createdAtMs = 0, updatedAtMs = 0,
        )
}
