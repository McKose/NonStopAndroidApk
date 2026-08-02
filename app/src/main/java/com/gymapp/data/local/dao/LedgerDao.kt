package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gymapp.data.local.entity.LedgerEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Finans defteri erişimi.
 *
 * Defter append-only olduğu için burada bilinçli olarak `@Update`/`@Delete` yoktur.
 * Toplamlar veritabanında hesaplanır; eski `FinanceViewModel` tüm kayıtları belleğe
 * çekip istemcide filtreliyordu.
 */
@Dao
interface LedgerDao {

    @Insert
    suspend fun insert(entry: LedgerEntryEntity)

    @Insert
    suspend fun insertAll(entries: List<LedgerEntryEntity>)

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun getById(id: String): LedgerEntryEntity?

    /** Dönem kayıtları — yarı açık aralık `[start, end)`. */
    @Query("""
        SELECT * FROM ledger_entries
        WHERE tenantId = :tenantId AND occurredAtMs >= :startMs AND occurredAtMs < :endMs
        ORDER BY occurredAtMs DESC
    """)
    fun observeBetween(tenantId: String, startMs: Long, endMs: Long): Flow<List<LedgerEntryEntity>>

    @Query("""
        SELECT * FROM ledger_entries
        WHERE tenantId = :tenantId AND memberId = :memberId
        ORDER BY occurredAtMs DESC
    """)
    fun observeForMember(tenantId: String, memberId: String): Flow<List<LedgerEntryEntity>>

    /**
     * Bir dönemdeki net tutar.
     *
     * Ters kayıtlar ve iptal ettikleri kayıtlar birbirini götürsün diye,
     * ters kaydı bulunan kayıtlar toplamdan düşülür.
     */
    @Query("""
        SELECT COALESCE(SUM(e.amountMinor), 0) FROM ledger_entries e
        WHERE e.tenantId = :tenantId
          AND e.type = :type
          AND e.occurredAtMs >= :startMs AND e.occurredAtMs < :endMs
          AND e.reversesId IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ledger_entries r WHERE r.reversesId = e.id
          )
    """)
    fun observeNetTotal(tenantId: String, type: String, startMs: Long, endMs: Long): Flow<Long>

    /**
     * Üyenin bakiyesi (kuruş): tahakkuk − tahsilat.
     *
     * Pozitif değer borcu gösterir. Ödeme durumu artık ayrı bir kolonda
     * saklanmıyor; defterden türetiliyor.
     */
    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN e.type = 'CHARGE' THEN e.amountMinor
                 WHEN e.type = 'PAYMENT' THEN -e.amountMinor
                 ELSE 0 END
        ), 0)
        FROM ledger_entries e
        WHERE e.tenantId = :tenantId
          AND e.memberId = :memberId
          AND e.reversesId IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ledger_entries r WHERE r.reversesId = e.id
          )
    """)
    suspend fun outstandingBalanceMinor(tenantId: String, memberId: String): Long

    /** Bir kaydın daha önce ters kayıtla iptal edilip edilmediği. */
    @Query("SELECT EXISTS(SELECT 1 FROM ledger_entries WHERE reversesId = :entryId)")
    suspend fun isReversed(entryId: String): Boolean

    /** Bir randevudan doğan, henüz iptal edilmemiş hakediş kayıtları. */
    @Query("""
        SELECT * FROM ledger_entries e
        WHERE e.appointmentId = :appointmentId
          AND e.reversesId IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ledger_entries r WHERE r.reversesId = e.id
          )
    """)
    suspend fun activeEntriesForAppointment(appointmentId: String): List<LedgerEntryEntity>
}
