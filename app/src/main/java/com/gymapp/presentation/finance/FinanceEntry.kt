package com.gymapp.presentation.finance

import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.local.entity.TransactionEntity
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod

/**
 * Finans ekranının kaynaktan bağımsız kayıt modeli.
 *
 * Geçiş süresince iki kaynak birden okunuyor (**dual-read**): eski `transactions`
 * tablosu ve yeni `ledger_entries` defteri. Yazıcılar tek tek deftere taşınırken
 * finans ekranı kesintisiz doğru çalışsın diye bu ara model gerekiyor; tüm
 * yazıcılar taşındıktan sonra eski kaynak ve bu dönüşüm kaldırılacak.
 */
data class FinanceEntry(
    val id: String,
    val isIncome: Boolean,
    val amount: Money,
    val category: String,
    val description: String,
    val paymentMethod: PaymentMethod,
    val occurredAtMs: Long,
    /** Tahakkuk etmiş ama henüz tahsil edilmemiş tutar. */
    val isPending: Boolean,
    /** Ters kayıtla iptal edilmiş ya da ters kaydın kendisi — toplamlara girmez. */
    val isVoided: Boolean,
    val note: String?,
) {
    val categoryLabel: String get() = categoryLabels[category] ?: category
}

private val categoryLabels = mapOf(
    "MEMBERSHIP" to "Üyelik",
    "MARKET" to "Market",
    "COMMISSION" to "Hakediş",
    "SALARY" to "Maaş",
    "RENT" to "Kira",
    "BILL" to "Fatura",
    "PURCHASE" to "Alım",
    "OTHER" to "Diğer",
)

/** Eski `transactions` kaydı → ortak model. */
fun TransactionEntity.toFinanceEntry(): FinanceEntry = FinanceEntry(
    id = "legacy-$id",
    isIncome = type == "INCOME",
    amount = Money.ofMajor(amount),
    category = category,
    description = description,
    paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }
        .getOrDefault(PaymentMethod.CASH),
    occurredAtMs = date,
    isPending = isPending,
    isVoided = false, // eski tabloda ters kayıt kavramı yoktu
    note = note,
)

/**
 * Defter kaydı → ortak model.
 *
 * `CHARGE` (tahakkuk) eski modeldeki "bekliyor" durumuna karşılık gelir:
 * gelir sayılır ama tahsil edilmediği için ciroya dahil edilmez.
 *
 * @param reversedIds ters kayıtla iptal edilmiş kayıtların kimlikleri
 */
fun LedgerEntryEntity.toFinanceEntry(reversedIds: Set<String>): FinanceEntry = FinanceEntry(
    id = id,
    isIncome = type != LedgerType.EXPENSE,
    amount = Money(amountMinor),
    category = category.name,
    description = description,
    paymentMethod = paymentMethod,
    occurredAtMs = occurredAtMs,
    isPending = type == LedgerType.CHARGE,
    isVoided = reversesId != null || id in reversedIds,
    note = null,
)

/** Enum karşılığı olmayan eski kategori metinlerini de kapsayan yardımcı. */
fun LedgerCategory.labelTr(): String = categoryLabels[name] ?: name
