package com.gymapp.domain.appointment

import androidx.room.withTransaction
import com.gymapp.data.local.dao.AppointmentDao
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.dao.MemberPackageDao
import com.gymapp.data.local.dao.MultiSportRateDao
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.dao.TransactionDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.MemberPackageEntity
import com.gymapp.data.local.entity.TransactionCategory
import com.gymapp.data.local.entity.TransactionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Appointment durum geçişi (özellikle COMPLETED) için orchestration.
 *
 * COMPLETED durumunda:
 *   1) Üyenin aktif paketlerinden en erken biten ACTIVE paket seçilir.
 *   2) Paketin remaining_sessions > 0 ise 1 düşülür; 1 → 0 olduğunda paket HISTORY'e.
 *   3) Hakediş = (package_price / total_sessions) × staff.commissionRate
 *      (total_sessions <= 0 ise fallback: staff.hourlyRate × commissionRate)
 *      Transaction: EXPENSE / TRAINER_COMMISSION
 *   4) Paketin paymentType == MULTISPORT ise ek olarak o günkü MSRate ile INCOME
 *      Transaction: INCOME / MULTISPORT_SESSION. Rate, appointment start_time_ms'e
 *      göre multisport_rates'ten çekilir ("o tarihte geçerli olan").
 */
@Singleton
class CompleteAppointmentUseCase @Inject constructor(
    private val db: GymDatabase,
    private val appointmentDao: AppointmentDao,
    private val memberDao: MemberDao,
    private val staffDao: StaffDao,
    private val transactionDao: TransactionDao,
    private val memberPackageDao: MemberPackageDao,
    private val multiSportRateDao: MultiSportRateDao
) {
    suspend operator fun invoke(
        appointmentId: Long,
        newStatus: String,
        notes: String?
    ): Result<Unit> = runCatching {
        db.withTransaction {
            val appointment = appointmentDao.getAppointmentById(appointmentId)
                ?: throw IllegalStateException("Randevu bulunamadı")
            if (appointment.isProcessed) return@withTransaction

            if (newStatus == "COMPLETED") {
                val member = memberDao.getMemberById(appointment.memberId)
                    ?: throw IllegalStateException("Üye bulunamadı")
                val staff = staffDao.getStaffById(appointment.staffId)
                    ?: throw IllegalStateException("Personel bulunamadı")

                // Aktif paketlerden en erken biten
                val actives: List<MemberPackageEntity> =
                    memberPackageDao.getActiveForMemberOnce(member.id)
                val targetPackage = actives.minByOrNull { it.endDateMs }

                // Seans düş
                if (targetPackage != null && targetPackage.totalSessions > 0) {
                    memberPackageDao.decrementSession(targetPackage.id)
                    // Legacy MemberEntity sayacı da güncelle (dashboard için)
                    if (member.totalSessions > 0) {
                        memberDao.decrementSession(member.id)
                    }
                }

                // Hakediş hesabı — paket bazlı
                val perSessionRate: Double = when {
                    targetPackage != null && targetPackage.totalSessions > 0 &&
                        targetPackage.packagePrice > 0 ->
                        targetPackage.packagePrice / targetPackage.totalSessions
                    staff.hourlyRate > 0 -> staff.hourlyRate
                    else -> 0.0
                }
                val commission = perSessionRate * staff.commissionRate

                if (commission > 0) {
                    transactionDao.insertTransaction(
                        TransactionEntity(
                            memberId = member.id,
                            staffId = staff.id,
                            memberPackageId = targetPackage?.id,
                            amount = commission,
                            type = "EXPENSE",
                            category = TransactionCategory.TRAINER_COMMISSION,
                            description = "${staff.fullName} - ${member.fullName} Hakediş",
                            date = appointment.startTimeMs,
                            isPending = false,
                            paymentMethod = "CASH"
                        )
                    )
                }

                // MultiSport gelir kaydı — paket türüne göre değil, paymentType'a göre
                if (targetPackage?.paymentType == "MULTISPORT") {
                    val msRate = multiSportRateDao.getRateAt(appointment.startTimeMs)
                    val msAmount = msRate?.amount ?: 0.0
                    if (msAmount > 0) {
                        transactionDao.insertTransaction(
                            TransactionEntity(
                                memberId = member.id,
                                staffId = staff.id,
                                memberPackageId = targetPackage.id,
                                amount = msAmount,
                                type = "INCOME",
                                category = TransactionCategory.MULTISPORT_SESSION,
                                description = "${member.fullName} - MultiSport seans (${targetPackage.packageNameSnapshot})",
                                date = appointment.startTimeMs,
                                paymentMethod = "MULTISPORT"
                            )
                        )
                    }
                }
            }

            appointmentDao.updateAppointment(
                appointment.copy(status = newStatus, notes = notes, isProcessed = true)
            )
        }
    }
}
