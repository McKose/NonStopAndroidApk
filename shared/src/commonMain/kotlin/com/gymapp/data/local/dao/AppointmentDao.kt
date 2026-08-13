package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import com.gymapp.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Randevu sorguları.
 *
 * Durum değişikliği ve finansal yan etkiler [com.gymapp.data.repository.AppointmentRepository]
 * içinde yönetilir; defter erişimi repository katmanında olduğu için orkestrasyonun
 * burada durması yukarı doğru bir bağımlılık gerektiriyordu.
 */
@Dao
interface AppointmentDao {

    @Query("""
        SELECT * FROM appointments
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
        ORDER BY startTimeMs ASC
    """)
    fun observeAll(tenantId: String): Flow<List<AppointmentEntity>>

    @Query("""
        SELECT * FROM appointments
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
          AND startTimeMs >= :startMs AND startTimeMs < :endMs
        ORDER BY startTimeMs ASC
    """)
    fun observeBetween(tenantId: String, startMs: Long, endMs: Long): Flow<List<AppointmentEntity>>

    /** Aynı eğitmenin aynı saat aralığında başka randevusu var mı? (çift rezervasyon) */
    @Query("""
        SELECT COUNT(*) FROM appointments
        WHERE tenantId = :tenantId
          AND staffId = :staffId
          AND deletedAtMs IS NULL
          AND state != 'CANCELLED'
          AND id != :excludeId
          AND startTimeMs < :endTimeMs
          AND endTimeMs > :startTimeMs
    """)
    suspend fun countOverlapping(
        tenantId: String,
        staffId: String,
        startTimeMs: Long,
        endTimeMs: Long,
        excludeId: String = "",
    ): Int

    @Insert
    suspend fun insert(appointment: AppointmentEntity)

    @Update
    suspend fun update(appointment: AppointmentEntity)

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: String): AppointmentEntity?

    /**
     * Sunucudan gelen satırı yazar: yoksa ekler, varsa üzerine yazar.
     *
     * Çekme tarafının tek yazma yolu. `@Insert` ile ayrı bir `@Update` yerine
     * tek çağrı olması bilinçli: hangisinin gerektiğine karar vermek için önce
     * okumak gerekirdi ve o okuma ile yazma arasında satır değişebilirdi.
     *
     * Yerelde gönderim bekleyen satırlar buraya hiç gelmiyor; o ayıklama
     * `PullEngine` içinde yapılıyor (yerel değişiklik sunucudakinden yenidir).
     */
    @Upsert
    suspend fun upsertFromServer(row: AppointmentEntity)
}
