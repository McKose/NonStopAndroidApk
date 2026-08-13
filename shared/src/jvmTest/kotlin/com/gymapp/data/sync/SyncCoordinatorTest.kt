package com.gymapp.data.sync

import com.gymapp.data.auth.TenantProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tur yönetiminin davranışı.
 *
 * Motor ve veritabanı yok: sınanan şey kaç tur atıldığı, ne zaman durulduğu ve
 * dışarıya hangi durumun bildirildiği. Bunların hepsi sessizce yanlış olabilecek
 * kararlar — yanlış olduklarında uygulama çalışmaya devam eder, yalnızca veri
 * sunucuya gitmez.
 */
class SyncCoordinatorTest {

    private val tenant = "11111111-2222-3333-4444-555555555555"

    /** Sırayla verilen sonuçları döndüren, çağrıları sayan motor. */
    private class SahteRunner(
        private val sonuclar: List<SyncOutcome>,
        private val kapi: CompletableDeferred<Unit>? = null,
    ) : SyncRunner {
        var cagriSayisi = 0
        val salonlar = mutableListOf<String>()

        override suspend fun syncOnce(tenantId: String): SyncOutcome {
            cagriSayisi++
            salonlar += tenantId
            kapi?.await()
            return sonuclar.getOrElse(cagriSayisi - 1) { sonuclar.last() }
        }
    }

    // ─── Tur yönetimi ───────────────────────────────────────────────────────

    /**
     * Kuyruk boşalana kadar tur atılıyor.
     *
     * Motor tek çağrıda en fazla bir grup işliyor; tek tur atan bir koordinatör
     * 50'den fazla bekleyen kaydı olan bir cihazda kuyruğu asla bitiremezdi.
     */
    @Test
    fun `kuyruk bosalana kadar tur atar`() = runTest {
        val runner = SahteRunner(
            listOf(
                SyncOutcome(pushed = 50),
                SyncOutcome(pushed = 50),
                SyncOutcome(pushed = 7),
                SyncOutcome(pushed = 0),
            )
        )
        val koordinator = koordinator(runner)

        val durum = koordinator.syncNow()

        assertEquals(4, runner.cagriSayisi)
        assertEquals(SyncState.Done(pushed = 107, atMs = SAAT), durum)
    }

    @Test
    fun `bos kuyrukta tek tur atilir`() = runTest {
        val runner = SahteRunner(listOf(SyncOutcome()))

        val durum = koordinator(runner).syncNow()

        assertEquals(1, runner.cagriSayisi)
        assertEquals(SyncState.Done(pushed = 0, atMs = SAAT), durum)
    }

    /**
     * Geçici hata turu bitiriyor.
     *
     * Ağ yokken tur atmaya devam etmek `attemptCount` sayaçlarını şişirir ve geri
     * çekilme süresini yanlış yere uzatırdı — motorun tur içinde yaptığı şeyin
     * turlar arasındaki karşılığı bu.
     */
    @Test
    fun `gecici hatada durur ve sorunu bildirir`() = runTest {
        val runner = SahteRunner(
            listOf(
                SyncOutcome(pushed = 3),
                SyncOutcome(pushed = 1, failed = 1, stopped = true),
                SyncOutcome(pushed = 99),
            )
        )

        val durum = koordinator(runner).syncNow()

        assertEquals(2, runner.cagriSayisi, "Durdurulan turdan sonra devam edilmemeli")
        val sorun = assertIs<SyncState.Problem>(durum)
        assertEquals(4, sorun.pushed)
        assertEquals(1, sorun.failed)
        assertTrue(sorun.reason.contains("Bağlantı"), "Gerekçe: ${sorun.reason}")
    }

    /**
     * Reddedilen kayıtlar "bitti" diye raporlanmıyor.
     *
     * Kuyruk ilerlemiyor ama boş da değil; bunu başarı saymak, sunucunun
     * reddettiği kayıtların varlığını kullanıcıdan tamamen gizlerdi.
     */
    @Test
    fun `reddedilen kayitlar sorun olarak bildirilir`() = runTest {
        val runner = SahteRunner(listOf(SyncOutcome(pushed = 0, failed = 2)))

        val durum = koordinator(runner).syncNow()

        val sorun = assertIs<SyncState.Problem>(durum)
        assertEquals(2, sorun.failed)
        assertTrue(sorun.reason.contains("reddedildi"), "Gerekçe: ${sorun.reason}")
    }

    /**
     * Tur sınırı sonsuz döngüyü kesiyor.
     *
     * Kuyruktan düşürme mantığındaki bir hata "her turda gönderdim" diyen ama
     * kuyruğu hiç boşaltmayan bir döngü üretebilirdi: pili bitiren, hiçbir
     * belirti vermeyen türden bir hata.
     */
    @Test
    fun `tur sayisi sinirli`() = runTest {
        val runner = SahteRunner(listOf(SyncOutcome(pushed = 1)))

        val durum = koordinator(runner, maxRounds = 5).syncNow()

        assertEquals(5, runner.cagriSayisi)
        val sorun = assertIs<SyncState.Problem>(durum)
        assertTrue(sorun.reason.contains("sürüyor"), "Gerekçe: ${sorun.reason}")
    }

    // ─── Oturum ─────────────────────────────────────────────────────────────

    /** Giriş yapılmamışsa motora hiç gidilmiyor. */
    @Test
    fun `oturum yoksa gonderim denenmez`() = runTest {
        val runner = SahteRunner(listOf(SyncOutcome()))
        val koordinator = SyncCoordinator(
            runner = runner,
            tenants = TenantProvider { null },
            scope = this,
            now = { SAAT },
        )

        assertEquals(SyncState.NoSession, koordinator.syncNow())
        assertEquals(0, runner.cagriSayisi)
    }

    @Test
    fun `salon kimligi motora aktarilir`() = runTest {
        val runner = SahteRunner(listOf(SyncOutcome()))

        koordinator(runner).syncNow()

        assertEquals(listOf(tenant), runner.salonlar)
    }

    // ─── Tek seferlik koşma ─────────────────────────────────────────────────

    /**
     * Aynı anda iki tur koşmuyor.
     *
     * Koşarsa ikisi de aynı kayıtları okur ve aynı satır iki kez gönderilir.
     * İkinci çağrı beklemiyor: koşan tura "bitince bir tur daha at" diyip
     * dönüyor. Beklemek, toplu kayıt sırasında onlarca askıda coroutine
     * biriktirirdi.
     */
    @Test
    fun `es zamanli cagrilar tek tur olarak birlesir`() = runTest {
        val kapi = CompletableDeferred<Unit>()
        val runner = SahteRunner(listOf(SyncOutcome()), kapi = kapi)
        val koordinator = koordinator(runner)

        val ilk = async { koordinator.syncNow() }
        // İlk çağrının kilidi almasını bekle.
        while (runner.cagriSayisi == 0) kotlinx.coroutines.yield()

        val ikinci = async { koordinator.syncNow() }
        ikinci.await()

        kapi.complete(Unit)
        ilk.await()

        // İlk tur + tekrar isteği yüzünden atılan ikinci tur; ikinci çağrının
        // kendi turu yok.
        assertEquals(2, runner.cagriSayisi)
    }

    // ─── Yardımcılar ────────────────────────────────────────────────────────

    private fun kotlinx.coroutines.CoroutineScope.koordinator(
        runner: SyncRunner,
        maxRounds: Int = 40,
    ) = SyncCoordinator(
        runner = runner,
        tenants = TenantProvider { tenant },
        scope = this,
        now = { SAAT },
        maxRounds = maxRounds,
    )

    private companion object {
        const val SAAT = 1_700_000_000_000L
    }
}
