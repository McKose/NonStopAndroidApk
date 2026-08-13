package com.gymapp.data.sync

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.testTenants
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.SyncOutboxEntity
import com.gymapp.domain.Ids
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Motorun kuyruk yönetimi.
 *
 * Kuyruk gerçek veritabanında, uzak uç sahte. Ayrım bilinçli: sınanan şey SQL
 * değil motorun kararları (sıra, durma, geri çekilme), ve gerçek bir sunucu bu
 * kararları sınamak için ne gerekli ne de yeterli olurdu.
 */
class SyncEngineTest {

    private lateinit var db: GymDatabase
    private lateinit var queue: SyncQueue

    /** Ne istenirse onu döndüren, çağrıları kaydeden uzak uç. */
    private class FakeRemote(
        private val behaviour: suspend (SyncTable, String) -> PushResult =
            { _, _ -> PushResult.Success },
    ) : RemoteDataSource {
        val calls = mutableListOf<Pair<SyncTable, String>>()

        override suspend fun push(table: SyncTable, entityId: String): PushResult {
            calls += table to entityId
            return behaviour(table, entityId)
        }
    }

    @BeforeTest
    fun setUp() {
        db = createTestDatabase()
        queue = SyncQueue(db.syncOutboxDao(), testTenants)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `basarili tur kuyrugu bosaltir`() = runTest {
        enqueue("a", atMs = 1_000)
        enqueue("b", atMs = 2_000)
        val remote = FakeRemote()

        val outcome = engine(remote).syncOnce(TEST_TENANT)

        assertEquals(SyncOutcome(pushed = 2), outcome)
        assertEquals(0, db.syncOutboxDao().pendingCount(TEST_TENANT))
    }

    /**
     * Sıra korunmalı: satırlar arasında bağımlılık var (sipariş üyeye, randevu
     * eğitmene bakıyor). Sırayı bozmak sunucuda henüz var olmayan bir satıra
     * referans göndermek olurdu.
     */
    @Test
    fun `kayitlar kuyruga girdikleri sirayla gonderilir`() = runTest {
        enqueue("gec", atMs = 3_000)
        enqueue("erken", atMs = 1_000)
        enqueue("orta", atMs = 2_000)
        val remote = FakeRemote()

        engine(remote).syncOnce(TEST_TENANT)

        assertEquals(listOf("erken", "orta", "gec"), remote.calls.map { it.second })
    }

    /**
     * Geçici hata turu durdurur.
     *
     * Ağ yoksa sıradakiler de patlayacak; hepsini denemek `attemptCount`
     * sayaçlarını boş yere şişirir ve geri çekilmeyi yanlış yere uzatırdı.
     */
    @Test
    fun `gecici hata turu durdurur ve kayit kuyrukta kalir`() = runTest {
        enqueue("a", atMs = 1_000)
        enqueue("b", atMs = 2_000)
        val remote = FakeRemote { _, _ -> PushResult.Retryable("ağ yok") }

        val outcome = engine(remote).syncOnce(TEST_TENANT)

        assertTrue(outcome.stopped)
        assertEquals(1, outcome.failed)
        assertEquals(1, remote.calls.size, "İlk hatadan sonra denenmemeli")
        assertEquals(2, db.syncOutboxDao().pendingCount(TEST_TENANT))
    }

    /**
     * Kalıcı hata turu durdurmaz.
     *
     * Sunucunun reddettiği tek kayıt arkasındaki her şeyi süresiz bekletmemeli.
     */
    @Test
    fun `kalici hata kuyrugu tikamaz`() = runTest {
        enqueue("bozuk", atMs = 1_000)
        enqueue("saglam", atMs = 2_000)
        val remote = FakeRemote { _, id ->
            if (id == "bozuk") PushResult.Permanent("sunucu reddetti") else PushResult.Success
        }

        val outcome = engine(remote).syncOnce(TEST_TENANT)

        assertEquals(1, outcome.pushed)
        assertEquals(1, outcome.failed)
        assertFalse(outcome.stopped)

        // Bozuk kayıt silinmedi: sessizce kaybolması hem veri hem teşhis kaybı olurdu.
        val remaining = db.syncOutboxDao().peek(TEST_TENANT, 10).single()
        assertEquals("bozuk", remaining.entityId)
        assertEquals(1, remaining.attemptCount)
        assertEquals("sunucu reddetti", remaining.lastError)
    }

    /** Hata alan kayıt geri çekilme süresi dolmadan tekrar denenmez. */
    @Test
    fun `geri cekilme suresi dolmadan tekrar denenmez`() = runTest {
        enqueue("a", atMs = 1_000)
        val remote = FakeRemote { _, _ -> PushResult.Permanent("hata") }
        var clock = 10_000L

        engine(remote, now = { clock }).syncOnce(TEST_TENANT)
        assertEquals(1, remote.calls.size)

        // Taban geri çekilme 5 sn; 2 sn sonra sıra bu kayda gelmemeli.
        clock += 2_000
        val outcome = engine(remote, now = { clock }).syncOnce(TEST_TENANT)

        assertEquals(1, remote.calls.size, "Süre dolmadan tekrar denenmemeli")
        assertEquals(1, outcome.skipped)
    }

    @Test
    fun `geri cekilme suresi dolunca tekrar denenir`() = runTest {
        enqueue("a", atMs = 1_000)
        val remote = FakeRemote { _, _ -> PushResult.Permanent("hata") }
        var clock = 10_000L

        engine(remote, now = { clock }).syncOnce(TEST_TENANT)

        clock += 6_000
        engine(remote, now = { clock }).syncOnce(TEST_TENANT)

        assertEquals(2, remote.calls.size)
    }

    /**
     * Gönderim sürerken satır tekrar değişmişse kayıt kuyrukta kalmalı.
     *
     * Aksi hâlde o pencerede yapılan değişiklik sunucuya hiç gitmez ve kimse
     * fark etmezdi.
     */
    @Test
    fun `gonderim sirasinda degisen satir kuyrukta kalir`() = runTest {
        enqueue("a", atMs = 1_000)
        val remote = FakeRemote { _, id ->
            // Gönderim sürerken satır tekrar değişti: kayıt yenilendi.
            val current = db.syncOutboxDao().peek(TEST_TENANT, 1).single()
            db.syncOutboxDao().removeIfUnchanged(current.id, current.enqueuedAtMs)
            enqueue(id, atMs = 9_000)
            PushResult.Success
        }

        val outcome = engine(remote).syncOnce(TEST_TENANT)

        assertEquals(0, outcome.pushed, "Eski kayıt düşürülmemeli")
        assertEquals(1, outcome.skipped)
        assertEquals(1, db.syncOutboxDao().pendingCount(TEST_TENANT))
    }

    /** Tanınmayan tablo kalıcı hata sayılır; kayıt silinmez. */
    @Test
    fun `taninmayan tablo kalici hata sayilir`() = runTest {
        db.syncOutboxDao().enqueue(
            SyncOutboxEntity(
                id = Ids.new(),
                tenantId = TEST_TENANT,
                entityTable = "artik_olmayan_tablo",
                entityId = "x",
                enqueuedAtMs = 1_000,
            )
        )
        val remote = FakeRemote()

        val outcome = engine(remote).syncOnce(TEST_TENANT)

        assertEquals(0, remote.calls.size, "Uzak uca hiç gidilmemeli")
        assertEquals(1, outcome.failed)
        assertEquals(1, db.syncOutboxDao().pendingCount(TEST_TENANT))
    }

    @Test
    fun `bos kuyrukta tur sonuc uretmez`() = runTest {
        val outcome = engine(FakeRemote()).syncOnce(TEST_TENANT)

        assertEquals(SyncOutcome(), outcome)
    }

    // ─── Yardımcılar ────────────────────────────────────────────────────────

    private fun engine(
        remote: RemoteDataSource,
        now: () -> Long = { 10_000L },
    ) = SyncEngine(db.syncOutboxDao(), remote, now = now)

    private suspend fun enqueue(entityId: String, atMs: Long) =
        queue.enqueue(SyncTable.PACKAGES, entityId, TEST_TENANT, atMs)
}
