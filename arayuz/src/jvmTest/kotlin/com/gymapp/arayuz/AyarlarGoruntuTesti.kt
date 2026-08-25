package com.gymapp.arayuz

import com.gymapp.arayuz.ayarlar.AyarlarEkrani
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
    ): @Composable () -> Unit = {
        AyarlarEkrani(
            salonAdi = "Non Stop GYM",
            senkDurumu = durum,
            bekleyen = bekleyen,
            cikistaBekleyen = cikistaBekleyen,
            onGeri = {}, onPersonel = {}, onSimdiEsitle = {},
            onCikisIste = {}, onCikisiOnayla = {}, onCikistanVazgec = {},
            onSalonAdiKaydet = {},
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
