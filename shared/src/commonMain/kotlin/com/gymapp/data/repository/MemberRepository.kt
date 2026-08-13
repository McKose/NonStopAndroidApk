package com.gymapp.data.repository

import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.auth.requireTenantId
import com.gymapp.data.local.dao.MeasurementDao
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.db.inTransaction
import com.gymapp.data.local.db.isUniqueConstraintViolation
import com.gymapp.data.local.entity.MeasurementEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.Now
import com.gymapp.domain.Periods
import com.gymapp.domain.Ids
import com.gymapp.domain.MemberManualStatus
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.Pricing
import kotlinx.coroutines.flow.Flow

/**
 * Üye kaydı, paket yenileme ve ödeme durumu.
 *
 * Buradaki yazma yollarının hepsi üye satırı **ve** defter kaydını birlikte
 * değiştiriyor; ikisi tek transaction içinde olmak zorunda. Aksi hâlde araya
 * giren bir hata "paketi yenilenmiş ama borcu yazılmamış üye" ya da "tahsilatı
 * defterde görünen ama satırı borçlu kalan üye" gibi yarım durumlar bırakır —
 * defter append-only olduğu için bunlar elle de düzeltilemez.
 */
class MemberRepository(
    private val database: GymDatabase,
    private val memberDao: MemberDao,
    private val ledgerRepository: LedgerRepository,
    private val measurementDao: MeasurementDao,
    private val syncQueue: SyncQueue,
    private val tenants: TenantProvider,
) {
    /**
     * Çalışılan salon; her sorgu ve her yazma bununla süzülüyor.
     *
     * Sabit `"default"` değeri kaldırıldı: salon kimliği artık oturumdan
     * geliyor ve sunucudaki `gyms.id` ile aynı. Sabit değer, tek salonlu
     * kurulumda çalışıyor gibi görünüp sunucuya gönderimde reddedilirdi —
     * `tenant_id` orada `uuid`.
     */
    private val tenantId: String
        get() = tenants.requireTenantId()

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

        database.inTransaction {
            // Aynı numarayla kayıtlı biri var mı? Tombstone satırlar da UNIQUE index'e
            // dahil olduğu için silinmişler de aranır.
            val existing = memberDao.getMemberByPhone(tenantId, normalizedPhone)
            if (existing != null && existing.deletedAtMs == null) {
                throw IllegalArgumentException("Bu telefon numarası zaten kayıtlı.")
            }

            val nowMs = Now.epochMillis()
            val endDateMs = nowMs + Periods.daysInMillis(pkg.validityDays)

            val basePrice = Money(pkg.basePriceMinor)
            val safeInstallment = Pricing.normalizeInstallment(paymentType, installmentCount)
            val safeDiscount = Money.ofMajor(discount).coerceNonNegative().coerceAtMost(basePrice)
            val finalPrice = Pricing.finalPrice(basePrice, safeDiscount, paymentType, safeInstallment)

            // Geri dönen üye: silinmiş kaydı *canlandır*. Yeni satır açmak numarayı
            // UNIQUE index'e takardı ve üyenin defter geçmişi kopardı.
            val memberId = existing?.id ?: Ids.new()
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
                // Canlandırmada ilk kayıt tarihi korunur; `deletedAtMs` varsayılan olarak null.
                createdAtMs = existing?.createdAtMs ?: nowMs,
                updatedAtMs = nowMs,
            )

            if (existing != null) {
                memberDao.updateMember(member)
            } else {
                // Kontrol ile insert arasındaki yarışta UNIQUE index devreye girer;
                // ham SQLite hatası yerine anlaşılır mesaj döndür.
                try {
                    memberDao.insertMember(member)
                } catch (e: Exception) {
                    if (e.isUniqueConstraintViolation()) {
                        throw IllegalArgumentException("Bu telefon numarası zaten kayıtlı.", e)
                    }
                    throw e
                }
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

            syncQueue.enqueue(SyncTable.MEMBERS, memberId, tenantId, nowMs)
            memberId
        }
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
        database.inTransaction {
            val member = memberDao.getMemberById(memberId)
                ?: throw IllegalArgumentException("Üye bulunamadı.")

            val nowMs = Now.epochMillis()

            // Üyelik henüz bitmediyse yeni süre mevcut bitiş tarihinden başlar; aksi hâlde bugünden.
            val currentEndDate = member.endDateMs ?: nowMs
            val baseDate = if (currentEndDate > nowMs) currentEndDate else nowMs
            val endDateMs = baseDate + Periods.daysInMillis(selectedPackage.validityDays)

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

            syncQueue.enqueue(SyncTable.MEMBERS, memberId, tenantId, nowMs)
        }
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
     *
     * Bakiyeyi okuyup üzerine tahsilat yazmak tek bir transaction içinde olmak
     * zorunda: iki eşzamanlı "ödendi" işareti aynı bakiyeyi okuyup ikisi de
     * tahsilat yazsaydı üye borcunun iki katını ödemiş görünürdü.
     */
    suspend fun updatePaymentStatus(memberId: String, isPaid: Boolean): Result<Unit> = runCatching {
        database.inTransaction {
            val member = memberDao.getMemberById(memberId)
                ?: throw IllegalArgumentException("Üye bulunamadı.")
            val nowMs = Now.epochMillis()

            if (isPaid) {
                // Kalan borç kadar tahsilat yaz; borç yoksa yapılacak bir şey yok.
                val outstanding = ledgerRepository.outstandingBalance(memberId)
                if (!outstanding.isPositive) return@inTransaction

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
                syncQueue.enqueue(SyncTable.MEMBERS, memberId, tenantId, nowMs)
            } else {
                val reversed = ledgerRepository.reversePaymentsForMember(
                    memberId = memberId,
                    reason = "${member.fullName} - ödeme geri alındı",
                    occurredAtMs = nowMs,
                ).getOrThrow()
                if (reversed == 0) return@inTransaction

                memberDao.updateMember(
                    member.copy(paymentStatus = "PENDING", paymentDateMs = null, updatedAtMs = nowMs)
                )
                syncQueue.enqueue(SyncTable.MEMBERS, memberId, tenantId, nowMs)
            }
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

    /**
     * Tombstone siler; üyeye bağlı randevu, ölçüm ve defter kayıtları öksüz kalmaz.
     *
     * Silme de bir değişiklik: tombstone satır kuyruğa girmezse silme sunucuya hiç
     * gitmez ve üye diğer cihazlarda yaşamaya devam eder.
     */
    suspend fun deleteMember(id: String) {
        val nowMs = Now.epochMillis()
        database.inTransaction {
            memberDao.softDeleteMember(id, nowMs)
            syncQueue.enqueue(SyncTable.MEMBERS, id, tenantId, nowMs)
        }
    }

    suspend fun updateMemberInfo(member: MemberEntity) {
        val nowMs = Now.epochMillis()
        database.inTransaction {
            memberDao.updateMember(member.copy(updatedAtMs = nowMs))
            syncQueue.enqueue(SyncTable.MEMBERS, member.id, tenantId, nowMs)
        }
    }

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
        val nowMs = Now.epochMillis()
        val measurementId = Ids.new()
        database.inTransaction {
            measurementDao.insert(
                MeasurementEntity(
                    id = measurementId,
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
            syncQueue.enqueue(SyncTable.MEASUREMENTS, measurementId, tenantId, nowMs)
        }
    }

    /** Fiziksel silmez; tombstone işaretler ki silme de senkronize olabilsin. */
    suspend fun deleteMeasurement(measurementId: String) {
        val nowMs = Now.epochMillis()
        database.inTransaction {
            measurementDao.softDelete(measurementId, nowMs)
            syncQueue.enqueue(SyncTable.MEASUREMENTS, measurementId, tenantId, nowMs)
        }
    }
}
