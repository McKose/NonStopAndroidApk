package com.gymapp.data.local.db

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.entity.SyncOutboxEntity
import com.gymapp.data.sync.SampleRows
import com.gymapp.data.sync.SyncTable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Çıkışta cihazdaki veri gerçekten siliniyor mu?
 *
 * ### Kapatılan sızıntı
 * Projede `clearAllTables` hiç çağrılmıyordu, yani çıkış yalnızca jetonu ve
 * salon kimliğini siliyordu; **indirilmiş satırlar cihazda kalıyordu.**
 *
 * Somut senaryo: ADMIN giriş yapar, senkronizasyon personel tablosunu (maaşlar
 * dahil — `PersonnelScreen` bunları ekrana basıyor) ve defteri indirir. Çıkar.
 * Aynı cihazda TRAINER giriş yapar ve bu satırları **salt okunur olarak görür**.
 * Rol kontrolü yalnızca yazma düğmelerini gizliyordu, veriyi değil; sunucudaki
 * erişim kuralları ise doğru çalışıyordu. Sızıntı tamamen cihazda kalan
 * kopyadaydı.
 *
 * ### Neden bu test
 * Temizlemenin kendisi tek satır. Asıl mesele **hangi tabloların** temizlendiği:
 * elle yazılmış bir `DELETE` listesi, on ikinci tablo eklendiğinde sessizce
 * eksik kalırdı — ve eksik kalan tablo tam olarak sızan tablo olurdu. Bu test,
 * en hassas iki tabloyu (maaşlı personel ve defter) ve kuyruğu adıyla sınıyor.
 *
 * Gerçek SQLite üzerinde koşuyor.
 */
class ClearAllTablesTest {

    private val db = createTestDatabase()

    @AfterTest
    fun kapat() {
        db.close()
    }

    /** Temizlikten önce veri gerçekten oradaydı — yoksa test hiçbir şey sınamaz. */
    private suspend fun doldur() {
        db.staffDao().insertStaff(SampleRows.staff.copy(tenantId = TEST_TENANT))
        db.memberDao().insertMember(SampleRows.member.copy(tenantId = TEST_TENANT))
        db.ledgerDao().insert(SampleRows.ledgerEntry.copy(tenantId = TEST_TENANT))
        db.syncOutboxDao().enqueue(
            SyncOutboxEntity(
                id = "outbox-1",
                tenantId = TEST_TENANT,
                entityTable = SyncTable.MEMBERS.tableName,
                entityId = SampleRows.member.id,
                enqueuedAtMs = 1,
            )
        )
    }

    @Test
    fun `cikista maasli personel kaydi cihazda kalmiyor`() = runTest {
        doldur()
        assertNotNull(
            db.staffDao().getStaffById(SampleRows.staff.id),
            "Kurgu bozuk: temizlemeden önce satır zaten yoktu",
        )

        db.clearAllTables()

        assertNull(
            db.staffDao().getStaffById(SampleRows.staff.id),
            "Çıkıştan sonra personel satırı cihazda kalmamalı; maaşlar sonraki kullanıcıya açık olurdu",
        )
    }

    @Test
    fun `cikista uye ve defter kayitlari cihazda kalmiyor`() = runTest {
        doldur()

        db.clearAllTables()

        assertNull(db.memberDao().getMemberById(SampleRows.member.id), "Üye satırı kalmamalı")
        assertTrue(
            db.ledgerDao().observeBetween(TEST_TENANT, 0, Long.MAX_VALUE).first().isEmpty(),
            "Defter kayıtları kalmamalı",
        )
    }

    /**
     * Kuyruk da temizleniyor.
     *
     * Kalsaydı daha kötüsü olurdu: sonraki kullanıcının oturumuyla, **önceki
     * kullanıcının** değişiklikleri gönderilmeye çalışılırdı.
     */
    @Test
    fun `cikista gonderim kuyrugu bosaliyor`() = runTest {
        doldur()
        assertTrue(
            db.syncOutboxDao().peek(TEST_TENANT, limit = 10).isNotEmpty(),
            "Kurgu bozuk: kuyruk zaten boştu",
        )

        db.clearAllTables()

        assertTrue(
            db.syncOutboxDao().peek(TEST_TENANT, limit = 10).isEmpty(),
            "Kuyruk kalsaydı önceki kullanıcının değişiklikleri sonraki oturumla gönderilirdi",
        )
    }
}
