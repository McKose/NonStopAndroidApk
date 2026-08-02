package com.gymapp.data.repository

import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.dao.TransactionDao
import com.gymapp.data.local.entity.*
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.Pricing
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao,
    private val transactionDao: TransactionDao,
    private val measurementDao: com.gymapp.data.local.dao.MeasurementDao
) {
    /** Fiyat mantığı [Pricing] içinde; burası yalnızca yönlendirir. */
    fun calculateFinalPrice(
        packagePrice: Double,
        discount: Double,
        paymentType: PaymentType,
        installmentCount: Int
    ): Double = Pricing.finalPrice(packagePrice, discount, paymentType, installmentCount)

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
        // Paket zorunlu: pakedsiz üyenin bitiş tarihi olmaz ve üyelik hiçbir zaman sona ermez.
        val pkg = selectedPackage
            ?: throw IllegalArgumentException("Üyelik paketi seçilmelidir.")

        // Numara E.164'e normalize edilmeden yazılırsa UNIQUE index'i işe yaramaz.
        val normalizedPhone = PhoneNumber.normalizeTr(phone)
            ?: throw IllegalArgumentException("Geçerli bir cep telefonu numarası giriniz.")

        val existing = memberDao.getMemberByPhone(normalizedPhone)
        if (existing != null) {
            throw IllegalArgumentException("Bu telefon numarası zaten kayıtlı.")
        }

        val nowMs = System.currentTimeMillis()
        val endDateMs = nowMs + TimeUnit.DAYS.toMillis(pkg.validityDays.toLong())

        val finalPrice = calculateFinalPrice(pkg.basePrice, discount, paymentType, installmentCount)

        val member = MemberEntity(
            fullName = fullName.trim(),
            phone = normalizedPhone,
            email = email?.trim()?.takeIf { it.isNotEmpty() },
            activePackageId = pkg.id,
            // DÜZELTME: totalSessions daha önce hiç yazılmıyordu; -1 kalınca personel
            // hakedişi hesaplayan koşul (`totalSessions > 0`) hiçbir zaman sağlanmıyordu.
            totalSessions = pkg.sessionCount,
            remainingSessions = pkg.sessionCount,
            startDateMs = nowMs,
            endDateMs = endDateMs,
            status = MemberStatus.ACTIVE.name,
            paymentType = paymentType.name,
            installmentCount = installmentCount,
            packagePrice = pkg.basePrice,
            discount = discount.coerceIn(0.0, pkg.basePrice),
            pricePaid = finalPrice,
            paymentStatus = paymentStatus,
            paymentDateMs = paymentDateMs ?: nowMs,
            healthRisks = healthRisks,
            healthNotes = healthNotes,
            notes = notes,
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )

        // Kontrol ile insert arasındaki yarışta UNIQUE index devreye girer;
        // ham SQLite hatası yerine anlaşılır mesaj döndür.
        val memberId = try {
            memberDao.insertMember(member)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw IllegalArgumentException("Bu telefon numarası zaten kayıtlı.", e)
        }

        // Ödeme yapıldıysa Finans'a işle
        if (paymentStatus == "PAID") {
            transactionDao.insertTransaction(
                TransactionEntity(
                    memberId = memberId,
                    amount = finalPrice,
                    type = "INCOME",
                    category = "MEMBERSHIP",
                    description = "${member.fullName} - ${pkg.name}",
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

        // NOT (Faz 1): yenileme, önceki paketin ödenmemiş bakiyesini sessizce siliyor.
        // Kalıcı çözüm append-only finans defteri; o gelene kadar davranış korundu.
        val updatedMember = member.copy(
            activePackageId = selectedPackage.id,
            totalSessions = selectedPackage.sessionCount,
            remainingSessions = selectedPackage.sessionCount,
            startDateMs = baseDate,
            endDateMs = endDateMs,
            status = MemberStatus.ACTIVE.name,
            paymentType = paymentType.name,
            installmentCount = installmentCount,
            packagePrice = selectedPackage.basePrice,
            discount = discount.coerceIn(0.0, selectedPackage.basePrice),
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
