package com.gymapp.data.repository

import com.gymapp.data.local.dao.LedgerDao
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.domain.Ids
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finans defteri üzerinde tip güvenli işlemler.
 *
 * Kuruş ↔ [Money] dönüşümü burada yapılır; entity katmanı düz `Long` ile,
 * çağıran katmanlar `Money` ile çalışır.
 *
 * Defter append-only olduğu için burada **güncelleme ve silme yoktur**;
 * düzeltme [reverse] ile yapılır.
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val ledgerDao: LedgerDao,
) {

    // ─── Yazma ──────────────────────────────────────────────────────────────

    /** Üyeye borç tahakkuk ettirir (paket satışı, yenileme). */
    suspend fun recordCharge(
        memberId: String,
        amount: Money,
        description: String,
        category: LedgerCategory = LedgerCategory.MEMBERSHIP,
        occurredAtMs: Long = System.currentTimeMillis(),
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Result<String> = record(
        type = LedgerType.CHARGE,
        category = category,
        amount = amount,
        description = description,
        occurredAtMs = occurredAtMs,
        tenantId = tenantId,
        memberId = memberId,
    )

    /** Tahsilat kaydeder. */
    suspend fun recordPayment(
        amount: Money,
        method: PaymentMethod,
        description: String,
        category: LedgerCategory = LedgerCategory.MEMBERSHIP,
        memberId: String? = null,
        orderId: String? = null,
        occurredAtMs: Long = System.currentTimeMillis(),
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Result<String> = record(
        type = LedgerType.PAYMENT,
        category = category,
        amount = amount,
        description = description,
        occurredAtMs = occurredAtMs,
        tenantId = tenantId,
        memberId = memberId,
        orderId = orderId,
        paymentMethod = method,
    )

    /** Gider kaydeder (hakediş, maaş, kira, fatura). */
    suspend fun recordExpense(
        amount: Money,
        category: LedgerCategory,
        description: String,
        method: PaymentMethod = PaymentMethod.CASH,
        staffId: String? = null,
        appointmentId: String? = null,
        occurredAtMs: Long = System.currentTimeMillis(),
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Result<String> = record(
        type = LedgerType.EXPENSE,
        category = category,
        amount = amount,
        description = description,
        occurredAtMs = occurredAtMs,
        tenantId = tenantId,
        staffId = staffId,
        appointmentId = appointmentId,
        paymentMethod = method,
    )

    /**
     * Bir kaydı ters kayıtla iptal eder.
     *
     * Kayıt **silinmez**; aynı tutarda, [LedgerEntryEntity.reversesId] alanı dolu
     * yeni bir kayıt eklenir. Böylece denetim izi korunur ve toplamlar sıfırlanır.
     *
     * İşlem idempotenttir: zaten iptal edilmiş bir kayıt tekrar iptal edilmez.
     */
    suspend fun reverse(
        entryId: String,
        reason: String,
        occurredAtMs: Long = System.currentTimeMillis(),
    ): Result<String?> = runCatching {
        val original = ledgerDao.getById(entryId)
            ?: throw IllegalArgumentException("Finans kaydı bulunamadı.")

        require(original.reversesId == null) { "Ters kayıt tekrar iptal edilemez." }
        if (ledgerDao.isReversed(entryId)) return@runCatching null // zaten iptal edilmiş

        val reversal = original.copy(
            id = Ids.new(),
            description = "İPTAL — $reason",
            occurredAtMs = occurredAtMs,
            reversesId = original.id,
            createdAtMs = System.currentTimeMillis(),
        )
        ledgerDao.insert(reversal)
        reversal.id
    }

    /**
     * Üyenin tüm aktif tahsilatlarını ters kayıtla iptal eder.
     *
     * "Ödemeyi geri al" akışının karşılığı: eski kurguda `updatePaymentStatus(id, false)`
     * hiçbir şey yapmıyordu çünkü geri alma yolu yoktu.
     *
     * @return iptal edilen kayıt sayısı
     */
    suspend fun reversePaymentsForMember(
        memberId: String,
        reason: String,
        occurredAtMs: Long = System.currentTimeMillis(),
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Result<Int> = runCatching {
        val active = ledgerDao.activePaymentsForMember(tenantId, memberId)
        val reversals = active.map { original ->
            original.copy(
                id = Ids.new(),
                description = "İPTAL — $reason",
                occurredAtMs = occurredAtMs,
                reversesId = original.id,
                createdAtMs = System.currentTimeMillis(),
            )
        }
        if (reversals.isNotEmpty()) ledgerDao.insertAll(reversals)
        reversals.size
    }

    /** Bir randevunun doğurduğu tüm aktif kayıtları iptal eder (randevu geri alınınca). */
    suspend fun reverseForAppointment(
        appointmentId: String,
        reason: String,
        occurredAtMs: Long = System.currentTimeMillis(),
    ): Result<Int> = runCatching {
        val active = ledgerDao.activeEntriesForAppointment(appointmentId)
        val reversals = active.map { original ->
            original.copy(
                id = Ids.new(),
                description = "İPTAL — $reason",
                occurredAtMs = occurredAtMs,
                reversesId = original.id,
                createdAtMs = System.currentTimeMillis(),
            )
        }
        if (reversals.isNotEmpty()) ledgerDao.insertAll(reversals)
        reversals.size
    }

    // ─── Okuma ──────────────────────────────────────────────────────────────

    /** Dönem kayıtları — yarı açık aralık `[startMs, endMs)`. */
    fun observeBetween(
        startMs: Long,
        endMs: Long,
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Flow<List<LedgerEntryEntity>> = ledgerDao.observeBetween(tenantId, startMs, endMs)

    /**
     * Dönem tahsilatı.
     *
     * Ciro **tahsilat** üzerinden hesaplanır (nakit esaslı): tahakkuk eden ama
     * henüz ödenmemiş tutar ciroya girmez.
     */
    fun observeIncome(
        startMs: Long,
        endMs: Long,
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Flow<Money> = ledgerDao
        .observeNetTotal(tenantId, LedgerType.PAYMENT.name, startMs, endMs)
        .map { Money(it) }

    fun observeExpense(
        startMs: Long,
        endMs: Long,
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Flow<Money> = ledgerDao
        .observeNetTotal(tenantId, LedgerType.EXPENSE.name, startMs, endMs)
        .map { Money(it) }

    fun observeForMember(
        memberId: String,
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Flow<List<LedgerEntryEntity>> = ledgerDao.observeForMember(tenantId, memberId)

    /**
     * Üyenin kalan borcu. Pozitif değer borcu gösterir.
     *
     * Ödeme durumu artık kolonda saklanmıyor; bu değerden türetiliyor.
     */
    suspend fun outstandingBalance(
        memberId: String,
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Money = Money(ledgerDao.outstandingBalanceMinor(tenantId, memberId))

    /** Üyenin borcu kapandı mı? */
    suspend fun isSettled(
        memberId: String,
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Boolean = outstandingBalance(memberId, tenantId).minor <= 0L

    // ─── Ortak yazma yolu ───────────────────────────────────────────────────

    private suspend fun record(
        type: LedgerType,
        category: LedgerCategory,
        amount: Money,
        description: String,
        occurredAtMs: Long,
        tenantId: String,
        memberId: String? = null,
        staffId: String? = null,
        orderId: String? = null,
        appointmentId: String? = null,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
    ): Result<String> = runCatching {
        // Tutar daima pozitif; yön `type` ile ifade edilir.
        require(amount.isPositive) { "Finans kaydı tutarı sıfırdan büyük olmalıdır." }
        require(description.isNotBlank()) { "Finans kaydı açıklaması boş olamaz." }

        val entry = LedgerEntryEntity(
            id = Ids.new(),
            tenantId = tenantId,
            type = type,
            category = category,
            amountMinor = amount.minor,
            paymentMethod = paymentMethod,
            memberId = memberId,
            staffId = staffId,
            orderId = orderId,
            appointmentId = appointmentId,
            description = description.trim(),
            occurredAtMs = occurredAtMs,
            createdAtMs = System.currentTimeMillis(),
        )
        ledgerDao.insert(entry)
        entry.id
    }
}
