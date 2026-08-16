package com.gymapp.data.local.db

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.dao.MaintenanceDao
import com.gymapp.data.local.entity.SyncOutboxEntity
import com.gymapp.data.local.entity.SyncPullStateEntity
import com.gymapp.data.sync.SampleRows
import com.gymapp.data.sync.SyncTable
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Çıkışta cihazdaki veri gerçekten siliniyor mu?
 *
 * ### Kapatılan sızıntı
 * Çıkış yalnızca jetonu ve salon kimliğini siliyordu; **indirilmiş satırlar
 * cihazda kalıyordu.**
 *
 * Somut senaryo: ADMIN giriş yapar, senkronizasyon personel tablosunu (maaşlar
 * dahil — `PersonnelScreen` bunları ekrana basıyor) ve defteri indirir. Çıkar.
 * Aynı cihazda TRAINER giriş yapar ve bu satırları **salt okunur olarak görür**.
 * Rol kontrolü yalnızca yazma düğmelerini gizliyordu, veriyi değil; sunucudaki
 * erişim kuralları ise doğru çalışıyordu. Sızıntı tamamen cihazda kalan
 * kopyadaydı.
 *
 * ### Neden Room'un `clearAllTables`'ı kullanılmadı
 * O işlev Room'un ortak (KMP) yüzeyinde yok — bu proje ilk denemede tam olarak
 * bu yüzden derlenmedi. Silme [MaintenanceDao] içinde açıkça yazıldı: iOS'ta da
 * geçerli ve testi gerçek SQLite üzerinde koşuyor.
 *
 * ### Testin asıl işi
 * Silmenin kendisi tek satır; asıl mesele **hangi tabloların** silindiği. Elle
 * yazılmış bir liste, on ikinci tablo eklendiğinde sessizce eksik kalırdı — ve
 * eksik kalan tablo tam olarak sızan tablo olurdu. Bu yüzden aşağıdaki test
 * [SyncTable] üzerinde geziyor: senkronize edilen dokuz tablodan biri
 * [MaintenanceDao.wipeAll] içinde unutulursa test düşer.
 */
class ClearAllTablesTest {

    private val db = createTestDatabase()
    private val bakim: MaintenanceDao = db.maintenanceDao()

    @AfterTest
    fun kapat() {
        db.close()
    }

    /** Senkronize edilen her tablonun satır sayacı. */
    private suspend fun sayim(tablo: SyncTable): Int = when (tablo) {
        SyncTable.MEMBERS -> bakim.countMembers()
        SyncTable.PACKAGES -> bakim.countPackages()
        SyncTable.PRODUCTS -> bakim.countProducts()
        SyncTable.APPOINTMENTS -> bakim.countAppointments()
        SyncTable.STAFF -> bakim.countStaff()
        SyncTable.ORDERS -> bakim.countOrders()
        SyncTable.MEASUREMENTS -> bakim.countMeasurements()
        SyncTable.LEDGER_ENTRIES -> bakim.countLedgerEntries()
        SyncTable.STOCK_MOVEMENTS -> bakim.countStockMovements()
    }

    /** Senkronize edilen dokuz tablonun hepsine birer satır yazar. */
    private suspend fun doldur() {
        val t = TEST_TENANT
        db.memberDao().insertMember(SampleRows.member.copy(tenantId = t))
        db.packageDao().upsertFromServer(SampleRows.packageRow.copy(tenantId = t))
        db.productDao().upsertFromServer(SampleRows.product.copy(tenantId = t))
        db.appointmentDao().upsertFromServer(SampleRows.appointment.copy(tenantId = t))
        db.staffDao().insertStaff(SampleRows.staff.copy(tenantId = t))
        db.orderDao().upsertFromServer(SampleRows.order.copy(tenantId = t))
        db.measurementDao().upsertFromServer(SampleRows.measurement.copy(tenantId = t))
        db.ledgerDao().insert(SampleRows.ledgerEntry.copy(tenantId = t))
        db.stockMovementDao().upsertFromServer(SampleRows.stockMovement.copy(tenantId = t))

        // Senkronize edilmeyen iki defter tablosu.
        db.syncOutboxDao().enqueue(
            SyncOutboxEntity(
                id = "outbox-1",
                tenantId = t,
                entityTable = SyncTable.MEMBERS.tableName,
                entityId = SampleRows.member.id,
                enqueuedAtMs = 1,
            )
        )
        db.syncPullStateDao().save(
            SyncPullStateEntity(
                tenantId = t,
                entityTable = SyncTable.MEMBERS.tableName,
                lastPulledAtMs = 1,
            )
        )
    }

    /**
     * **Sınıfı kapatan test:** senkronize edilen hiçbir tablo geride kalmıyor.
     *
     * Tek tek tablo saymak yerine [SyncTable] üzerinde geziyor; onuncu tablo
     * eklendiğinde `sayim` derlenmez (`when` tükenmez) ve unutulan silme burada
     * yakalanır.
     */
    @Test
    fun `cikista senkronize edilen hicbir tablo geride kalmiyor`() = runTest {
        doldur()

        // Karşı kontrol: temizlemeden önce hepsi gerçekten doluydu. Bu olmasa
        // test boş bir veritabanında da geçerdi, yani hiçbir şey sınamazdı.
        for (tablo in SyncTable.entries) {
            assertEquals(1, sayim(tablo), "Kurgu bozuk: ${tablo.tableName} zaten boştu")
        }

        bakim.wipeAll()

        for (tablo in SyncTable.entries) {
            assertEquals(
                0, sayim(tablo),
                "${tablo.tableName} çıkıştan sonra da doluydu; sonraki kullanıcıya açık kalırdı",
            )
        }
    }

    /** En hassas tablo adıyla ayrıca sınanıyor: personel satırı maaş taşıyor. */
    @Test
    fun `cikista maasli personel kaydi cihazda kalmiyor`() = runTest {
        doldur()
        assertNotNull(
            db.staffDao().getStaffById(SampleRows.staff.id),
            "Kurgu bozuk: temizlemeden önce satır zaten yoktu",
        )

        bakim.wipeAll()

        assertNull(
            db.staffDao().getStaffById(SampleRows.staff.id),
            "Personel satırı kalmamalı; maaşlar sonraki kullanıcıya açık olurdu",
        )
    }

    /**
     * Senkronizasyon defterleri de temizleniyor.
     *
     * Kuyruk kalsaydı daha kötüsü olurdu: sonraki kullanıcının oturumuyla
     * **önceki kullanıcının** değişiklikleri gönderilmeye çalışılırdı. Çekme su
     * işareti kalsaydı da yeni kullanıcı eski zaman damgasından devam eder ve
     * kendi verisinin bir kısmını hiç indirmezdi.
     */
    @Test
    fun `cikista senkronizasyon defterleri bosaliyor`() = runTest {
        doldur()
        assertTrue(
            db.syncOutboxDao().peek(TEST_TENANT, limit = 10).isNotEmpty(),
            "Kurgu bozuk: kuyruk zaten boştu",
        )

        bakim.wipeAll()

        assertEquals(0, bakim.countSyncOutbox(), "Gönderim kuyruğu kalmamalı")
        assertEquals(0, bakim.countSyncPullState(), "Çekme su işareti kalmamalı")
    }
}
