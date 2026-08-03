package com.gymapp.data.repository

import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.entity.*
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.Pricing
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao,
    private val ledgerRepository: LedgerRepository,
    private val measurementDao: com.gymapp.data.local.dao.MeasurementDao
) {
    /**
     * Üye kimliği defterde metin olarak tutulur; tablolar UUID biçimine geçene
     * kadar `Long` kimlik burada köprüleniyor.
     */
    private fun ledgerMemberId(memberId: Long): String = memberId.toString()

    private fun paymentMethodOf(paymentType: PaymentType): PaymentMethod =
        runCatching { PaymentMethod.valueOf(paymentType.name) }
            .getOrDefault(PaymentMethod.CASH)
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

        // Satış her hâlükârda borç doğurur; tahsilat ayrı bir kayıttır.
        // Böylece ödenmemiş bakiye defterden türetilebiliyor.
        val amount = Money.ofMajor(finalPrice)
        val ledgerId = ledgerMemberId(memberId)
        val occurredAt = paymentDateMs ?: nowMs

        if (amount.isPositive) {
            ledgerRepository.recordCharge(
                memberId = ledgerId,
                amount = amount,
                description = "${member.fullName} - ${pkg.name}",
                occurredAtMs = occurredAt,
            ).getOrThrow()

            if (paymentStatus == "PAID") {
                ledgerRepository.recordPayment(
                    amount = amount,
                    method = paymentMethodOf(paymentType),
                    description = "${member.fullName} - ${pkg.name} tahsilat",
                    memberId = ledgerId,
                    occurredAtMs = occurredAt,
                ).getOrThrow()
            }
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

        // Üye satırındaki `pricePaid` yeni paketle değişiyor, ancak önceki paketin
        // ödenmemiş bakiyesi artık kaybolmuyor: defterdeki eski tahakkuk yerinde
        // duruyor ve yeni tahakkuk üzerine ekleniyor, dolayısıyla toplam borç doğru.
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

        val amount = Money.ofMajor(finalPrice)
        val ledgerId = ledgerMemberId(memberId)
        val occurredAt = paymentDateMs ?: nowMs

        if (amount.isPositive) {
            ledgerRepository.recordCharge(
                memberId = ledgerId,
                amount = amount,
                description = "${member.fullName} - ${selectedPackage.name} (Yenileme)",
                occurredAtMs = occurredAt,
            ).getOrThrow()

            if (paymentStatus == "PAID") {
                ledgerRepository.recordPayment(
                    amount = amount,
                    method = paymentMethodOf(paymentType),
                    description = "${member.fullName} - ${selectedPackage.name} (Yenileme) tahsilat",
                    memberId = ledgerId,
                    occurredAtMs = occurredAt,
                ).getOrThrow()
            }
        }
    }

    /**
     * Ödeme durumunu değiştirir.
     *
     * Artık **her iki yön de** çalışıyor: `isPaid = false` çağrısı önceden hiçbir şey
     * yapmıyordu, çünkü tahsilatı geri almanın bir yolu yoktu. Defter append-only
     * olduğu için geri alma, kaydı silerek değil ters kayıt ekleyerek yapılıyor.
     *
     * İşlem idempotenttir: zaten ödenmiş bir üye tekrar tahsil edilmez, zaten
     * borçlu bir üyenin geri alınacak tahsilatı yoktur.
     */
    suspend fun updatePaymentStatus(memberId: Long, isPaid: Boolean): Result<Unit> = runCatching {
        val member = memberDao.getMemberById(memberId) ?: throw Exception("Üye bulunamadı")
        val ledgerId = ledgerMemberId(memberId)
        val nowMs = System.currentTimeMillis()

        if (isPaid) {
            // Kalan borç kadar tahsilat yaz; borç yoksa yapılacak bir şey yok.
            val outstanding = ledgerRepository.outstandingBalance(ledgerId)
            if (!outstanding.isPositive) return@runCatching

            ledgerRepository.recordPayment(
                amount = outstanding,
                method = paymentMethodOf(
                    runCatching { PaymentType.valueOf(member.paymentType) }
                        .getOrDefault(PaymentType.CASH)
                ),
                description = "${member.fullName} - Paket ödemesi alındı",
                memberId = ledgerId,
                occurredAtMs = nowMs,
            ).getOrThrow()

            memberDao.updateMember(
                member.copy(paymentStatus = "PAID", paymentDateMs = nowMs, updatedAtMs = nowMs)
            )
        } else {
            val reversed = ledgerRepository.reversePaymentsForMember(
                memberId = ledgerId,
                reason = "${member.fullName} - ödeme geri alındı",
                occurredAtMs = nowMs,
            ).getOrThrow()
            if (reversed == 0) return@runCatching

            memberDao.updateMember(
                member.copy(paymentStatus = "PENDING", paymentDateMs = null, updatedAtMs = nowMs)
            )
        }
    }

    /**
     * Üyenin kalan borcu. Ödeme durumu geçiş sonrasında bu değerden türetilecek;
     * şimdilik `paymentStatus` kolonu UI uyumluluğu için güncel tutuluyor.
     */
    suspend fun outstandingBalance(memberId: Long) =
        ledgerRepository.outstandingBalance(ledgerMemberId(memberId))

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
