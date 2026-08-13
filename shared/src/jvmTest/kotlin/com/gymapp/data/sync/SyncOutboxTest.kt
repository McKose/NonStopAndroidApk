package com.gymapp.data.sync

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.testTenants
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.db.inTransaction
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.repository.PackageRepository
import com.gymapp.domain.Ids
import com.gymapp.domain.Money
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Gönderim kuyruğunun davranışı.
 *
 * Kuyruğun tek işi "hangi satır gönderilmeyi bekliyor" bilgisini **kaybetmemek**.
 * Buradaki testler o sözü tutup tutmadığını gerçek veritabanı üzerinde sınıyor.
 */
class SyncOutboxTest {

    private lateinit var db: GymDatabase
    private lateinit var queue: SyncQueue
    private lateinit var packages: PackageRepository

    @BeforeTest
    fun setUp() {
        db = createTestDatabase()
        queue = SyncQueue(db.syncOutboxDao(), testTenants)
        packages = PackageRepository(db, db.packageDao(), queue, testTenants)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `yazma satiri kuyruga alir`() = runTest {
        val id = savePackage()

        val pending = db.syncOutboxDao().peek(TEST_TENANT, limit = 10)
        assertEquals(1, pending.size)
        assertEquals(SyncTable.PACKAGES.tableName, pending.first().entityTable)
        assertEquals(id, pending.first().entityId)
    }

    /**
     * Aynı satır tekrar değişince kuyrukta ikinci kayıt oluşmamalı ve **ilk**
     * kayıt korunmalı: kayıt bir işaretçiden ibaret olduğu için ikincisi bilgi
     * taşımaz, eskiyi korumak da FIFO sırasını bozmaz.
     */
    @Test
    fun `ayni satirin ikinci degisikligi yeni kayit acmaz`() = runTest {
        val id = savePackage()
        val first = db.syncOutboxDao().peek(TEST_TENANT, limit = 10).single()

        savePackage(packageId = id, name = "Güncellenmiş")

        val pending = db.syncOutboxDao().peek(TEST_TENANT, limit = 10)
        assertEquals(1, pending.size, "Aynı satır için tek bekleyen kayıt olmalı")
        assertEquals(first.id, pending.single().id, "İlk kayıt korunmalı")
        assertEquals(first.enqueuedAtMs, pending.single().enqueuedAtMs)
    }

    /** Farklı satırlar ayrı ayrı kuyruğa girer. */
    @Test
    fun `farkli satirlar ayri kayit acar`() = runTest {
        savePackage(name = "A")
        savePackage(name = "B")

        assertEquals(2, db.syncOutboxDao().peek(TEST_TENANT, limit = 10).size)
    }

    /**
     * Silme de kuyruğa girmeli.
     *
     * Tombstone satır kuyruğa girmezse silme sunucuya hiç gitmez ve kayıt diğer
     * cihazlarda yaşamaya devam eder — sessizce "geri gelen" kayıtlar.
     */
    @Test
    fun `silme kuyruga girer`() = runTest {
        val id = savePackage()
        // Kaydetmenin bıraktığı kaydı düşür ki silmenin kendi kaydını görelim.
        val afterSave = db.syncOutboxDao().peek(TEST_TENANT, limit = 10).single()
        db.syncOutboxDao().removeIfUnchanged(afterSave.id, afterSave.enqueuedAtMs)

        packages.deletePackage(id)

        val pending = db.syncOutboxDao().peek(TEST_TENANT, limit = 10)
        assertEquals(1, pending.size)
        assertEquals(id, pending.single().entityId)
    }

    /**
     * Yazma ile kuyruğa alma **aynı transaction'da**: biri geri alınırsa diğeri de
     * geri alınmalı.
     *
     * Bu, kuyruk tasarımının dayandığı sözün kendisi. Tutmasaydı ya değişiklik
     * sunucuya hiç gitmezdi (satır var, kayıt yok) ya da var olmayan bir
     * değişiklik gönderilmeye çalışılıp kuyruk tıkanırdı (kayıt var, satır yok).
     */
    @Test
    fun `transaction geri alininca kuyruk kaydi da geri alinir`() = runTest {
        val id = Ids.new()

        // Repository'nin `savePackage`'i kendi transaction'ını açıyor; onu burada
        // ikinci bir transaction'a sarmak iç içe transaction olurdu ve bu proje
        // bilinçli olarak Room'un o davranışına bel bağlamıyor. Bu yüzden test
        // yazma + kuyruğa alma ikilisini doğrudan kuruyor — sınanan şey zaten
        // repository'nin kendisi değil, `inTransaction`'ın verdiği söz.
        assertFailsWith<IllegalStateException> {
            db.inTransaction {
                val nowMs = 1_700_000_000_000L
                db.packageDao().insertPackage(
                    PackageEntity(
                        id = id,
                        tenantId = TEST_TENANT,
                        name = "Yarıda kalan",
                        type = TrainingType.FITNESS,
                        category = PackageCategory.INDIVIDUAL,
                        validityDays = 30,
                        sessionCount = 10,
                        basePriceMinor = Money.ofMajor(100.0).minor,
                        createdAtMs = nowMs,
                        updatedAtMs = nowMs,
                    )
                )
                queue.enqueue(SyncTable.PACKAGES, id, TEST_TENANT, nowMs)

                throw IllegalStateException("kasıtlı hata")
            }
        }

        assertNull(db.packageDao().getPackageById(id), "Satır geri alınmalı")
        assertEquals(
            0,
            db.syncOutboxDao().peek(TEST_TENANT, limit = 10).size,
            "Kuyruk kaydı da geri alınmalı",
        )
    }

    @Test
    fun `basarili gonderim kaydi kuyruktan dusurur`() = runTest {
        savePackage()
        val entry = db.syncOutboxDao().peek(TEST_TENANT, limit = 10).single()

        val removed = db.syncOutboxDao().removeIfUnchanged(entry.id, entry.enqueuedAtMs)

        assertEquals(1, removed)
        assertEquals(0, db.syncOutboxDao().pendingCount(TEST_TENANT))
    }

    /**
     * Gönderim sürerken satır tekrar değişmişse silme eşleşmemeli.
     *
     * Koşulsuz silseydik gönderim penceresinde yapılan değişiklik sessizce
     * kaybolurdu: satır güncel değil ama kuyrukta da kaydı yok.
     */
    @Test
    fun `gonderim penceresinde degisen kayit dusmez`() = runTest {
        savePackage()
        val entry = db.syncOutboxDao().peek(TEST_TENANT, limit = 10).single()

        val removed = db.syncOutboxDao().removeIfUnchanged(entry.id, entry.enqueuedAtMs + 1)

        assertEquals(0, removed, "enqueuedAtMs eşleşmiyorsa silme olmamalı")
        assertNotNull(db.syncOutboxDao().peek(TEST_TENANT, limit = 10).singleOrNull())
    }

    private suspend fun savePackage(
        packageId: String? = null,
        name: String = "Test paketi",
    ): String = packages.savePackage(
        packageId = packageId,
        name = name,
        type = TrainingType.FITNESS,
        category = PackageCategory.INDIVIDUAL,
        basePrice = Money.ofMajor(500.0),
        validityDays = 30,
        sessionCount = 10,
    ).getOrThrow()
}
