package com.gymapp.data.repository

import com.gymapp.data.local.dao.LedgerDao
import com.gymapp.data.local.dao.TransactionDao
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.local.entity.TransactionEntity
import com.gymapp.domain.Ids
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finans okuma/yazma.
 *
 * Geçiş süresince **iki kaynak birden** okunur (dual-read): eski `transactions`
 * tablosu ve yeni `ledger_entries` defteri. Yeni yazımların tamamı deftere gider;
 * eski tablo yalnızca daha önce yazılmış kayıtlar için okunur ve kalan yazıcılar
 * da taşındıktan sonra düşürülecek.
 */
@Singleton
class FinanceRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val ledgerDao: LedgerDao,
    private val ledgerRepository: LedgerRepository,
) {
    // ─── Okuma: eski kaynak (yalnızca geçiş süresince) ──────────────────────

    fun getLegacyTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.getAllTransactions()

    // ─── Okuma: defter ──────────────────────────────────────────────────────

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
