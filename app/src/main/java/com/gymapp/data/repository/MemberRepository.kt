package com.gymapp.data.repository

import com.gymapp.data.local.dao.InstallmentCommissionDao
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.dao.MemberPackageDao
import com.gymapp.data.local.dao.MultiSportRateDao
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
    private val measurementDao: com.gymapp.data.local.dao.MeasurementDao,
    private val memberPackageDao: MemberPackageDao,
    private val installmentDao: InstallmentCommissionDao,
    private val multiSportRateDao: MultiSportRateDao
) {

    // ─── Fiyat hesabı — taksit komisyonu DB'den gelir ───────────────────────────
    //
    // MultiSport: paket fiyatı peşin tahsil edilmez. Her seansta/geldiği günde o
    // günkü MSRate kadar gelir kaydı oluşur. Bu yüzden registerMember
    // MULTISPORT paymentType için membership INCOME yazmaz.

    suspend fun calculateFinalPrice(
        packagePrice: Double,
        discount: Double,
        paymentType: PaymentType,
        installmentCount: Int
    ): Double {
        val baseAfterDiscount = (packagePrice - discount).coerceAtLeast(0.0)
        if (paymentType != PaymentType.CARD) return baseAfterDiscount
        val rate = installmentDao.getRate(installmentCount) ?: 0.0
        return baseAfterDiscount * (1.0 + rate / 100.0)
    }

    /** UI preview için surcharge kısmını ayrı verir. */
    suspend fun calculatePriceBreakdown(
        packagePrice: Double,
        discount: Double,
        paymentType: PaymentType,
        installmentCount: Int
    ): PriceBreakdown {
        val baseAfterDiscount = (packagePrice - discount).coerceAtLeast(0.0)
        val ratePercent = if (paymentType == PaymentType.CARD)
            installmentDao.getRate(installmentCount) ?: 0.0
        else 0.0
        val surcharge = baseAfterDiscount * (ratePercent / 100.0)
        return PriceBreakdown(
            baseAfterDiscount = baseAfterDiscount,
            surcharge = surcharge,
            ratePercent = ratePercent,
            finalPrice = baseAfterDiscount + surcharge
        )
    }

    data class PriceBreakdown(
        val baseAfterDiscount: Double,
        val surcharge: Double,
        val ratePercent: Double,
        val finalPrice: Double
    )

    // ─── Üye kaydı ─────────────────────────────────────────────────────────────

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
        if (existing != null) throw IllegalArgumentException("Bu telefon numarası zaten kayıtlı.")

        val nowMs = System.currentTimeMillis()
        val breakdown = selectedPackage?.let {
            calculatePriceBreakdown(it.basePrice, discount, paymentType, installmentCount)
        } ?: PriceBreakdown(0.0, 0.0, 0.0, 0.0)

        val endDateMs = selectedPackage?.let {
            nowMs + TimeUnit.DAYS.toMillis(it.validityDays.toLong())
        }

        val member = MemberEntity(
            fullName = fullName.trim(),
            phone = phone.trim(),
            email = email,
            activePackageId = selectedPackage?.id,
            totalSessions = selectedPackage?.sessionCount ?: -1,
            remainingSessions = selectedPackage?.sessionCount ?: -1,
            startDateMs = nowMs,
            endDateMs = endDateMs,
            status = MemberStatus.ACTIVE.name,
            paymentType = paymentType.name,
            installmentCount = installmentCount,
            packagePrice = selectedPackage?.basePrice ?: 0.0,
            discount = discount,
            pricePaid = breakdown.finalPrice,
            paymentStatus = paymentStatus,
            paymentDateMs = paymentDateMs ?: nowMs,
            healthRisks = healthRisks,
            healthNotes = healthNotes,
            notes = notes,
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )
        val memberId = memberDao.insertMember(member)

        selectedPackage?.let { pkg ->
            val mpId = memberPackageDao.insert(
                MemberPackageEntity(
                    memberId = memberId,
                    packageId = pkg.id,
                    packageNameSnapshot = pkg.name,
                    packageType = pkg.type,
                    totalSessions = pkg.sessionCount,
                    remainingSessions = pkg.sessionCount,
                    startDateMs = nowMs,
                    endDateMs = nowMs + TimeUnit.DAYS.toMillis(pkg.validityDays.toLong()),
                    packagePrice = pkg.basePrice,
                    discount = discount,
                    installmentSurcharge = breakdown.surcharge,
                    pricePaid = breakdown.finalPrice,
                    paymentType = paymentType.name,
                    installmentCount = installmentCount,
                    paymentStatus = paymentStatus,
                    paymentDateMs = paymentDateMs ?: nowMs,
                    status = "ACTIVE"
                )
            )

            // MULTISPORT ise peşin gelir kaydı YAPILMAZ; gün gün ödeme girişi seans olurken
            // AppointmentDao tarafında yazılır.
            if (paymentStatus == "PAID" && paymentType != PaymentType.MULTISPORT) {
                transactionDao.insertTransaction(
                    TransactionEntity(
                        memberId = memberId,
                        memberPackageId = mpId,
                        amount = breakdown.finalPrice,
                        type = "INCOME",
                        category = TransactionCategory.MEMBERSHIP,
                        description = "${fullName.trim()} - ${pkg.name}",
                        paymentMethod = paymentType.name,
                        installmentCount = installmentCount,
                        installmentSurchargeAmount = breakdown.surcharge,
                        date = paymentDateMs ?: nowMs,
                        note = notes
                    )
                )
            }
        }
        memberId
    }

    // ─── Paket yenileme — iki paket aynı anda aktif, süreler toplanır ───────────

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

        // Aktif paketlerin en uzak bitişi; yoksa bugün
        val actives = memberPackageDao.getActiveForMemberOnce(memberId)
        val baseDate = actives.maxOfOrNull { it.endDateMs }?.takeIf { it > nowMs } ?: nowMs
        val newEnd = baseDate + TimeUnit.DAYS.toMillis(selectedPackage.validityDays.toLong())

        val breakdown = calculatePriceBreakdown(selectedPackage.basePrice, discount, paymentType, installmentCount)

        val mpId = memberPackageDao.insert(
            MemberPackageEntity(
                memberId = memberId,
                packageId = selectedPackage.id,
                packageNameSnapshot = selectedPackage.name,
                packageType = selectedPackage.type,
                totalSessions = selectedPackage.sessionCount,
                remainingSessions = selectedPackage.sessionCount,
                startDateMs = baseDate,
                endDateMs = newEnd,
                packagePrice = selectedPackage.basePrice,
                discount = discount,
                installmentSurcharge = breakdown.surcharge,
                pricePaid = breakdown.finalPrice,
                paymentType = paymentType.name,
                installmentCount = installmentCount,
                paymentStatus = paymentStatus,
                paymentDateMs = paymentDateMs ?: nowMs,
                status = "ACTIVE"
            )
        )

        // Üye özet alanları (geriye dönük) — en uzak bitişe göre güncellenir
        val updatedMember = member.copy(
            activePackageId = selectedPackage.id,
            totalSessions = selectedPackage.sessionCount,
            remainingSessions = selectedPackage.sessionCount,
            startDateMs = baseDate,
            endDateMs = newEnd,
            status = MemberStatus.ACTIVE.name,
            paymentType = paymentType.name,
            installmentCount = installmentCount,
            packagePrice = selectedPackage.basePrice,
            discount = discount,
            pricePaid = breakdown.finalPrice,
            paymentStatus = paymentStatus,
            paymentDateMs = paymentDateMs ?: nowMs,
            updatedAtMs = nowMs
        )
        memberDao.updateMember(updatedMember)

        if (paymentStatus == "PAID" && paymentType != PaymentType.MULTISPORT) {
            transactionDao.insertTransaction(
                TransactionEntity(
                    memberId = memberId,
                    memberPackageId = mpId,
                    amount = breakdown.finalPrice,
                    type = "INCOME",
                    category = TransactionCategory.MEMBERSHIP,
                    description = "${member.fullName} - ${selectedPackage.name} (Yenileme)",
                    paymentMethod = paymentType.name,
                    installmentCount = installmentCount,
                    installmentSurchargeAmount = breakdown.surcharge,
                    date = paymentDateMs ?: nowMs
                )
            )
        }
    }

    suspend fun updatePaymentStatus(memberId: Long, isPaid: Boolean): Result<Unit> = runCatching {
        val member = memberDao.getMemberById(memberId) ?: throw Exception("Üye bulunamadı")
        if (member.paymentStatus == "PAID") return@runCatching

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
                    category = TransactionCategory.MEMBERSHIP,
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

    fun getActivePackagesForMember(memberId: Long): Flow<List<MemberPackageEntity>> =
        memberPackageDao.getActiveForMember(memberId)

    fun getPackageHistoryForMember(memberId: Long): Flow<List<MemberPackageEntity>> =
        memberPackageDao.getHistoryForMember(memberId)

    suspend fun deleteMember(id: Long) = memberDao.softDeleteMember(id)

    suspend fun expireOverdueMembers(): Int {
        val memberExpired = memberDao.expireOverdueMembers()
        memberPackageDao.expireOverduePackages()
        return memberExpired
    }

    // ─── Ölçümler ─────────────────────────────────────────────────────────────

    fun getMeasurementsForMember(memberId: Long): Flow<List<MeasurementEntity>> =
        measurementDao.getMeasurementsForMember(memberId)

    suspend fun getMeasurementById(id: Long): MeasurementEntity? =
        measurementDao.getById(id)

    suspend fun addMeasurement(measurement: MeasurementEntity) =
        measurementDao.insertMeasurement(measurement)

    suspend fun updateMeasurement(measurement: MeasurementEntity) =
        measurementDao.updateMeasurement(measurement)

    suspend fun deleteMeasurement(measurement: MeasurementEntity) =
        measurementDao.deleteMeasurement(measurement)

    suspend fun updateMemberInfo(member: MemberEntity) =
        memberDao.updateMember(member)
}
