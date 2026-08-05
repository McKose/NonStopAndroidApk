package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.StaffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {

    @Query("""
        SELECT * FROM staff
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
        ORDER BY fullName ASC
    """)
    fun getAllStaff(tenantId: String): Flow<List<StaffEntity>>

    /**
     * `REPLACE` yerine `ABORT`: aynı kullanıcı adına sahip bir kayıt varsa
     * sessizce üzerine yazmak yerine hata döner, çağıran anlamlı mesaj üretir.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStaff(staff: StaffEntity)

    @Update
    suspend fun updateStaff(staff: StaffEntity)

    @Query("SELECT * FROM staff WHERE id = :id AND deletedAtMs IS NULL")
    suspend fun getStaffById(id: String): StaffEntity?

    @Query("""
        SELECT * FROM staff
        WHERE tenantId = :tenantId AND nickname = :nickname AND deletedAtMs IS NULL
        LIMIT 1
    """)
    suspend fun getStaffByNickname(tenantId: String, nickname: String): StaffEntity?

    /** Tombstone silme; personele bağlı randevular ve hakediş kayıtları öksüz kalmaz. */
    @Query("UPDATE staff SET deletedAtMs = :nowMs, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun softDelete(id: String, nowMs: Long)
}
