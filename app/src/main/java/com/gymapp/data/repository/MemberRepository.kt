package com.gymapp.data.repository

import com.gymapp.data.local.dao.MeasurementDao
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.entity.MeasurementEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.domain.Ids
import com.gymapp.domain.MemberManualStatus
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
    private val measurementDao: MeasurementDao,
) {
    private val tenantId = Ids.DEFAULT_TENANT

    /**
     * Ekranda gösterilen fiyat önizlemesi.
     *
     * Kaydedilen tutar [Pricing.finalPrice] ile **kuruş** üzerinden hesaplanır;
     * bu metot yalnızca aynı kuralın TL karşılığını gösterir.
     */
    fun calculateFinalPrice(
        packagePrice: Double,
        discount: Double,
        paymentType: PaymentMethod,
        installmentCount: Int,
    ): Double = Pricing.previewPrice(packagePrice, discount, paymentType, installmentCount)

    suspend fun registerMember(
        fullName: String,
        phone: String,
        email: String?,
        selectedPackage: PackageEntity?,
        paymentType: PaymentMethod,
        installmentCount: Int,
        discount: Double,
        paymentStatus: String,
        paymentDateMs: Long?,
        healthRisks: String?,
        healthNotes: String?,
        notes: String?,
    ): Result<String> = runCatching {
        // Paket zorunlu: paketsiz üyenin bitiş tarihi olmaz ve üyelik hiçbir zaman sona ermez.
        val pkg = selectedPackage
            ?: throw IllegalArgumentException("Üyelik paketi seçilmelidir.")

        // Numara E.164'e normalize edilmeden yazılırsa UNIQUE index'i işe yaramaz.
        val normalizedPhone = PhoneNumber.normalizeTr(phone)
            ?: throw IllegalArgumentException("Geçerli bir cep telefonu numarası giriniz.")

        if (memberDao.getMemberByPhone(tenantId, normalizedPhone) != null) {
            throw IllegalArgumentException("Bu telefon numarası zaten kayıtlı.")
        }

        val nowMs = System.currentTimeMillis()
        val endDateMs = nowMs + TimeUnit.DAYS.toMillis(pkg.validityDays.toLong())

        val basePrice = Money(pkg.basePriceMinor)
        val safeInstallment = Pricing.normalizeInstallment(paymentType, installmentCount)
        val safeDiscount = Money.ofMajor(discount).coerceNonNegative().coerceAtMost(basePrice)
        val finalPrice = Pricing.finalPrice(basePrice, safeDiscount, paymentType, safeInstallment)

        val memberId = Ids.new()
        val member = MemberEntity(
            id = memberId,
            tenantId = tenantId,
            fullName = fullName.trim(),
            phone = normalizedPhone,
            email = email?.trim()?.takeIf { it.isNotEmpty() },
            activePackageId = pkg.id,
            // DÜZELTME: totalSessions daha önce hiç yazılmıyordu; sentinel kalınca personel
            // hakedişi hesaplayan koşul hiçbir zaman sağlanmıyordu.
            totalSessions = pkg.sessionCount,
            remainingSessions = pkg.sessionCount,
            startDateMs = nowMs,
            endDateMs = endDateMs,
            status = MemberManualStatus.ACTIVE,
            paymentType = paymentType,
            installmentCount = safeInstallment,
            packagePriceMinor = basePrice.minor,
            discountMinor = safeDiscount.minor,
            pricePaidMinor = finalPrice.minor,
            paymentStatus = paymentStatus,
            paymentDateMs = paymentDateMs ?: nowMs,
            healthRisks = healthRisks?.takeIf { it.isNotBlank() },
            healthNotes = healthNotes?.takeIf { it.isNotBlank() },
            notes = notes?.takeIf { it.isNotBlank() },
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )

        // Kontrol ile insert arasındaki yarışta UNIQUE index devreye girer;
        // ham SQLite hatası yerine anlaşılır mesaj döndür.
        try {
            memberDao.insertMember(member)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw IllegalArgumentException("Bu telefon numarası zaten kayıtlı.", e)
        }

        recordSale(
            memberId = memberId,
            memberName = member.fullName,
            packageName = pkg.name,
            amount = finalPrice,
            paymentType = paymentType,
            paymentStatus = paymentStatus,
            occurredAtMs = paymentDateMs ?: nowMs,
            suffix = null,
        )

        memberId
    }

    suspend fun renewPackage(
        memberId: String,
        selectedPackage: PackageEntity,
        paymentType: PaymentMethod,
        installmentCount: Int,
        discount: Double,
        paymentStatus: String,
        paymentDateMs: Long?,
    ): Result<Unit> = runCatching {
        val member = memberDao.getMemberById(memberId)
            ?: throw IllegalArgumentException("Üye bulunamadı.")

        val nowMs = System.currentTimeMillis()

        // Üyelik henüz bitmediyse yeni süre mevcut bitiş tarihinden başlar; aksi hâlde bugünden.
        val currentEndDate = member.endDateMs ?: nowMs
        val baseDate = if (currentEndDate > nowMs) currentEndDate else nowMs
        val endDateMs = baseDate + TimeUnit.DAYS.toMillis(selectedPackage.validityDays.toLong())

        val basePrice = Money(selectedPackage.basePriceMinor)
        val safeInstallment = Pricing.normalizeInstallment(paymentType, installmentCount)
        val safeDiscount = Money.ofMajor(discount).coerceNonNegative().coerceAtMost(basePrice)
        val finalPrice = Pricing.finalPrice(basePrice, safeDiscount, paymentType, safeInstallment)

        // Üye satırındaki tutarlar yeni paketle değişiyor, ancak önceki paketin ödenmemiş
        // bakiyesi kaybolmuyor: defterdeki eski tahakkuk yerinde duruyor ve yeni tahakkuk
        // üzerine ekleniyor, dolayısıyla toplam borç doğru.
        memberDao.updateMember(
            member.copy(
                activePackageId = selectedPackage.id,
                totalSessions = selectedPackage.sessionCount,
                remainingSessions = selectedPackage.sessionCount,
                startDateMs = baseDate,
                endDateMs = endDateMs,
                status = MemberManualStatus.ACTIVE,
                paymentType = paymentType,
                installmentCount = safeInstallment,
                packagePriceMinor = basePrice.minor,
                discountMinor = safeDiscount.minor,
                pricePaidMinor = finalPrice.minor,
                paymentStatus = paymentStatus,
                paymentDateMs = paymentDateMs ?: nowMs,
                updatedAtMs = nowMs,
            )
        )

        recordSale(
            memberId = memberId,
            memberName = member.fullName,
            packageName = selectedPackage.name,
            amount = finalPrice,
            paymentType = paymentType,
            paymentStatus = paymentStatus,
            occurredAtMs = paymentDateMs ?: nowMs,
            suffix = "Yenileme",
        )
    }

    /**
     * Satışın defter kayıtları.
     *
     * Satış her hâlükârda **borç doğurur** (CHARGE); tahsilat ayrı bir kayıttır (PAYMENT).
     * Böylece ödenmemiş bakiye defterden türetilebiliyor ve kısmi ödeme kaybolmuyor.
     */
    private suspend fun recordSale(
        memberId: String,
        memberName: String,
        packageName: String,
        amount: Money,
        paymentType: PaymentMethod,
        paymentStatus: String,
        occurredAtMs: Long,
        suffix: String?,
    ) {
        if (!amount.isPositive) return

        val label = if (suffix == null) "$memberName - $packageName" else "$memberName - $packageName ($suffix)"

        ledgerRepository.recordCharge(
            memberId = memberId,
            amount = amount,
            description = label,
            occurredAtMs = occurredAtMs,
        ).getOrThrow()

        if (paymentStatus == "PAID") {
            ledgerRepository.recordPayment(
                amount = amount,
                method = paymentType,
                description = "$label tahsilat",
                memberId = memberId,
                occurredAtMs = occurredAtMs,
            ).getOrThrow()
        }
    }

    /**
     * Ödeme durumunu değiştirir.
     *
     * **Her iki yön de** çalışır: `isPaid = false` çağrısı önceden hiçbir şey yapmıyordu,
     * çünkü tahsilatı geri almanın bir yolu yoktu. Defter append-only olduğu için geri
     * alma, kaydı silerek değil ters kayıt ekleyerek yapılıyor.
     *
     * İşlem idempotenttir: zaten ödenmiş bir üye tekrar tahsil edilmez, zaten borçlu bir
     * üyenin geri alınacak tahsilatı yoktur.
     */
    suspend fun updatePaymentStatus(memberId: String, isPaid: Boolean): Result<Unit> = runCatching {
        val member = memberDao.getMemberById(memberId)
            ?: throw IllegalArgumentException("Üye bulunamadı.")
        val nowMs = System.currentTimeMillis()

        if (isPaid) {
            // Kalan borç kadar tahsilat yaz; borç yoksa yapılacak bir şey yok.
            val outstanding = ledgerRepository.outstandingBalance(memberId)
            if (!outstanding.isPositive) return@runCatching

            ledgerRepository.recordPayment(
                amount = outstanding,
                method = member.paymentType,
                description = "${member.fullName} - Paket ödemesi alındı",
                memberId = memberId,
                occurredAtMs = nowMs,
            ).getOrThrow()

            memberDao.updateMember(
                member.copy(paymentStatus = "PAID", paymentDateMs = nowMs, updatedAtMs = nowMs)
            )
        } else {
            val reversed = ledgerRepository.reversePaymentsForMember(
                memberId = memberId,
                reason = "${member.fullName} - ödeme geri alındı",
                occurredAtMs = nowMs,
            ).getOrThrow()
            if (reversed == 0) return@runCatching

            memberDao.updateMember(
                member.copy(paymentStatus = "PENDING", paymentDateMs = null, updatedAtMs = nowMs)
            )
        }
    }

    /** Üyenin kalan borcu — defterden türetilir, üye satırındaki kolondan değil. */
    suspend fun outstandingBalance(memberId: String): Money =
        ledgerRepository.outstandingBalance(memberId)

    // ─── Sorgu metodları ──────────────────────────────────────────────────────

    fun getAllMembers(): Flow<List<MemberEntity>> = memberDao.getAllMembers(tenantId)
    fun getActiveMembers(): Flow<List<MemberEntity>> = memberDao.getActiveMembers(tenantId)
    fun searchMembers(query: String): Flow<List<MemberEntity>> =
        memberDao.searchMembers(tenantId, query)

    fun getMemberById(id: String): Flow<MemberEntity?> = memberDao.getMemberByIdFlow(id)

    /** Tombstone siler; üyeye bağlı randevu, ölçüm ve defter kayıtları öksüz kalmaz. */
    suspend fun deleteMember(id: String) =
        memberDao.softDeleteMember(id, System.currentTimeMillis())

    suspend fun updateMemberInfo(member: MemberEntity) =
        memberDao.updateMember(member.copy(updatedAtMs = System.currentTimeMillis()))

    // ─── Ölçümler ─────────────────────────────────────────────────────────────

    fun getMeasurementsForMember(memberId: String): Flow<List<MeasurementEntity>> =
        measurementDao.observeForMember(tenantId, memberId)

    /** Kimlik ve zaman damgaları burada üretilir; çağıran katmanın bilmesi gerekmez. */
    suspend fun addMeasurement(
        memberId: String,
        height: Double,
        weight: Double,
        shoulder: Double,
        chest: Double,
        waist: Double,
        hips: Double,
        leg: Double,
        arm: Double,
        notes: String,
    ): Result<Unit> = runCatching {
        val nowMs = System.currentTimeMillis()
        measurementDao.insert(
            MeasurementEntity(
                id = Ids.new(),
                tenantId = tenantId,
                memberId = memberId,
                dateMs = nowMs,
                height = height,
                weight = weight,
                shoulder = shoulder,
                chest = chest,
                waist = waist,
                hips = hips,
                leg = leg,
                arm = arm,
                notes = notes.trim(),
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
        )
    }

    /** Fiziksel silmez; tombstone işaretler ki silme de senkronize olabilsin. */
    suspend fun deleteMeasurement(measurementId: String) =
        measurementDao.softDelete(measurementId)
}
