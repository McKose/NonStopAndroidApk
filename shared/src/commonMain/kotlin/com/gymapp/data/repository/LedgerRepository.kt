package com.gymapp.data.repository

import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.auth.requireTenantId
import com.gymapp.data.local.dao.LedgerDao
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.Now
import com.gymapp.domain.Ids
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Finans defteri üzerinde tip güvenli işlemler.
 *
 * Kuruş ↔ [Money] dönüşümü burada yapılır; entity katmanı düz `Long` ile,
 * çağıran katmanlar `Money` ile çalışır.
 *
 * Defter append-only olduğu için burada **güncelleme ve silme yoktur**;
 * düzeltme [reverse] ile yapılır.
 *
 * **Burada transaction açılmaz.** Bu repository'nin yazma metotları neredeyse her
 * zaman başka bir repository'nin transaction'ı içinden çağrılıyor (üye kaydı,
 * sipariş, randevu tamamlama). Kural: transaction'ı giriş noktası açar. Doğrudan
 * çağıran tek yer [FinanceRepository] ve orası kendi transaction'ını açıyor.
 */
class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val syncQueue: SyncQueue,
    private val tenants: TenantProvider,
) {

    // ─── Yazma ──────────────────────────────────────────────────────────────

    /** Üyeye borç tahakkuk ettirir (paket satışı, yenileme). */
    suspend fun recordCharge(
        memberId: String,
        amount: Money,
        description: String,
        category: LedgerCategory = LedgerCategory.MEMBERSHIP,
        occurredAtMs: Long = Now.epochMillis(),
        tenantId: String = tenants.requireTenantId(),
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
        occurredAtMs: Long = Now.epochMillis(),
        tenantId: String = tenants.requireTenantId(),
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
        occurredAtMs: Long = Now.epochMillis(),
        tenantId: String = tenants.requireTenantId(),
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
        occurredAtMs: Long = Now.epochMillis(),
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
            createdAtMs = Now.epochMillis(),
        )
        ledgerDao.insert(reversal)
        syncQueue.enqueue(
            SyncTable.LEDGER_ENTRIES, reversal.id, reversal.tenantId, reversal.createdAtMs,
        )
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
        occurredAtMs: Long = Now.epochMillis(),
        tenantId: String = tenants.requireTenantId(),
    ): Result<Int> = runCatching {
        val active = ledgerDao.activePaymentsForMember(tenantId, memberId)
        val reversals = active.map { original ->
            original.copy(
                id = Ids.new(),
                description = "İPTAL — $reason",
                occurredAtMs = occurredAtMs,
                reversesId = original.id,
                createdAtMs = Now.epochMillis(),
            )
        }
        if (reversals.isNotEmpty()) {
            ledgerDao.insertAll(reversals)
            // Her kayıt kendi tenant'ıyla kuyruğa alınıyor. Hepsine ilk kaydın
            // tenant'ını vermek bugün doğru sonucu üretirdi (tek kiracı var) ama
            // çok kiracılıya geçişte kayıtları yanlış hesaba yazardı.
            reversals.forEach {
                syncQueue.enqueue(SyncTable.LEDGER_ENTRIES, it.id, it.tenantId, it.createdAtMs)
            }
        }
        reversals.size
    }

    /** Bir randevunun doğurduğu tüm aktif kayıtları iptal eder (randevu geri alınınca). */
    suspend fun reverseForAppointment(
        appointmentId: String,
        reason: String,
        occurredAtMs: Long = Now.epochMillis(),
    ): Result<Int> = runCatching {
        val active = ledgerDao.activeEntriesForAppointment(appointmentId)
        val reversals = active.map { original ->
            original.copy(
                id = Ids.new(),
                description = "İPTAL — $reason",
                occurredAtMs = occurredAtMs,
                reversesId = original.id,
                createdAtMs = Now.epochMillis(),
            )
        }
        if (reversals.isNotEmpty()) {
            ledgerDao.insertAll(reversals)
            // Her kayıt kendi tenant'ıyla kuyruğa alınıyor. Hepsine ilk kaydın
            // tenant'ını vermek bugün doğru sonucu üretirdi (tek kiracı var) ama
            // çok kiracılıya geçişte kayıtları yanlış hesaba yazardı.
            reversals.forEach {
                syncQueue.enqueue(SyncTable.LEDGER_ENTRIES, it.id, it.tenantId, it.createdAtMs)
            }
        }
        reversals.size
    }

    // ─── Okuma ──────────────────────────────────────────────────────────────

    /** Dönem kayıtları — yarı açık aralık `[startMs, endMs)`. */
    fun observeBetween(
        startMs: Long,
        endMs: Long,
        tenantId: String = tenants.requireTenantId(),
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
        tenantId: String = tenants.requireTenantId(),
    ): Flow<Money> = ledgerDao
        .observeNetTotal(tenantId, LedgerType.PAYMENT.name, startMs, endMs)
        .map { Money(it) }

    fun observeExpense(
        startMs: Long,
        endMs: Long,
        tenantId: String = tenants.requireTenantId(),
    ): Flow<Money> = ledgerDao
        .observeNetTotal(tenantId, LedgerType.EXPENSE.name, startMs, endMs)
        .map { Money(it) }

    fun observeForMember(
        memberId: String,
        tenantId: String = tenants.requireTenantId(),
    ): Flow<List<LedgerEntryEntity>> = ledgerDao.observeForMember(tenantId, memberId)

    /**
     * Üyenin kalan borcu. Pozitif değer borcu gösterir.
     *
     * Ödeme durumu artık kolonda saklanmıyor; bu değerden türetiliyor.
     */
    suspend fun outstandingBalance(
        memberId: String,
        tenantId: String = tenants.requireTenantId(),
    ): Money = Money(ledgerDao.outstandingBalanceMinor(tenantId, memberId))

    /** Üyenin borcu kapandı mı? */
    suspend fun isSettled(
        memberId: String,
        tenantId: String = tenants.requireTenantId(),
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
            createdAtMs = Now.epochMillis(),
        )
        ledgerDao.insert(entry)
        syncQueue.enqueue(SyncTable.LEDGER_ENTRIES, entry.id, tenantId, entry.createdAtMs)
        entry.id
    }
}
