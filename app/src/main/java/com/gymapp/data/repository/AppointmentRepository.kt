package com.gymapp.data.repository

import androidx.room.withTransaction
import com.gymapp.data.local.dao.AppointmentDao
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.Ids
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.Money
import com.gymapp.domain.Rate
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Randevu akışı ve finansal yan etkileri.
 *
 * Orkestrasyon bilinçli olarak DAO'da değil burada: defter erişimi repository
 * katmanında olduğu için DAO'da durması yukarı doğru bağımlılık gerektiriyordu.
 */
@Singleton
class AppointmentRepository @Inject constructor(
    private val database: GymDatabase,
    private val appointmentDao: AppointmentDao,
    private val memberDao: MemberDao,
    private val staffDao: StaffDao,
    private val ledgerRepository: LedgerRepository,
) {
    private val tenantId = Ids.DEFAULT_TENANT

    fun observeAll(): Flow<List<AppointmentEntity>> = appointmentDao.observeAll(tenantId)

    suspend fun getById(id: String): AppointmentEntity? = appointmentDao.getById(id)

    /** Aynı eğitmenin o saat aralığında başka randevusu var mı? */
    suspend fun hasOverlap(staffId: Long, startTimeMs: Long, endTimeMs: Long): Boolean =
        appointmentDao.countOverlapping(tenantId, staffId, startTimeMs, endTimeMs) > 0

    /**
     * Randevu oluşturur ve hakediş matrahını **o anda dondurur**.
     *
     * Matrah önceden tamamlama anında üyenin güncel paketinden hesaplanıyordu;
     * üye arada paketini yenilerse aynı ders için farklı hakediş çıkıyordu.
     */
    suspend fun create(
        memberId: Long,
        staffId: Long,
        trainingType: TrainingType,
        startTimeMs: Long,
        endTimeMs: Long,
    ): Result<String> = runCatching {
        val member = memberDao.getMemberById(memberId)
            ?: throw IllegalArgumentException("Üye bulunamadı.")

        val nowMs = System.currentTimeMillis()
        val appointmentId = Ids.new()

        appointmentDao.insert(
            AppointmentEntity(
                id = appointmentId,
                tenantId = tenantId,
                memberId = memberId,
                staffId = staffId,
                trainingType = trainingType,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                sessionValueMinor = sessionValue(member).minor,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
        )
        appointmentId
    }

    /**
     * Randevu durumunu değiştirir ve finansal yan etkileri **idempotent** yönetir.
     *
     * [AppointmentEntity.settledAtMs] "durum kilitlendi" değil, **"finansal etki
     * uygulandı"** anlamına gelir:
     *  - `COMPLETED` → seans düşülür + personel hakedişi defterde gider olarak yazılır
     *  - `COMPLETED` sonrası başka duruma dönülürse → seans iade edilir ve hakediş
     *    **ters kayıtla** iptal edilir
     *  - Diğer geçişler finansal etki doğurmaz
     *
     * Ters kayıt defterdeki **gerçek kayıtları** iptal ettiği için tutar birebir
     * doğrudur; üye paketini arada değiştirmiş olsa bile geri alma sapmaz.
     */
    suspend fun processStatus(
        appointmentId: String,
        state: AppointmentState,
        notes: String?,
    ): Result<Unit> = runCatching {
        database.withTransaction {
            val appointment = appointmentDao.getById(appointmentId)
                ?: throw IllegalArgumentException("Randevu bulunamadı.")

            val shouldSettle = state == AppointmentState.COMPLETED
            val isSettled = appointment.settledAtMs != null
            val nowMs = System.currentTimeMillis()

            if (shouldSettle && !isSettled) {
                val member = memberDao.getMemberById(appointment.memberId)
                    ?: throw IllegalStateException("Randevuya bağlı üye bulunamadı.")
                val staff = staffDao.getStaffById(appointment.staffId)
                    ?: throw IllegalStateException("Randevuya bağlı eğitmen bulunamadı.")

                memberDao.decrementSession(appointment.memberId, nowMs)

                // Matrah randevu anında donduruldu; boşsa üyeden hesaplanır (eski kayıtlar).
                val basis = Money(appointment.sessionValueMinor)
                    .takeIf { it.isPositive } ?: sessionValue(member)
                val commission = basis.applyRate(commissionRate(staff.commissionRate))

                if (commission.isPositive) {
                    ledgerRepository.recordExpense(
                        amount = commission,
                        category = LedgerCategory.COMMISSION,
                        description = "${staff.fullName} — ${member.fullName} hakediş",
                        staffId = staff.id.toString(),
                        appointmentId = appointmentId,
                        occurredAtMs = nowMs,
                    ).getOrThrow()
                }
            } else if (!shouldSettle && isSettled) {
                memberDao.incrementSession(appointment.memberId, nowMs)
                ledgerRepository.reverseForAppointment(
                    appointmentId = appointmentId,
                    reason = "Randevu geri alındı",
                    occurredAtMs = nowMs,
                ).getOrThrow()
            }

            appointmentDao.update(
                appointment.copy(
                    state = state,
                    notes = notes,
                    settledAtMs = if (shouldSettle) (appointment.settledAtMs ?: nowMs) else null,
                    updatedAtMs = nowMs,
                )
            )
        }
    }
}

/** Bir seansın parasal değeri: paket ücreti / toplam seans. */
private fun sessionValue(member: MemberEntity): Money {
    if (member.totalSessions <= 0) return Money.ZERO
    return Money.ofMajor(member.packagePrice) / member.totalSessions
}

/** Personelin kesir cinsinden oranını baz puana çevirir. */
private fun commissionRate(fraction: Double): Rate =
    Rate((fraction.coerceIn(0.0, 1.0) * Rate.SCALE).roundToInt())
