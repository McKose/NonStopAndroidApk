package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.MultiSportRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MultiSportRateDao {

    @Query("SELECT * FROM multisport_rates ORDER BY effectiveFromMs DESC")
    fun getAll(): Flow<List<MultiSportRateEntity>>

    /**
     * Belirli bir tarihteki (ms) geçerli MS ücretini döner. effectiveFromMs <= date
     * ve (supersededByMs IS NULL OR supersededByMs > date) koşulunu sağlar.
     */
    @Query("""
        SELECT * FROM multisport_rates
        WHERE effectiveFromMs <= :atMs
          AND (supersededByMs IS NULL OR supersededByMs > :atMs)
        ORDER BY effectiveFromMs DESC
        LIMIT 1
    """)
    suspend fun getRateAt(atMs: Long): MultiSportRateEntity?

    @Query("SELECT * FROM multisport_rates WHERE supersededByMs IS NULL ORDER BY effectiveFromMs DESC LIMIT 1")
    suspend fun getCurrent(): MultiSportRateEntity?

    @Insert
    suspend fun insert(rate: MultiSportRateEntity): Long

    @Update
    suspend fun update(rate: MultiSportRateEntity)

    /** Cari aktif kaydın supersededByMs'ini kapatıp yeni kayıt ekler. */
    @Transaction
    suspend fun supersede(newAmount: Double, note: String? = null, nowMs: Long = System.currentTimeMillis()): Long {
        val current = getCurrent()
        if (current != null) {
            update(current.copy(supersededByMs = nowMs))
        }
        return insert(
            MultiSportRateEntity(
                amount = newAmount,
                effectiveFromMs = nowMs,
                supersededByMs = null,
                note = note
            )
        )
    }
}
