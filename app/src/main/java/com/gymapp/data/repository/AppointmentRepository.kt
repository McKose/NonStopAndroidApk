package com.gymapp.data.repository

import androidx.room.withTransaction
import com.gymapp.data.local.dao.AppointmentDao
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.AppointmentStatus
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.Money
import com.gymapp.domain.Rate
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Randevu akışı ve finansal yan etkileri.
 *
 * Bu mantık daha önce `AppointmentDao` içinde, DAO'ları parametre olarak alan bir
 * `@Transaction` metodundaydı. Defter erişimi repository katmanında olduğu için
 * orkestrasyon buraya taşındı; DAO artık yalnızca sorgu içeriyor.
 */
@Singleton
class AppointmentRepository @Inject constructor(
    private val database: GymDatabase,
    private val appointmentDao: AppointmentDao,
    private val memberDao: MemberDao,
    private val staffDao: StaffDao,
    private val ledgerRepository: LedgerRepository,
) {
    fun observeAll(): Flow<List<AppointmentEntity>> = appointmentDao.getAllAppointments()

    suspend fun getById(id: Long): AppointmentEntity? = appointmentDao.getAppointmentById(id)

    /** Aynı eğitmenin o saat aralığında başka randevusu var mı? */
    suspend fun hasOverlap(staffId: Long, startTimeMs: Long, endTimeMs: Long): Boolean =
        appointmentDao.countOverlapping(staffId, startTimeMs, endTimeMs) > 0

    suspend fun insert(appointment: AppointmentEntity) =
        appointmentDao.insertAppointment(appointment)

    /**
     * Randevu durumunu değiştirir ve finansal yan etkileri **idempotent** yönetir.
     *
     * `isProcessed` bayrağı "durum kilitlendi" değil, **"finansal etki uygulandı"**
     * anlamına gelir:
     *  - `COMPLETED` → seans düşülür + personel hakedişi defterde gider olarak yazılır
     *  - `COMPLETED` sonrası başka duruma dönülürse → seans iade edilir ve hakediş
     *    **ters kayıtla** iptal edilir
     *  - Diğer geçişler finansal etki doğurmaz
     *
     * Ters kayıt, defterdeki **gerçek kayıtları** iptal ettiği için tutar birebir
     * doğru olur; üye paketini arada değiştirmiş olsa bile geri alma sapmaz.
     *
     * Tümü tek transaction içinde; hata durumunda kısmi yazma kalmaz.
     */
    suspend fun processStatus(
        appointmentId: Long,
        status: String,
        notes: String?,
    ): Result<Unit> = runCatching {
        database.withTransaction {
            val appointment = appointmentDao.getAppointmentById(appointmentId)
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
                if (commission.isPositive) {
                    ledgerRepository.recordExpense(
                        amount = commission,
                        category = LedgerCategory.COMMISSION,
                        description = "${staff.fullName} — ${member.fullName} hakediş",
                        staffId = staff.id.toString(),
                        appointmentId = appointmentId.toString(),
                        occurredAtMs = nowMs,
                    ).getOrThrow()
                }
            } else if (!shouldSettle && appointment.isProcessed) {
                // Tamamlanmış randevu geri alınıyor: yan etkileri geri sar.
                memberDao.incrementSession(appointment.memberId, nowMs)

                ledgerRepository.reverseForAppointment(
                    appointmentId = appointmentId.toString(),
                    reason = "Randevu geri alındı",
                    occurredAtMs = nowMs,
                ).getOrThrow()
            }

            appointmentDao.updateAppointment(
                appointment.copy(status = status, notes = notes, isProcessed = shouldSettle)
            )
        }
    }
}

/**
 * Bir seansın personele düşen hakedişi: `(paket ücreti / toplam seans) * hakediş oranı`.
 *
 * Hesap `Money` üzerinden yapılır; tek yuvarlama noktasından geçtiği için
 * kuruş sapması oluşmaz.
 *
 * NOT (entity geçişi): matrah randevu anında `AppointmentEntity`'ye snapshot'lanmalı.
 * Şu anki kurguda üye paketini yenilerse **yeni** randevuların matrahı değişir;
 * geçmiş kayıtlar defterde durduğu için geriye dönük bozulma yaşanmaz.
 */
private fun sessionCommission(member: MemberEntity, staff: StaffEntity): Money {
    if (member.totalSessions <= 0) return Money.ZERO
    val sessionValue = Money.ofMajor(member.packagePrice) / member.totalSessions
    val rate = Rate((staff.commissionRate.coerceIn(0.0, 1.0) * Rate.SCALE).roundToInt())
    return sessionValue.applyRate(rate)
}
