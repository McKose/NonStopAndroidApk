package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gymapp.data.local.entity.MeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    /** Silinmiş (tombstone) kayıtlar listelenmez. */
    @Query("""
        SELECT * FROM measurements
        WHERE tenantId = :tenantId AND memberId = :memberId AND deletedAtMs IS NULL
        ORDER BY dateMs DESC
    """)
    fun observeForMember(tenantId: String, memberId: String): Flow<List<MeasurementEntity>>

    @Insert
    suspend fun insert(measurement: MeasurementEntity)

    /**
     * Kaydı fiziksel olarak silmez; tombstone işaretler.
     *
     * Silmenin de senkronize olması gerekiyor: fiziksel silme, diğer cihazda
     * kaydın "hiç silinmemiş" gibi geri gelmesine yol açardı.
     */
    @Query("UPDATE measurements SET deletedAtMs = :nowMs, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun softDelete(id: String, nowMs: Long)
}
