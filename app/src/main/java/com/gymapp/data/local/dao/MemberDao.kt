package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * Üye sorguları.
 *
 * Tüm okumalar `tenantId`'ye filtrelenir ve tombstone kayıtları (`deletedAtMs`)
 * dışarıda bırakır; silme fiziksel değil işaretlemedir, böylece silme de
 * senkronize olabilir.
 */
@Dao
interface MemberDao {

    // ─── CREATE ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMember(member: MemberEntity)

    // ─── READ ──────────────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM gym_members
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
        ORDER BY createdAtMs DESC
    """)
    fun getAllMembers(tenantId: String): Flow<List<MemberEntity>>

    /**
     * Kimliğe göre tekil okuma — tombstone **filtrelenmez**.
     *
     * Bu çağrı bir listeleme değil, var olan bir yabancı anahtarın çözümlenmesidir
     * (randevuya bağlı üye gibi). Filtrelenseydi, üyesi silinmiş bir randevuyu
     * tamamlamak "üye bulunamadı" ile başarısız olurdu.
     */
    @Query("SELECT * FROM gym_members WHERE id = :id")
    suspend fun getMemberById(id: String): MemberEntity?

    /** Ekran akışı — silinen üyenin detay ekranı boşalsın diye tombstone filtrelenir. */
    @Query("SELECT * FROM gym_members WHERE id = :id AND deletedAtMs IS NULL")
    fun getMemberByIdFlow(id: String): Flow<MemberEntity?>

    /**
     * Telefona göre arama — silinmiş kayıtlar **dahil**.
     *
     * UNIQUE index tombstone satırlarını da kapsadığından, silinmiş bir üyenin
     * numarası filtrelenseydi yeniden kayıt `INSERT` aşamasında anlaşılmaz bir
     * kısıt hatasıyla düşerdi. Kayıt bulunduğunda üye *canlandırılır*, böylece
     * geri dönen üyenin defter geçmişi de korunur.
     */
    @Query("""
        SELECT * FROM gym_members
        WHERE tenantId = :tenantId AND phone = :phone
        LIMIT 1
    """)
    suspend fun getMemberByPhone(tenantId: String, phone: String): MemberEntity?

    @Query("""
        SELECT * FROM gym_members
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL AND status = 'ACTIVE'
        ORDER BY fullName ASC
    """)
    fun getActiveMembers(tenantId: String): Flow<List<MemberEntity>>

    /** Arama — ad soyad veya telefon. */
    @Query("""
        SELECT * FROM gym_members
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
          AND (fullName LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY fullName ASC
    """)
    fun searchMembers(tenantId: String, query: String): Flow<List<MemberEntity>>

    // ─── UPDATE ────────────────────────────────────────────────────────────────

    @Update
    suspend fun updateMember(member: MemberEntity)

    /**
     * Seans düşümü.
     *
     * `remainingSessions IS NULL` sınırsız paketi (abonman) ifade eder ve
     * etkilenmez; sayaç sıfırın altına inmez.
     */
    @Query("""
        UPDATE gym_members
        SET remainingSessions = CASE
                WHEN remainingSessions > 0 THEN remainingSessions - 1
                ELSE remainingSessions
            END,
            updatedAtMs = :nowMs
        WHERE id = :memberId AND remainingSessions IS NOT NULL
    """)
    suspend fun decrementSession(memberId: String, nowMs: Long)

    /**
     * Tamamlanmış bir randevu geri alındığında seans hakkını iade eder.
     *
     * Sınırsız paket etkilenmez ve iade `totalSessions` tavanını aşamaz.
     */
    @Query("""
        UPDATE gym_members
        SET remainingSessions = CASE
                WHEN totalSessions IS NULL OR remainingSessions < totalSessions
                    THEN remainingSessions + 1
                ELSE remainingSessions
            END,
            updatedAtMs = :nowMs
        WHERE id = :memberId AND remainingSessions IS NOT NULL
    """)
    suspend fun incrementSession(memberId: String, nowMs: Long)

    // ─── DELETE ────────────────────────────────────────────────────────────────

    /** Tombstone silme; kayıt fiziksel olarak kalır ki silme senkronize olabilsin. */
    @Query("UPDATE gym_members SET deletedAtMs = :nowMs, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun softDeleteMember(id: String, nowMs: Long)
}
