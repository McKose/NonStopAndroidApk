package com.gymapp.data.repository

import com.gymapp.data.local.dao.LedgerDao
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.domain.Ids
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow

/**
 * Finans okuma/yazma — tek kaynak: `ledger_entries`.
 *
 * Geçiş boyunca eski `transactions` tablosu da okunuyordu (dual-read); tüm
 * yazıcılar deftere taşındığı için o kaynak ve tablo kaldırıldı.
 */
class FinanceRepository(
    private val ledgerDao: LedgerDao,
    private val ledgerRepository: LedgerRepository,
) {
    // ─── Okuma ──────────────────────────────────────────────────────────────

    fun observeLedgerBetween(
        startMs: Long,
        endMs: Long,
        tenantId: String = Ids.DEFAULT_TENANT,
    ): Flow<List<LedgerEntryEntity>> = ledgerDao.observeBetween(tenantId, startMs, endMs)

    // ─── Yazma: yalnızca defter ─────────────────────────────────────────────

    /** Gider kaydı (kira, fatura, maaş, alım). */
    suspend fun addExpense(
        amount: Money,
        category: LedgerCategory,
        description: String,
        method: PaymentMethod,
        occurredAtMs: Long,
    ): Result<String> = ledgerRepository.recordExpense(
        amount = amount,
        category = category,
        description = description,
        method = method,
        occurredAtMs = occurredAtMs,
    )

    /** Elle girilen tahsilat kaydı. */
    suspend fun addIncome(
        amount: Money,
        category: LedgerCategory,
        description: String,
        method: PaymentMethod,
        occurredAtMs: Long,
    ): Result<String> = ledgerRepository.recordPayment(
        amount = amount,
        method = method,
        description = description,
        category = category,
        occurredAtMs = occurredAtMs,
    )

    /** Hatalı kaydı ters kayıtla iptal eder (kayıt silinmez). */
    suspend fun voidEntry(entryId: String, reason: String): Result<String?> =
        ledgerRepository.reverse(entryId, reason)
}
