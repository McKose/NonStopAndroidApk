package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.MemberPackageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberPackageDao {

    @Query("SELECT * FROM member_packages WHERE member_id = :memberId ORDER BY created_at_ms DESC")
    fun getAllForMember(memberId: Long): Flow<List<MemberPackageEntity>>

    @Query("SELECT * FROM member_packages WHERE member_id = :memberId AND status = 'ACTIVE' ORDER BY end_date_ms ASC")
    fun getActiveForMember(memberId: Long): Flow<List<MemberPackageEntity>>

    @Query("SELECT * FROM member_packages WHERE member_id = :memberId AND status = 'ACTIVE' ORDER BY end_date_ms ASC")
    suspend fun getActiveForMemberOnce(memberId: Long): List<MemberPackageEntity>

    @Query("SELECT * FROM member_packages WHERE member_id = :memberId AND status = 'HISTORY' ORDER BY updated_at_ms DESC")
    fun getHistoryForMember(memberId: Long): Flow<List<MemberPackageEntity>>

    @Query("SELECT * FROM member_packages WHERE id = :id")
    suspend fun getById(id: Long): MemberPackageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MemberPackageEntity): Long

    @Update
    suspend fun update(entity: MemberPackageEntity)

    @Delete
    suspend fun delete(entity: MemberPackageEntity)

    /**
     * Seans düşür — remaining_sessions > 0 ise 1 düşürür, 0'a indiğinde status=HISTORY.
     * -1 (sınırsız) ise dokunmaz.
     * Dönüş: güncellenen paketin yeni remaining değeri, -999 ise dokunulmadı.
     */
    @Query("""
        UPDATE member_packages
        SET remaining_sessions = CASE
                WHEN remaining_sessions > 0 THEN remaining_sessions - 1
                ELSE remaining_sessions
            END,
            status = CASE
                WHEN remaining_sessions = 1 THEN 'HISTORY'
                ELSE status
            END,
            updated_at_ms = :nowMs
        WHERE id = :packageId AND remaining_sessions != -1
    """)
    suspend fun decrementSession(packageId: Long, nowMs: Long = System.currentTimeMillis()): Int

    /** Süresi geçmiş aktif paketleri toplu HISTORY'e al. */
    @Query("""
        UPDATE member_packages
        SET status = 'HISTORY', updated_at_ms = :nowMs
        WHERE status = 'ACTIVE' AND end_date_ms < :nowMs
    """)
    suspend fun expireOverduePackages(nowMs: Long = System.currentTimeMillis()): Int
}
