package com.gymapp.arayuz

import androidx.compose.runtime.Composable
import com.gymapp.arayuz.takvim.TakvimEkrani
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.TrainingType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Takvim ekranının çizim testi.
 *
 * ### Saat dilimi tuzağı
 * Ekran randevuları yerel saate göre 9–21 satırlarına dağıtıyor ve
 * `TarihBicimi.saatSayisi` cihazın saat dilimini kullanıyor. CI koşucusu
 * UTC, geliştirme makinesi başka bir dilim olabilir; aynı epoch değeri iki
 * yerde farklı satıra düşer.
 *
 * Örnek zamanlar bu yüzden **hem UTC'de hem İstanbul'da** çalışma
 * saatlerinin içinde kalacak şekilde seçildi (UTC 10:00 = İstanbul 13:00).
 * Seçilmeseydi randevular çalışma saatlerinin dışına düşer, hiç çizilmez ve
 * "dolu gün" testi boş günden ayırt edilemezdi — yani sessizce hiçbir şey
 * sınamayan bir test olurdu.
 *
 * ### Sheet neden sınanmıyor
 * Randevu ekleme `ModalBottomSheet` kullanıyor; kendi penceresini ve
 * animasyonunu getirdiği için ekransız Skia sahnesinde güvenilir çizilmiyor.
 * Durum diyaloğu (düz `AlertDialog`) sınanıyor.
 */
class TakvimGoruntuTesti {

    /** 25 Ağustos 2026 — UTC 10:00, İstanbul 13:00. */
    private val gunMs = 1787652000000L

    /** Aynı gün, UTC 13:00 / İstanbul 16:00. */
    private val ikinciSaatMs = 1787662800000L

    private val uyeler = listOf(
        uye("uye-1", "Ayşe Yılmaz"),
        uye("uye-2", "Mehmet Demir"),
    )

    private val personeller = listOf(personel("per-1", "Can Öz"))

    private val randevular = listOf(
        randevu("ran-1", "uye-1", "per-1", gunMs, AppointmentState.SCHEDULED),
        randevu("ran-2", "uye-2", "per-1", ikinciSaatMs, AppointmentState.COMPLETED),
        // Üyesi ve eğitmeni eşlemede yok: "Bilinmeyen" dalları da çizilsin.
        randevu("ran-3", "yok", "yok", ikinciSaatMs, AppointmentState.CANCELLED),
    )

    private fun ekran(
        gun: Long = gunMs,
        randevular: List<AppointmentEntity> = this.randevular,
        secilenRandevu: AppointmentEntity? = null,
    ): @Composable () -> Unit = {
        TakvimEkrani(
            secilenGunMs = gun,
            randevular = randevular,
            uyeler = uyeler,
            personeller = personeller,
            randevuEklemeAcik = false,
            secilenRandevu = secilenRandevu,
            onGeri = {}, onOncekiGun = {}, onSonrakiGun = {}, onBugun = {},
            onRandevuEklemeAc = {}, onRandevuEklemeKapat = {}, onRandevuSec = {},
            onRandevuEkle = { _, _, _, _ -> }, onDurumGuncelle = { _, _, _ -> },
        )
    }

    @Test
    fun `dolu gun ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("takvim", icerik = ekran()))
    }

    @Test
    fun `bos gun ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("takvim-bos", icerik = ekran(randevular = emptyList())))
    }

    @Test
    fun `durum diyalogu ciziliyor`() {
        val dosya = ekraniCiz("takvim-durum-diyalogu", icerik = ekran(secilenRandevu = randevular[0]))
        cizildiginiDogrula(dosya)
    }

    /**
     * Randevular gerçekten çiziliyor mu.
     *
     * Dolu gün ile boş gün aynı çıkıyorsa ya liste ekrana bağlanmamış ya da
     * saat eşlemesi randevuları çalışma saatlerinin dışına atıyor demektir.
     * İkisi de "her saat müsait görünen" bir takvim üretir — kullanıcı dolu
     * bir günü boş sanır.
     */
    @Test
    fun `randevular gunu degistiriyor`() {
        val dolu = ekraniCiz("takvim", icerik = ekran()).readBytes()
        val bos = ekraniCiz("takvim-bos", icerik = ekran(randevular = emptyList())).readBytes()

        assertTrue(
            !dolu.contentEquals(bos),
            "Dolu gün boş günle aynı çizildi — randevular saat satırlarına düşmüyor olabilir",
        )
    }

    /**
     * Seçili gün başlıkta yazıyor mu.
     *
     * `secilenGunMs` bağlanmamış olsaydı ileri/geri okları tarihi değiştirir
     * ama başlık sabit kalırdı; kullanıcı hangi güne baktığını bilemezdi.
     */
    @Test
    fun `secili gun basligi degisiyor`() {
        val bugun = ekraniCiz("takvim", icerik = ekran()).readBytes()
        val yarin = ekraniCiz(
            "takvim-ertesi-gun",
            icerik = ekran(gun = gunMs + 86_400_000L, randevular = emptyList()),
        ).readBytes()

        assertTrue(
            !bugun.contentEquals(yarin),
            "Seçili gün görüntüyü değiştirmedi — başlık `secilenGunMs`'e bağlı değil",
        )
    }

    // ─── Örnek veri ─────────────────────────────────────────────────────────

    private fun uye(id: String, ad: String) = MemberEntity(
        id = id, tenantId = "t", fullName = ad, phone = "05001112233",
        createdAtMs = 0, updatedAtMs = 0,
    )

    private fun personel(id: String, ad: String) = StaffEntity(
        id = id, tenantId = "t", fullName = ad, title = "Eğitmen",
        branch = "Reformer", phone = "05001112233", nickname = ad.substringBefore(' '),
        createdAtMs = 0, updatedAtMs = 0,
    )

    private fun randevu(
        id: String,
        uyeId: String,
        personelId: String,
        baslangic: Long,
        durum: AppointmentState,
    ) = AppointmentEntity(
        id = id, tenantId = "t", memberId = uyeId, staffId = personelId,
        trainingType = TrainingType.REFORMER,
        startTimeMs = baslangic, endTimeMs = baslangic + 3_600_000,
        state = durum,
        createdAtMs = 0, updatedAtMs = 0,
    )
}
