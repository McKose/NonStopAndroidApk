package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.TransactionEntity
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

            // Yalnızca seans sayısı sınırlı üyeler için seans düş
            if (member.totalSessions > 0) {
                memberDao.decrementSession(appointment.memberId)
            }

            // Hakediş hesaplaması:
            //  - Sınırlı seans paketinde: (Paket Ücreti / Toplam Seans) * Hakediş Oranı
            //  - Sınırsız (ABONMAN) paketinde: Personel saatlik ücreti üzerinden hakediş
            val perSessionRate: Double = when {
                member.totalSessions > 0 && member.packagePrice > 0 ->
                    member.packagePrice / member.totalSessions
                staff.hourlyRate > 0 ->
                    staff.hourlyRate
                else -> 0.0
            }
            val commission = perSessionRate * staff.commissionRate

            if (commission > 0) {
                transactionDao.insertTransaction(
                    TransactionEntity(
                        memberId = member.id,
                        amount = commission,
                        type = "EXPENSE",
                        category = "SALARY",
                        description = "${staff.fullName} - ${member.fullName} Hakediş",
                        date = System.currentTimeMillis(),
                        isPending = false,
                        paymentMethod = "CASH"
                    )
                )
            }
        }

        val updatedAppointment = appointment.copy(
            status = status,
            notes = notes,
            isProcessed = true
        )
        updateAppointment(updatedAppointment)
    }
}
