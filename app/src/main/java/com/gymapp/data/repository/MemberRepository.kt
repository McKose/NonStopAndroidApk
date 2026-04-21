package com.gymapp.data.repository

import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.dao.TransactionDao
import com.gymapp.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao,
    private val transactionDao: TransactionDao,
    private val measurementDao: com.gymapp.data.local.dao.MeasurementDao
) {
    private val commissionRates = mapOf(
        1  to 0.0,
        2  to 3.0,
        3  to 5.0,
        6  to 10.0,
        9  to 15.0,
        12 to 20.0
    )

    fun calculateFinalPrice(
        packagePrice: Double,
        discount: Double,
        paymentType: PaymentType,
        installmentCount: Int
    ): Double {
        val baseAfterDiscount = (packagePrice - discount).coerceAtLeast(0.0)
        if (paymentType == PaymentType.CASH || paymentType == PaymentType.MULTISPORT) return baseAfterDiscount
        val rate = commissionRates.getOrDefault(installmentCount, 0.0)
        return baseAfterDiscount * (1.0 + rate / 100.0)
    }

    suspend fun registerMember(
        fullName: String,
        phone: String,
        email: String?,
        selectedPackage: PackageEntity?,
        paymentType: PaymentType,
        installmentCount: Int,
        discount: Double,
        paymentStatus: String,
        paymentDateMs: Long?,
        healthRisks: String?,
        healthNotes: String?,
        notes: String?
    ): Result<Long> = runCatching {
        val existing = memberDao.getMemberByPhone(phone)
        if (existing != null) {
            throw IllegalArgumentException("Bu telefon numarası zaten kayıtlı.")
        }

        val nowMs = System.currentTimeMillis()
        val endDateMs = selectedPackage?.let {
            nowMs + TimeUnit.DAYS.toMillis(it.validityDays.toLong())
        }

        val finalPrice = selectedPackage?.let {
            calculateFinalPrice(it.basePrice, discount, paymentType, installmentCount)
        } ?: 0.0

        val packageSessions = selectedPackage?.sessionCount ?: -1
        val member = MemberEntity(
            fullName = fullName.trim(),
            phone = phone.trim(),
            email = email,
            activePackageId = selectedPackage?.id,
            totalSessions = packageSessions,
            remainingSessions = packageSessions,
            startDateMs = nowMs,
            endDateMs = endDateMs,
            status = MemberStatus.ACTIVE.name,
            paymentType = paymentType.name,
            installmentCount = installmentCount,
            packagePrice = selectedPackage?.basePrice ?: 0.0,
            discount = discount,
            pricePaid = finalPrice,
            paymentStatus = paymentStatus,
            paymentDateMs = paymentDateMs ?: nowMs,
            healthRisks = healthRisks,
            healthNotes = healthNotes,
            notes = notes,
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )

        val memberId = memberDao.insertMember(member)

        // Ödeme yapıldıysa Finans'a işle
        if (paymentStatus == "PAID") {
            transactionDao.insertTransaction(
                TransactionEntity(
                    memberId = memberId,
                    amount = finalPrice,
                    type = "INCOME",
                    category = "MEMBERSHIP",
                    description = "${fullName} - ${selectedPackage?.name ?: "Üyelik"}",
                    paymentMethod = paymentType.name,
                    date = paymentDateMs ?: nowMs,
                    note = notes
                )
            )
        }
        memberId
    }

    suspend fun renewPackage(
        memberId: Long,
        selectedPackage: PackageEntity,
        paymentType: PaymentType,
        installmentCount: Int,
        discount: Double,
        paymentStatus: String,
        paymentDateMs: Long?
    ): Result<Unit> = runCatching {
        val member = memberDao.getMemberById(memberId) ?: throw Exception("Üye bulunamadı")
        val nowMs = System.currentTimeMillis()
        
        // Mevcut bitiş tarihinden mi başlasın yoksa bugünden mi?
        val currentEndDate = member.endDateMs ?: nowMs
        val baseDate = if (currentEndDate > nowMs) currentEndDate else nowMs
        val endDateMs = baseDate + TimeUnit.DAYS.toMillis(selectedPackage.validityDays.toLong())
        
        val finalPrice = calculateFinalPrice(selectedPackage.basePrice, discount, paymentType, installmentCount)

        val packageSessions = selectedPackage.sessionCount
        val updatedMember = member.copy(
            activePackageId = selectedPackage.id,
            totalSessions = packageSessions,
            remainingSessions = packageSessions,
            startDateMs = baseDate,
            endDateMs = endDateMs,
            status = MemberStatus.ACTIVE.name,
            paymentType = paymentType.name,
            installmentCount = installmentCount,
            packagePrice = selectedPackage.basePrice,
            discount = discount,
            pricePaid = finalPrice,
            paymentStatus = paymentStatus,
            paymentDateMs = paymentDateMs ?: nowMs,
            updatedAtMs = nowMs
        )
        
        memberDao.updateMember(updatedMember)

        if (paymentStatus == "PAID") {
            transactionDao.insertTransaction(
                TransactionEntity(
                    memberId = memberId,
                    amount = finalPrice,
                    type = "INCOME",
                    category = "MEMBERSHIP",
                    description = "${member.fullName} - ${selectedPackage.name} (Yenileme)",
                    paymentMethod = paymentType.name,
                    date = paymentDateMs ?: nowMs
                )
            )
        }
    }

    suspend fun updatePaymentStatus(memberId: Long, isPaid: Boolean): Result<Unit> = runCatching {
        val member = memberDao.getMemberById(memberId) ?: throw Exception("Üye bulunamadı")
        if (member.paymentStatus == "PAID") return@runCatching // Zaten ödenmiş

        if (isPaid) {
            val updatedMember = member.copy(
                paymentStatus = "PAID",
                paymentDateMs = System.currentTimeMillis()
            )
            memberDao.updateMember(updatedMember)
            
            transactionDao.insertTransaction(
                TransactionEntity(
                    memberId = memberId,
                    amount = member.pricePaid,
                    type = "INCOME",
                    category = "MEMBERSHIP",
                    description = "${member.fullName} - Paket Ödemesi Alındı",
                    paymentMethod = member.paymentType
                )
            )
        }
    }

    // ─── Sorgu metodları ──────────────────────────────────────────────────────

    fun getAllMembers(): Flow<List<MemberEntity>> = memberDao.getAllMembers()
    fun getActiveMembers(): Flow<List<MemberEntity>> = memberDao.getActiveMembers()
    fun searchMembers(query: String): Flow<List<MemberEntity>> = memberDao.searchMembers(query)

    fun getMemberById(id: Long): Flow<MemberEntity?> = memberDao.getMemberByIdFlow(id)

    suspend fun deleteMember(id: Long) = memberDao.softDeleteMember(id)

    /** DÜZELTME #3 — WorkManager bu metodu çağırır */
    suspend fun expireOverdueMembers(): Int = memberDao.expireOverdueMembers()

    // ─── Ölçümler (Measurements) ──────────────────────────────────────────────

    fun getMeasurementsForMember(memberId: Long): Flow<List<MeasurementEntity>> =
        measurementDao.getMeasurementsForMember(memberId)

    suspend fun addMeasurement(measurement: MeasurementEntity) =
        measurementDao.insertMeasurement(measurement)

    suspend fun deleteMeasurement(measurement: MeasurementEntity) =
        measurementDao.deleteMeasurement(measurement)

    suspend fun updateMemberInfo(member: MemberEntity) =
        memberDao.updateMember(member)
}
