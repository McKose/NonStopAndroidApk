package com.gymapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Şifre kuralının testleri.
 *
 * Aynı örnekler panelin `sifre.test.js` dosyasında da var. Kural iki yerde
 * yazılı olmak zorunda (tarayıcı Kotlin çalıştırmıyor) ve ayrışması sessiz
 * olurdu: bir tarafta kabul edilen şifre diğerinde reddedilir, kullanıcı da
 * hangisinin doğru olduğunu bilemezdi.
 */
class SifreKuraliTest {

    private fun gecersizMesaji(yeni: String, tekrar: String = yeni, mevcut: String = "eski1234"): String {
        val sonuc = SifreKurali.dogrula(yeni, tekrar, mevcut)
        assertIs<SifreSonucu.Gecersiz>(sonuc, "kabul edildi: '$yeni'")
        return sonuc.mesaj
    }

    @Test
    fun `gecerli sifre kabul ediliyor`() {
        val sonuc = SifreKurali.dogrula("yeniSifre1", "yeniSifre1", "eski1234")
        assertIs<SifreSonucu.Gecerli>(sonuc)
        assertEquals("yeniSifre1", sonuc.sifre)
    }

    @Test
    fun `kisa sifre reddediliyor`() {
        assertTrue(gecersizMesaji("kisa12").contains("${SifreKurali.EN_AZ_UZUNLUK}"))
        // Tam sınır kabul ediliyor: "en az 8" sekizi dışarıda bırakmamalı.
        assertIs<SifreSonucu.Gecerli>(SifreKurali.dogrula("12345678", "12345678", "eski1234"))
    }

    @Test
    fun `tekrar tutmuyorsa reddediliyor`() {
        assertTrue(gecersizMesaji("yeniSifre1", tekrar = "yeniSifre2").contains("tutmuyor"))
    }

    /**
     * Yeni şifre eskisiyle aynı olamaz.
     *
     * Bu akışın var oluş sebebi geçici şifreyi kalıcı olmaktan çıkarmak; aynı
     * şifreyi tekrar yazmak işlemin yapıldığı izlenimi verir ama hiçbir şey
     * değiştirmez. Sunucu da reddediyor, ama İngilizce ve ağ turundan sonra.
     */
    @Test
    fun `yeni sifre eskisiyle ayni olamaz`() {
        assertTrue(gecersizMesaji("eski1234", mevcut = "eski1234").contains("aynı"))
    }

    @Test
    fun `mevcut sifre bos birakilamaz`() {
        assertTrue(gecersizMesaji("yeniSifre1", mevcut = "").contains("Mevcut"))
    }

    /**
     * Baştaki/sondaki boşluk KIRPILMIYOR, reddediliyor.
     *
     * Kırpılsaydı kullanıcı "abc12345 " yazar, sunucuya "abc12345" giderdi;
     * sonra giriş ekranında yazdığının aynısını yazıp "şifre yanlış" cevabını
     * alırdı ve kaybettiği karakteri hiçbir yerde göremezdi.
     */
    @Test
    fun `bosluklu sifre kirpilmiyor reddediliyor`() {
        assertTrue(gecersizMesaji("yeniSifre1 ").contains("boşluk"))
        assertTrue(gecersizMesaji(" yeniSifre1").contains("boşluk"))
    }

    /** Şifrenin içindeki boşluk serbest: yalnızca uçlardaki sorun. */
    @Test
    fun `ortadaki bosluk kabul ediliyor`() {
        val sonuc = SifreKurali.dogrula("iki kelime", "iki kelime", "eski1234")
        assertIs<SifreSonucu.Gecerli>(sonuc)
        assertEquals("iki kelime", sonuc.sifre, "şifre olduğu gibi geçmeli")
    }
}
