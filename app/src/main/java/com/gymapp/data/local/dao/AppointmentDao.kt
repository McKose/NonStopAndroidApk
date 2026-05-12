package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Randevu kayıt ve sorgulama DAO'su.
 *
 * Randevu durum geçişleri (özellikle COMPLETED için seans düşümü, hakediş kaydı,
 * MultiSport gelir yazımı) [com.gymapp.domain.appointment.CompleteAppointmentUseCase]
 * tarafından orkestre edilir. Burada iş kuralı tutulmaz.
 */
@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY start_time_ms ASC")
    fun getAllAppointments(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE start_time_ms >= :start AND start_time_ms <= :end ORDER BY start_time_ms ASC")
    fun getAppointmentsBetween(start: Long, end: Long): Flow<List<AppointmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity)

    @Update
    suspend fun updateAppointment(appointment: AppointmentEntity)

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getAppointmentById(id: Long): AppointmentEntity?
}
