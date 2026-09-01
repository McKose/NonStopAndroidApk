package com.gymapp.arayuz

import com.gymapp.arayuz.ayarlar.AyarlarEkrani
import com.gymapp.arayuz.ayarlar.SifreDurumu
import com.gymapp.arayuz.ayarlar.senkronizasyonOzeti
import androidx.compose.runtime.Composable
import com.gymapp.data.sync.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ayarlar ekranının çizim testi.
 *
 * Ekranın iki ayrı karar noktası var: eşitleme özeti (beş `SyncState` dalı) ve
 * çıkış uyarısı diyaloğu (`cikistaBekleyen` null mı değil mi). İkisi de
 * derlemenin yakalayamayacağı türden — yanlış dal da derlenir.
 */
class AyarlarGoruntuTesti {

    private fun ekran(
        durum: SyncState = SyncState.Idle,
        bekleyen: Int = 0,
        cikistaBekleyen: Int? = null,
        sifreDurumu: SifreDurumu = SifreDurumu.Bosta,
        sifreDiyaloguAcik: Boolean = false,
    ): @Composable () -> Unit = {
        AyarlarEkrani(
            salonAdi = "Non Stop GYM",
            senkDurumu = durum,
            bekleyen = bekleyen,
            cikistaBekleyen = cikistaBekleyen,
            sifreDurumu = sifreDurumu,
            sifreDiyaloguAcik = sifreDiyaloguAcik,
            onGeri = {}, onPersonel = {}, onSimdiEsitle = {},
            onCikisIste = {}, onCikisiOnayla = {}, onCikistanVazgec = {},
            onSalonAdiKaydet = {},
            onSifreDegistir = { _, _, _ -> },
            onSifreDiyaloguAc = {}, onSifreDiyaloguKapat = {},
        )
    }

    @Test
    fun `ayarlar listesi ciziliyor`() {
        cizildiginiDogrula(ekraniCiz("ayarlar", icerik = ekran()))
    }

    /**
     * Çıkış uyarısı diyaloğu çiziliyor mu.
     *
     * `cikistaBekleyen` ekrana hiç bağlanmamış olsaydı iki görüntü aynı çıkardı
     * ve kullanıcı gönderilmemiş verisini uyarı görmeden silerdi — bu ekranın
     * en pahalı hatası.
     */
    @Test
    fun `cikis uyarisi goruntuyu degistiriyor`() {
        val uyarisiz = ekraniCiz("ayarlar", icerik = ekran()).readBytes()
        val uyarili = ekraniCiz(
            "ayarlar-cikis-uyarisi",
            icerik = ekran(bekleyen = 3, cikistaBekleyen = 3),
        ).readBytes()

        assertTrue(
            !uyarisiz.contentEquals(uyarili),
            "Çıkış uyarısı çizilmedi — `cikistaBekleyen` ekrana bağlanmamış olabilir",
        )
    }

    // ─── Şifre değiştirme ───────────────────────────────────────────────────

    @Test
    fun `sifre diyalogu ciziliyor`() {
        cizildiginiDogrula(
            ekraniCiz("ayarlar-sifre", icerik = ekran(sifreDiyaloguAcik = true))
        )
    }

    /**
     * Diyalog gerçekten açılıyor mu.
     *
     * `sifreDiyaloguAcik` çizime bağlanmamış olsaydı satıra basmak hiçbir şey
     * yapmaz ve geçici şifreyi değiştirmenin yolu yine olmazdı — yani özellik
     * kodda var, kullanıcı için yok olurdu.
     */
    @Test
    fun `sifre diyalogu goruntuyu degistiriyor`() {
        val kapali = ekraniCiz("ayarlar", icerik = ekran()).readBytes()
        val acik = ekraniCiz("ayarlar-sifre", icerik = ekran(sifreDiyaloguAcik = true))
            .readBytes()

        assertTrue(!kapali.contentEquals(acik), "Şifre diyaloğu çizilmedi")
    }

    /**
     * Hata mesajı diyalogda görünüyor mu.
     *
     * Görünmeseydi "Değiştir"e basan kullanıcı hiçbir tepki alamaz, şifresinin
     * değiştiğini sanıp çıkardı — ve bir dahaki girişte hangi şifrenin geçerli
     * olduğunu bilemezdi.
     */
    @Test
    fun `sifre hatasi diyalogda goruniyor`() {
        val bosta = ekraniCiz("ayarlar-sifre", icerik = ekran(sifreDiyaloguAcik = true))
            .readBytes()
        val hatali = ekraniCiz(
            "ayarlar-sifre-hata",
            icerik = ekran(
                sifreDiyaloguAcik = true,
                sifreDurumu = SifreDurumu.Hata("Mevcut şifreniz yanlış."),
            ),
        ).readBytes()

        assertTrue(!bosta.contentEquals(hatali), "Hata mesajı çizilmedi")
    }

    /**
     * Başarı ekranda KALIYOR, sessizce kapanmıyor.
     *
     * Kapansaydı kullanıcı şifresinin gerçekten değişip değişmediğini bilemezdi
     * ve bu akışta bilememek pahalı: bir dahaki girişte hangi şifreyi yazacağını
     * bilmiyor demek.
     */
    @Test
    fun `sifre basarisi diyalogda goruniyor`() {
        val bosta = ekraniCiz("ayarlar-sifre", icerik = ekran(sifreDiyaloguAcik = true))
            .readBytes()
        val basarili = ekraniCiz(
            "ayarlar-sifre-basarili",
            icerik = ekran(sifreDiyaloguAcik = true, sifreDurumu = SifreDurumu.Basarili),
        ).readBytes()

        assertTrue(!bosta.contentEquals(basarili), "Başarı mesajı çizilmedi")
    }

    /**
     * Eşitleme durumu alt yazıya yansıyor mu.
     *
     * "Güncel" ile "Bağlantı yok. Bekleyen: 7" arasındaki fark, kullanıcının
     * verisinin gidip gitmediğini anlayabildiği tek yer.
     */
    @Test
    fun `senkron durumu goruntuyu degistiriyor`() {
        val guncel = ekraniCiz("ayarlar", icerik = ekran()).readBytes()
        val sorunlu = ekraniCiz(
            "ayarlar-senkron-sorunu",
            icerik = ekran(
                durum = SyncState.Problem(
                    reason = "Bağlantı yok.",
                    pushed = 0, pulled = 0, failed = 7, retryable = true,
                ),
                bekleyen = 7,
            ),
        ).readBytes()

        assertTrue(
            !guncel.contentEquals(sorunlu),
            "Eşitleme durumu görüntüyü değiştirmedi — özet ekrana bağlanmamış olabilir",
        )
    }

    /**
     * Özet metninin beş dalı.
     *
     * Çizim testi "farklı" olduğunu gösteriyor ama ne yazdığını göstermiyor;
     * metnin kendisi burada tek tek doğrulanıyor. `Done`'da inen kayıt sayısı
     * yalnızca sıfırdan büyükse ekleniyor — o koşul iki ayrı beklentiyle
     * sınanıyor, çünkü koşul tersine dönse de test kırmızıya dönmezdi.
     */
    @Test
    fun `senkron ozeti metni dogru`() {
        assertEquals("Eşitleniyor…", senkronizasyonOzeti(SyncState.Running, 4))
        assertEquals("Oturum yok", senkronizasyonOzeti(SyncState.NoSession, 4))
        assertEquals("Bekleyen değişiklik yok", senkronizasyonOzeti(SyncState.Idle, 0))
        assertEquals("Bekleyen: 4", senkronizasyonOzeti(SyncState.Idle, 4))
        assertEquals(
            "Bağlantı yok. Bekleyen: 7",
            senkronizasyonOzeti(
                SyncState.Problem("Bağlantı yok.", 0, 0, 7, retryable = true),
                7,
            ),
        )
        assertEquals(
            "Güncel",
            senkronizasyonOzeti(SyncState.Done(pushed = 2, pulled = 0, atMs = 0), 0),
        )
        assertEquals(
            "Güncel · 10 kayıt indirildi",
            senkronizasyonOzeti(SyncState.Done(pushed = 0, pulled = 10, atMs = 0), 0),
        )
        assertEquals(
            "Bekleyen: 1 · 3 kayıt indirildi",
            senkronizasyonOzeti(SyncState.Done(pushed = 0, pulled = 3, atMs = 0), 1),
        )
    }
}
