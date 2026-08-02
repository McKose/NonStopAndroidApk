package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    // ─── CREATE ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMember(member: MemberEntity): Long

    // ─── READ ──────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM gym_members ORDER BY createdAtMs DESC")
    fun getAllMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM gym_members WHERE id = :id")
    suspend fun getMemberById(id: Long): MemberEntity?

    @Query("SELECT * FROM gym_members WHERE id = :id")
    fun getMemberByIdFlow(id: Long): Flow<MemberEntity?>

    @Query("SELECT * FROM gym_members WHERE phone = :phone LIMIT 1")
    suspend fun getMemberByPhone(phone: String): MemberEntity?

    @Query("SELECT * FROM gym_members WHERE status = 'ACTIVE' ORDER BY fullName ASC")
    fun getActiveMembers(): Flow<List<MemberEntity>>

    /** Arama — ad, soyad veya telefon */
    @Query("""
        SELECT * FROM gym_members
        WHERE fullName LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
        ORDER BY fullName ASC
    """)
    fun searchMembers(query: String): Flow<List<MemberEntity>>

    // ─── UPDATE ────────────────────────────────────────────────────────────────

    @Update
    suspend fun updateMember(member: MemberEntity)

    /**
     * DÜZELTME #1 — Seans düşme güvenli yapıldı.
     * remainingSessions = -1 ise (ABONMAN) hiçbir şey yapmıyor.
     */
    @Query("""
        UPDATE gym_members
        SET remainingSessions = CASE
            WHEN remainingSessions > 0 THEN remainingSessions - 1
            ELSE remainingSessions
        END,
        updatedAtMs = :nowMs
        WHERE id = :memberId AND remainingSessions != -1
    """)
    suspend fun decrementSession(memberId: Long, nowMs: Long = System.currentTimeMillis())

    /**
     * Tamamlanmış bir randevu geri alındığında seans hakkını iade eder.
     *
     * Abonman (-1) etkilenmez ve iade `totalSessions` tavanını aşamaz.
     */
    @Query("""
        UPDATE gym_members
        SET remainingSessions = CASE
            WHEN totalSessions < 0 OR remainingSessions < totalSessions
                THEN remainingSessions + 1
            ELSE remainingSessions
        END,
        updatedAtMs = :nowMs
        WHERE id = :memberId AND remainingSessions != -1
    """)
    suspend fun incrementSession(memberId: Long, nowMs: Long = System.currentTimeMillis())

    /**
     * DÜZELTME #3 — WorkManager tarafından çağrılır.
     * endDate geçmiş tüm ACTIVE üyeleri toplu PASSIVE yapar.
     */
    @Query("""
        UPDATE gym_members
        SET status = 'PASSIVE', updatedAtMs = :nowMs
        WHERE status = 'ACTIVE'
          AND endDateMs IS NOT NULL
          AND endDateMs < :nowMs
    """)
    suspend fun expireOverdueMembers(nowMs: Long = System.currentTimeMillis()): Int

    // ─── DELETE ────────────────────────────────────────────────────────────────

    /** Soft delete: status = PASSIVE yapıyoruz, kaydı silmiyoruz. */
    @Query("UPDATE gym_members SET status = 'PASSIVE', updatedAtMs = :nowMs WHERE id = :id")
    suspend fun softDeleteMember(id: Long, nowMs: Long = System.currentTimeMillis())
}
