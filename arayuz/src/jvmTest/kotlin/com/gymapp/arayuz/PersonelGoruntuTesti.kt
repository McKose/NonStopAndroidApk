package com.gymapp.arayuz

import androidx.compose.runtime.Composable
import com.gymapp.arayuz.personel.PersonelEkrani
import com.gymapp.arayuz.personel.PersonelFormHedefi
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.StaffRole
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Personel yönetimi ekranının çizim testi.
 *
 * Ekranın asıl kararı yetki: [PersonelEkrani] `yazabilir` `false` iken ekleme
 * düğmesini, satır tıklamasını ve silme simgesini çizmiyor. Yanlış bağlanırsa
 * yetkisiz kullanıcı maaş düzenleyebilir görünür — ve bunu derleme
 * yakalayamaz.
 *
 * Diyalog `AlertDialog` olduğu için ekransız sahnede güvenilir çiziliyor;
 * o yüzden açık hâli de sınanıyor.
 */
class PersonelGoruntuTesti {

    private val personeller = listOf(
        personel("per-1", "Can Öz", StaffRole.TRAINER, maas = 3_500_000, oran = 4000),
        personel("per-2", "Elif Ak", StaffRole.MANAGER, maas = 5_000_000, oran = 0),
    )

    private fun ekran(
        yazabilir: Boolean = true,
        formHedefi: PersonelFormHedefi? = null,
        silinecek: StaffEntity? = null,
    ): @Composable () -> Unit = {
        PersonelEkrani(
            personeller = personeller,
            yazabilir = yazabilir,
            formHedefi = formHedefi,
            silinecek = silinecek,
            onGeri = {}, onYeniPersonel = {}, onPersonelSec = {},
            onFormKapat = {}, onKaydet = { _, _ -> },
            onSilIste = {}, onSilOnayla = {}, onSilVazgec = {},
        )
    }

    @Test
    fun `personel listesi ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("personel", icerik = ekran()))
    }

    @Test
    fun `salt okunur liste ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("personel-salt-okunur", icerik = ekran(yazabilir = false)))
    }

    /**
     * Yetki görünümü gerçekten değiştiriyor mu.
     *
     * `yazabilir` bağlanmamış olsaydı iki görüntü aynı çıkardı: eğitmen
     * ekleme düğmesini ve silme simgesini görürdü. Sunucu tarafı yazmayı
     * yine reddederdi, ama kullanıcı yapabileceğini sanıp deneyecekti.
     */
    @Test
    fun `yazma yetkisi goruntuyu degistiriyor`() {
        val yazabilen = ekraniCiz("personel", icerik = ekran(yazabilir = true)).readBytes()
        val okuyan = ekraniCiz("personel-salt-okunur", icerik = ekran(yazabilir = false)).readBytes()

        assertTrue(
            !yazabilen.contentEquals(okuyan),
            "Yazma yetkisi görüntüyü değiştirmedi — yetki kontrolü ekrana bağlı değil",
        )
    }

    /**
     * Yeni kayıt ile düzenleme farklı diyalog gösteriyor mu.
     *
     * `PersonelFormHedefi(null)` yeni kayıt, dolu olan düzenleme. Ayrım
     * kaybolsaydı düzenleme diyaloğu boş açılır ve kullanıcı mevcut
     * personelin üstüne boş değerler yazardı.
     */
    @Test
    fun `yeni kayit ile duzenleme farkli`() {
        val yeni = ekraniCiz(
            "personel-diyalog-yeni",
            yukseklik = 1600,
            icerik = ekran(formHedefi = PersonelFormHedefi(null)),
        ).readBytes()

        val duzenleme = ekraniCiz(
            "personel-diyalog-duzenleme",
            yukseklik = 1600,
            icerik = ekran(formHedefi = PersonelFormHedefi(personeller[0])),
        ).readBytes()

        assertTrue(
            !yeni.contentEquals(duzenleme),
            "Yeni kayıt ile düzenleme aynı çizildi — form mevcut değerlerle dolmuyor olabilir",
        )
    }

    @Test
    fun `silme onayi ciziliyor`() {
        val dosya = ekraniCiz("personel-silme-onayi", icerik = ekran(silinecek = personeller[0]))
        cizildiginiDogrula(dosya)
    }

    private fun personel(
        id: String,
        ad: String,
        rol: StaffRole,
        maas: Long,
        oran: Int,
    ) = StaffEntity(
        id = id,
        tenantId = "t",
        fullName = ad,
        title = "Eğitmen",
        role = rol,
        branch = "Reformer",
        commissionBasisPoints = oran,
        monthlySalaryMinor = maas,
        phone = "05001112233",
        nickname = ad.substringBefore(' '),
        createdAtMs = 0,
        updatedAtMs = 0,
    )
}
