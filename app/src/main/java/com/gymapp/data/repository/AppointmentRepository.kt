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
import com.gymapp.domain.Membership
import com.gymapp.domain.Money
import com.gymapp.domain.Rate
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

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

    /**
     * Randevu oluşturur ve hakediş matrahını **o anda dondurur**.
     *
     * Matrah önceden tamamlama anında üyenin güncel paketinden hesaplanıyordu;
     * üye arada paketini yenilerse aynı ders için farklı hakediş çıkıyordu.
     *
     * Uygunluk kuralları (üyelik geçerli mi, seans hakkı var mı, eğitmen o saatte
     * boş mu) bilinçli olarak burada: ekranda yapılan kontrol ile yazma arasında
     * yarış vardı ve kontrol yalnızca tek bir çağrı yerini koruyordu. Kontrol ve
     * kayıt aynı transaction içinde olduğundan iki eşzamanlı rezervasyon aynı
     * saati alamaz.
     */
    suspend fun create(
        memberId: String,
        staffId: String,
        trainingType: TrainingType,
        startTimeMs: Long,
        endTimeMs: Long,
    ): Result<String> = runCatching {
        require(endTimeMs > startTimeMs) { "Randevu bitişi başlangıcından sonra olmalıdır." }

        database.withTransaction {
            val nowMs = System.currentTimeMillis()

            val member = memberDao.getMemberById(memberId)
                ?: throw IllegalArgumentException("Üye bulunamadı.")

            if (staffDao.getStaffById(staffId) == null) {
                throw IllegalArgumentException("Eğitmen bulunamadı.")
            }

            if (!Membership.canBookSession(
                    storedStatus = member.status,
                    endDateMs = member.endDateMs,
                    remainingSessions = member.remainingSessions,
                    nowMs = nowMs,
                )
            ) {
                throw IllegalStateException(
                    "${member.fullName}: üyelik aktif değil ya da seans hakkı kalmadı."
                )
            }

            if (appointmentDao.countOverlapping(tenantId, staffId, startTimeMs, endTimeMs) > 0) {
                throw IllegalStateException("Seçilen eğitmenin bu saatte başka randevusu var.")
            }

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
                val commission = basis.applyRate(Rate(staff.commissionBasisPoints))

                if (commission.isPositive) {
                    ledgerRepository.recordExpense(
                        amount = commission,
                        category = LedgerCategory.COMMISSION,
                        description = "${staff.fullName} — ${member.fullName} hakediş",
                        staffId = staff.id,
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

/**
 * Bir seansın parasal değeri: paket ücreti / toplam seans.
 *
 * Sınırsız (abonman) pakette seans başına değer tanımsızdır; hakediş matrahı sıfırdır.
 */
private fun sessionValue(member: MemberEntity): Money {
    val total = member.totalSessions ?: return Money.ZERO
    if (total <= 0) return Money.ZERO
    return Money(member.packagePriceMinor) / total
}
