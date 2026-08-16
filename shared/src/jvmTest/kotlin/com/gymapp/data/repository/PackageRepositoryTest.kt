package com.gymapp.data.repository

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.sync.LocalRowPayloadProvider
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncTable
import com.gymapp.data.testTenants
import com.gymapp.domain.Money
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Paket deposunun silme ve canlandırma davranışı.
 *
 * Bu dosya, repository katmanındaki **ilk** testlerden biri. O katmanda hiç test
 * yoktu ve paranın gerçekten yazıldığı yer orası; aşağıdaki iki hata da tam
 * olarak o boşlukta yaşadı.
 *
 * Gerçek SQLite üzerinde koşuyor, sahte DAO ile değil: sınanan şeylerin ikisi de
 * SQL semantiğinde (tombstone süzgeci, birincil anahtar çakışması), sahte bir
 * DAO bunların hiçbirini gösteremezdi.
 */
class PackageRepositoryTest {

    private val db: GymDatabase = createTestDatabase()
    private val repo = PackageRepository(
        database = db,
        packageDao = db.packageDao(),
        syncQueue = SyncQueue(db.syncOutboxDao(), testTenants),
        tenants = testTenants,
    )

    @AfterTest
    fun kapat() {
        db.close()
    }

    private suspend fun paketOlustur(ad: String = "Aylık"): String =
        repo.savePackage(
            name = ad,
            type = TrainingType.FITNESS,
            category = PackageCategory.INDIVIDUAL,
            basePrice = Money.ofMajor(1000.0),
            validityDays = 30,
            sessionCount = 10,
        ).getOrElse { fail("Paket kurulamadı: $it") }

    /**
     * Silinen paketin gönderilecek içeriği hâlâ üretilebiliyor.
     *
     * Bu, sessiz bir veri kaybının testi. `PackageDao.getPackageById` tombstone
     * süzdüğü için gönderim yolu silinen paketin içeriğini üretemiyor, gönderim
     * kalıcı hatayla duruyordu. Silme sunucuya hiç gitmiyor; paket panelde ve
     * diğer cihazlarda canlı kalıyor, personel satmaya devam ediyordu.
     */
    @Test
    fun `silinen paketin gonderilecek icerigi uretilebiliyor`() = runTest {
        val id = paketOlustur()
        repo.deletePackage(id)

        val icerik = LocalRowPayloadProvider(db).payload(SyncTable.PACKAGES, id)

        assertNotNull(
            icerik,
            "Silinen paketin içeriği üretilemezse silme sunucuya hiç gitmez",
        )
    }

    /** Silme kuyruğa giriyor: girmezse silme yine sunucuya ulaşmaz. */
    @Test
    fun `silme kuyruga giriyor`() = runTest {
        val id = paketOlustur()
        repo.deletePackage(id)

        val kuyruk = db.syncOutboxDao().peek(TEST_TENANT, limit = 10)
        assertTrue(
            kuyruk.any { it.entityTable == SyncTable.PACKAGES.tableName && it.entityId == id },
            "Silinen paket kuyrukta olmalı",
        )
    }

    /** Silinen paket ekranlara artık gelmiyor — süzgeç repository'ye taşındı. */
    @Test
    fun `silinen paket ekranlara gelmiyor`() = runTest {
        val id = paketOlustur()
        repo.deletePackage(id)

        assertNull(
            repo.getPackageById(id),
            "Silinen paket ekranlarda görünmemeli",
        )
    }

    /**
     * Silinmiş bir kimliğe kayıt, çökmek yerine paketi canlandırıyor.
     *
     * Önceki hâlde DAO silinmişleri süzdüğü için depo "böyle bir paket yok" deyip
     * `insert` deniyor, birincil anahtar çakışıyor ve ham SQLite hatası dışarı
     * çıkıyordu — üstelik `PackageRepository` bu sınıf hatayı `StaffRepository`
     * gibi anlaşılır mesaja çevirmiyordu.
     */
    @Test
    fun `silinmis kimlige kayit paketi canlandiriyor`() = runTest {
        val id = paketOlustur(ad = "Aylık")
        repo.deletePackage(id)

        val sonuc = repo.savePackage(
            packageId = id,
            name = "Aylık (yeniden)",
            type = TrainingType.REFORMER,
            category = PackageCategory.DUET,
            basePrice = Money.ofMajor(1500.0),
            validityDays = 45,
            sessionCount = 12,
        )

        assertTrue(sonuc.isSuccess, "Canlandırma başarısız: ${sonuc.exceptionOrNull()}")
        assertEquals(id, sonuc.getOrNull(), "Aynı kimlik korunmalı; yeni satır açılmamalı")

        val paket = repo.getPackageById(id)
        assertNotNull(paket, "Paket canlandırılmalı ve ekranlara geri gelmeli")
        assertEquals("Aylık (yeniden)", paket.name)
        assertEquals(45, paket.validityDays, "Yeni değerler yazılmalı")
        assertNull(paket.deletedAtMs, "Tombstone temizlenmeli")
    }

    /**
     * Canlandırma sonrası içerik hâlâ üretilebiliyor ve satır kuyrukta.
     *
     * Canlandırmanın kendisi de bir değişiklik; kuyruğa girmezse sunucuda paket
     * silinmiş kalır ve iki taraf kalıcı olarak ayrışır.
     */
    @Test
    fun `canlandirma da kuyruga giriyor`() = runTest {
        val id = paketOlustur()
        repo.deletePackage(id)
        db.syncOutboxDao().peek(TEST_TENANT, limit = 10).forEach {
            db.syncOutboxDao().removeIfUnchanged(it.id, it.enqueuedAtMs)
        }

        repo.savePackage(
            packageId = id,
            name = "Geri geldi",
            type = TrainingType.FITNESS,
            category = PackageCategory.INDIVIDUAL,
            basePrice = Money.ofMajor(1000.0),
            validityDays = 30,
            sessionCount = 10,
        ).getOrElse { fail("Canlandırma başarısız: $it") }

        val kuyruk = db.syncOutboxDao().peek(TEST_TENANT, limit = 10)
        assertTrue(
            kuyruk.any { it.entityId == id },
            "Canlandırma kuyruğa girmeli",
        )
    }
}
