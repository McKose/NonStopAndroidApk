package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.local.entity.TransactionEntity
import com.gymapp.domain.AppointmentStatus
import com.gymapp.domain.CommissionRate
import kotlinx.coroutines.flow.Flow

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

    /**
     * Randevu durumunu değiştirir ve finansal yan etkileri **idempotent** biçimde yönetir.
     *
     * `isProcessed` bayrağı "durum kilitlendi" değil, **"finansal etki uygulandı"** anlamına gelir:
     *  - `COMPLETED` → seans düşülür + personel hakedişi gider olarak yazılır
     *  - `COMPLETED` sonrası başka bir duruma dönülürse → seans iade edilir + hakediş **ters kayıtla** iptal edilir
     *  - Diğer durumlar arası geçişler finansal etki doğurmaz
     *
     * Böylece "Ertelendi" işaretlenen bir randevu sonradan "Tamamlandı" yapılabilir;
     * önceki sürümde bu geçiş sessizce hiçbir şey yapmıyordu.
     *
     * Hata durumunda fırlatır; `@Transaction` sayesinde kısmi yazma kalmaz.
     */
    @Transaction
    suspend fun processAppointmentStatus(
        appointmentId: Long,
        status: String,
        notes: String?,
        memberDao: MemberDao,
        transactionDao: TransactionDao,
        staffDao: StaffDao
    ) {
        val appointment = getAppointmentById(appointmentId)
            ?: throw IllegalArgumentException("Randevu bulunamadı.")

        val shouldSettle = AppointmentStatus.hasFinancialEffect(status)
        val nowMs = System.currentTimeMillis()

        if (shouldSettle && !appointment.isProcessed) {
            val member = memberDao.getMemberById(appointment.memberId)
                ?: throw IllegalStateException("Randevuya bağlı üye bulunamadı.")
            val staff = staffDao.getStaffById(appointment.staffId)
                ?: throw IllegalStateException("Randevuya bağlı eğitmen bulunamadı.")

            memberDao.decrementSession(appointment.memberId, nowMs)

            val commission = sessionCommission(member, staff)
            if (commission > 0.0) {
                transactionDao.insertTransaction(
                    TransactionEntity(
                        memberId = member.id,
                        amount = commission,
                        type = "EXPENSE",
                        category = "SALARY",
                        description = "${staff.fullName} — ${member.fullName} hakediş (Randevu #$appointmentId)",
                        date = nowMs,
                        isPending = false,
                        paymentMethod = "CASH",
                    )
                )
            }
        } else if (!shouldSettle && appointment.isProcessed) {
            // Tamamlanmış randevu geri alınıyor: yan etkileri geri sar.
            memberDao.incrementSession(appointment.memberId, nowMs)

            val member = memberDao.getMemberById(appointment.memberId)
            val staff = staffDao.getStaffById(appointment.staffId)
            if (member != null && staff != null) {
                val commission = sessionCommission(member, staff)
                if (commission > 0.0) {
                    // Kayıt silinmez; gideri sıfırlayan ters kayıt eklenir (denetim izi korunur).
                    transactionDao.insertTransaction(
                        TransactionEntity(
                            memberId = member.id,
                            amount = commission,
                            type = "INCOME",
                            category = "SALARY",
                            description = "İPTAL — ${staff.fullName} hakediş geri alındı (Randevu #$appointmentId)",
                            date = nowMs,
                            isPending = false,
                            paymentMethod = "CASH",
                        )
                    )
                }
            }
        }

        updateAppointment(
            appointment.copy(status = status, notes = notes, isProcessed = shouldSettle)
        )
    }
}

/**
 * Bir seansın personele düşen hakedişi: `(paket ücreti / toplam seans) * hakediş oranı`.
 *
 * NOT (Faz 1): matrah randevu anında `AppointmentEntity`'ye snapshot'lanmalı. Şu anki
 * kurguda üye paketini yenilerse geçmiş randevuların hakedişi geriye dönük değişir.
 */
private fun sessionCommission(member: MemberEntity, staff: StaffEntity): Double {
    if (member.totalSessions <= 0) return 0.0
    val sessionValue = member.packagePrice / member.totalSessions
    return CommissionRate.commissionFor(sessionValue, staff.commissionRate)
}
