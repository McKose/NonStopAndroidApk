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

    @Query("SELECT * FROM gym_members ORDER BY created_at_ms DESC")
    fun getAllMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM gym_members WHERE id = :id")
    suspend fun getMemberById(id: Long): MemberEntity?

    @Query("SELECT * FROM gym_members WHERE phone = :phone LIMIT 1")
    suspend fun getMemberByPhone(phone: String): MemberEntity?

    @Query("SELECT * FROM gym_members WHERE status = 'ACTIVE' ORDER BY full_name ASC")
    fun getActiveMembers(): Flow<List<MemberEntity>>

    /** Arama — ad, soyad veya telefon */
    @Query("""
        SELECT * FROM gym_members
        WHERE full_name LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
        ORDER BY full_name ASC
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
        SET remaining_sessions = CASE
            WHEN remaining_sessions > 0 THEN remaining_sessions - 1
            ELSE remaining_sessions
        END,
        updated_at_ms = :nowMs
        WHERE id = :memberId AND remaining_sessions != -1
    """)
    suspend fun decrementSession(memberId: Long, nowMs: Long = System.currentTimeMillis())

    /**
     * DÜZELTME #3 — WorkManager tarafından çağrılır.
     * endDate geçmiş tüm ACTIVE üyeleri toplu PASSIVE yapar.
     */
    @Query("""
        UPDATE gym_members
        SET status = 'PASSIVE', updated_at_ms = :nowMs
        WHERE status = 'ACTIVE'
          AND end_date_ms IS NOT NULL
          AND end_date_ms < :nowMs
    """)
    suspend fun expireOverdueMembers(nowMs: Long = System.currentTimeMillis()): Int

    // ─── DELETE ────────────────────────────────────────────────────────────────

    /** Soft delete: status = PASSIVE yapıyoruz, kaydı silmiyoruz. */
    @Query("UPDATE gym_members SET status = 'PASSIVE', updated_at_ms = :nowMs WHERE id = :id")
    suspend fun softDeleteMember(id: Long, nowMs: Long = System.currentTimeMillis())
}
