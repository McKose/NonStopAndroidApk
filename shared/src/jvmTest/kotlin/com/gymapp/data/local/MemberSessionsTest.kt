package com.gymapp.data.local

import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.domain.Ids
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Seans sayacı kuralları — **üretimde koşan SQL üzerinde**.
 *
 * Bu kuralın bir zamanlar `SessionQuota.consume`/`restore` biçiminde Kotlin
 * kopyası ve o kopyanın testleri vardı; kopyayı kimse çağırmıyordu. Yani yeşil
 * testler, üretimde koşmayan bir kuralı doğruluyordu. Kopya kaldırıldı, kapsam
 * burada geri kazanıldı: aynı iddialar bu kez gerçek `UPDATE` ifadelerine karşı
 * sınanıyor.
 */
class MemberSessionsTest {

    private lateinit var db: GymDatabase

    @BeforeTest
    fun setUp() {
        db = createTestDatabase()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seans tuketimi kotayi bir azaltir`() = runTest {
        val id = insertMember(total = 10, remaining = 10)

        db.memberDao().decrementSession(id, NOW)

        assertEquals(9, db.memberDao().getMemberById(id)?.remainingSessions)
    }

    @Test
    fun `kota sifirin altina inmez`() = runTest {
        val id = insertMember(total = 10, remaining = 0)

        db.memberDao().decrementSession(id, NOW)

        assertEquals(0, db.memberDao().getMemberById(id)?.remainingSessions)
    }

    @Test
    fun `sinirsiz kota tuketimde degismez`() = runTest {
        val id = insertMember(total = null, remaining = null)

        db.memberDao().decrementSession(id, NOW)

        assertNull(db.memberDao().getMemberById(id)?.remainingSessions)
    }

    @Test
    fun `randevu geri alininca seans iade edilir`() = runTest {
        val id = insertMember(total = 10, remaining = 5)

        db.memberDao().incrementSession(id, NOW)

        assertEquals(6, db.memberDao().getMemberById(id)?.remainingSessions)
    }

    @Test
    fun `iade kota tavanini asamaz`() = runTest {
        val id = insertMember(total = 10, remaining = 10)

        db.memberDao().incrementSession(id, NOW)

        assertEquals(10, db.memberDao().getMemberById(id)?.remainingSessions)
    }

    @Test
    fun `sinirsiz kotada iade etkisizdir`() = runTest {
        val id = insertMember(total = null, remaining = null)

        db.memberDao().incrementSession(id, NOW)

        assertNull(db.memberDao().getMemberById(id)?.remainingSessions)
    }

    /** Tüketim ve iade birbirini götürür. */
    @Test
    fun `tuketim ve iade birbirini goturur`() = runTest {
        val id = insertMember(total = 10, remaining = 10)

        db.memberDao().decrementSession(id, NOW)
        db.memberDao().incrementSession(id, NOW)

        assertEquals(10, db.memberDao().getMemberById(id)?.remainingSessions)
    }

    /** Sayaç değişince `updatedAtMs` de tazelenmeli; delta senkronizasyon buna bakıyor. */
    @Test
    fun `sayac degisikligi updatedAtMs tazeler`() = runTest {
        val id = insertMember(total = 10, remaining = 10)

        db.memberDao().decrementSession(id, NOW + 1_000)

        assertEquals(NOW + 1_000, db.memberDao().getMemberById(id)?.updatedAtMs)
    }

    private suspend fun insertMember(total: Int?, remaining: Int?): String {
        val id = Ids.new()
        db.memberDao().insertMember(
            MemberEntity(
                id = id,
                tenantId = Ids.DEFAULT_TENANT,
                fullName = "Test Üye",
                // Her test kendi bellek içi veritabanını açıyor, dolayısıyla
                // numaranın tekil olması yeterli — rastgeleliğe gerek yok.
                phone = "+905551234567",
                totalSessions = total,
                remainingSessions = remaining,
                createdAtMs = NOW,
                updatedAtMs = NOW,
            )
        )
        return id
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
