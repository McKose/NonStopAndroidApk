package com.gymapp.presentation.finance

import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod

/**
 * Finans ekranının kayıt modeli.
 *
 * Geçiş süresince eski `transactions` tablosu ile defteri birleştirmek için
 * kullanılıyordu; tüm yazıcılar deftere taşındığı için artık yalnızca defterden
 * besleniyor. Ekranı entity'ye doğrudan bağlamamak, ileriki şema değişikliklerinde
 * UI'ı korumaya devam ediyor.
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
