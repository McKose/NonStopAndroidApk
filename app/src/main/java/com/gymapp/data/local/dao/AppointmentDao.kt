package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Randevu sorguları.
 *
 * Durum değişikliği ve finansal yan etkiler bilinçli olarak burada değil,
 * [com.gymapp.data.repository.AppointmentRepository] içinde yönetilir: defter
 * erişimi repository katmanında olduğu için orkestrasyonun DAO'da durması
 * yukarı doğru bir bağımlılık gerektiriyordu.
 */
@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY start_time_ms ASC")
    fun getAllAppointments(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE start_time_ms >= :start AND start_time_ms < :end ORDER BY start_time_ms ASC")
    fun getAppointmentsBetween(start: Long, end: Long): Flow<List<AppointmentEntity>>

    /** Aynı eğitmenin aynı saat aralığında başka randevusu var mı? (çakışma kontrolü) */
    @Query("""
        SELECT COUNT(*) FROM appointments
        WHERE staff_id = :staffId
          AND status != 'CANCELLED'
          AND id != :excludeAppointmentId
          AND start_time_ms < :endTimeMs
          AND end_time_ms > :startTimeMs
    """)
    suspend fun countOverlapping(
        staffId: Long,
        startTimeMs: Long,
        endTimeMs: Long,
        excludeAppointmentId: Long = 0L,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity)

    @Update
    suspend fun updateAppointment(appointment: AppointmentEntity)

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointmentById(id: Long): AppointmentEntity?
}
