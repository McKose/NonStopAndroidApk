package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.AppointmentEntity
import kotlinx.coroutines.flow.Flow

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

    @Transaction
    suspend fun processAppointmentStatus(
        appointmentId: Long,
        status: String,
        notes: String?,
        memberDao: MemberDao,
        transactionDao: TransactionDao,
        staffDao: StaffDao
    ) {
        val appointment = getAppointmentById(appointmentId) ?: return
        if (appointment.isProcessed) return

        if (status == "COMPLETED") {
            val member = memberDao.getMemberById(appointment.memberId) ?: return
            val staff = staffDao.getStaffById(appointment.staffId) ?: return

            // 1. Üye dersinden düş — sadece COMPLETED'da
            memberDao.decrementSession(appointment.memberId)

            // 2. Personel hakediş hesapla — sadece COMPLETED'da
            // Formül: (Paket Ücreti / Toplam Seans) * Personel Hakediş Oranı
            if (member.totalSessions > 0) {
                val hourlyRateFromPackage = member.packagePrice / member.totalSessions
                val commission = hourlyRateFromPackage * staff.commissionRate

                if (commission > 0) {
                    transactionDao.insertTransaction(
                        com.gymapp.data.local.entity.TransactionEntity(
                            amount = commission,
                            type = "EXPENSE",
                            category = "SALARY",
                            description = "${staff.fullName} - ${member.fullName} Hakediş",
                            date = System.currentTimeMillis(),
                            isPending = false, // Finans ekranında anında gider olarak görünmesi için
                            paymentMethod = "CASH" // Varsayılan ödeme tipi
                        )
                    )
                }
            }
        }

        // Tek seferde tüm alanları güncelle — isProcessed dahil
        val updatedAppointment = appointment.copy(status = status, notes = notes, isProcessed = true)
        updateAppointment(updatedAppointment)
    }
}
