package com.gymapp.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Arka plan turunun "tekrar dene" kararı.
 *
 * Bu kararın iki yönde de bedeli var ve ikisi de sessiz:
 *  - geçici bir engele "bitti" demek → veri, kullanıcı uygulamayı açana kadar
 *    sunucuya gitmez; hiçbir hata görünmez,
 *  - kalıcı bir engele "tekrar dene" demek → zamanlayıcı cihazı boşuna
 *    uyandırmaya devam eder ve her turda aynı reddi alır.
 *
 * Bu yüzden her durum tek tek yazılı. Testin `when` üzerinden dolaşmak yerine
 * beklenen değerleri elle sayması bilinçli: uygulamanın kendisini tekrarlayan
 * bir test, uygulamadaki hatayı da tekrarlar.
 */
class BackgroundSyncTest {

    @Test
    fun `tur tamamlandiysa is bitmis sayilir`() {
        assertEquals(
            BackgroundSyncResult.DONE,
            SyncState.Done(pushed = 5, pulled = 3, atMs = 1L).backgroundResult(),
        )
    }

    @Test
    fun `hicbir sey degismediyse de is bitmis sayilir`() {
        assertEquals(
            BackgroundSyncResult.DONE,
            SyncState.Done(pushed = 0, pulled = 0, atMs = 1L).backgroundResult(),
        )
    }

    /**
     * Ağ sorunu tekrar denenmeli.
     *
     * Bu, arka plan işinin var olma sebebi: uygulama kapalıyken ağ geri
     * geldiğinde kuyruğun boşalması gerekiyor.
     */
    @Test
    fun `gecici sorun tekrar denenir`() {
        assertEquals(
            BackgroundSyncResult.RETRY,
            sorun(reason = "Bağlantı sorunu", retryable = true).backgroundResult(),
        )
    }

    /**
     * Sunucunun reddettiği kayıtlar tekrar denenmez.
     *
     * Tekrar denemek her turda aynı `403`'ü alır. Kayıt kuyrukta işaretli
     * kalıyor ve kullanıcı Ayarlar ekranında sebebini görüyor; arka planda
     * süresiz tekrarlamak yalnızca pil harcardı.
     */
    @Test
    fun `kalici sorun tekrar denenmez`() {
        assertEquals(
            BackgroundSyncResult.DONE,
            sorun(reason = "3 kayıt sunucu tarafından reddedildi.", retryable = false)
                .backgroundResult(),
        )
    }

    /**
     * Giriş yapılmamışsa tekrar denenmiyor.
     *
     * Oturum arka planda açılmıyor; `RETRY` dönmek çıkış yapmış bir cihazı
     * üstel geri çekilmeyle sürekli uyandırmak olurdu.
     */
    @Test
    fun `oturum yoksa tekrar denenmez`() {
        assertEquals(BackgroundSyncResult.DONE, SyncState.NoSession.backgroundResult())
    }

    /**
     * Başka bir tur koşuyorsa arka plan işi çekiliyor.
     *
     * Koordinatör tek seferlik: koşan tur kuyruğu zaten boşaltacak. Tekrar
     * denemek aynı işi ikinci kez planlamak olurdu.
     */
    @Test
    fun `tur zaten kosuyorsa tekrar denenmez`() {
        assertEquals(BackgroundSyncResult.DONE, SyncState.Running.backgroundResult())
    }

    @Test
    fun `beklenmeyen bos durumda sonsuz tekrara girilmiyor`() {
        assertEquals(BackgroundSyncResult.DONE, SyncState.Idle.backgroundResult())
    }

    /**
     * Sorunun tekrar denenebilirliği **yalnızca** bayrağa bakıyor.
     *
     * Gerekçe metnine bakan bir uygulama, mesaj her düzenlendiğinde sessizce
     * bozulurdu. Bu iddia iki aynı metni farklı bayraklarla karşılaştırıyor:
     * sonuçlar farklı çıkmalı.
     */
    @Test
    fun `karar mesaj metnine degil bayraga bakiyor`() {
        val metin = "Aynı gerekçe metni"
        assertEquals(
            BackgroundSyncResult.RETRY,
            sorun(reason = metin, retryable = true).backgroundResult(),
        )
        assertEquals(
            BackgroundSyncResult.DONE,
            sorun(reason = metin, retryable = false).backgroundResult(),
        )
    }

    private fun sorun(reason: String, retryable: Boolean) = SyncState.Problem(
        reason = reason,
        pushed = 0,
        pulled = 0,
        failed = 1,
        retryable = retryable,
    )
}
