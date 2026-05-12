package com.gymapp.domain.tax

import com.gymapp.data.local.dao.TransactionDao
import com.gymapp.data.local.entity.TransactionCategory
import com.gymapp.data.local.entity.TransactionEntity
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geçmişte oluşan KDV ve gelir vergisi dönemleri için otomatik PENDING EXPENSE
 * transaction yazar.
 *
 * Tetikleme: app açılışında (MainActivity onCreate) 1 kez çağrılır.
 *
 * Kurallar:
 *  - KDV: Ay bittikten sonraki ayın 1. günü 00:00'da yazılır. Tutar = o ay
 *    gerçekleşen INCOME × (0.20 / 1.20).
 *  - Gelir Vergisi: Yıllık. (year+1)-04-01 00:00 tarihli tek kayıt. Tutar =
 *    aşamalı gelir vergisi (TaxCalculator).
 *
 * İdempotensi: `description` benzersizliğine bakar. Aynı dönem için ikinci
 * çağrıda yeni kayıt yazılmaz.
 *
 * Tüm zamanlar Europe/Istanbul timezone'unda hesaplanır.
 */
@Singleton
class TaxAutoPostingUseCase @Inject constructor(
    private val transactionDao: TransactionDao
) {

    private val tz: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")
    private val turkishLocale: Locale = Locale("tr", "TR")

    private val turkishMonths = arrayOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )

    suspend operator fun invoke(nowMs: Long = System.currentTimeMillis()) {
        val earliestIncomeMs = transactionDao.getEarliestIncomeDate() ?: return
        val allTransactions = transactionDao.getAllTransactionsOnce()

        postPendingVatRecords(earliestIncomeMs, nowMs, allTransactions)
        postPendingIncomeTaxRecords(earliestIncomeMs, nowMs, allTransactions)
    }

    // ─── KDV ───────────────────────────────────────────────────────────────

    private suspend fun postPendingVatRecords(
        earliestIncomeMs: Long,
        nowMs: Long,
        allTransactions: List<TransactionEntity>
    ) {
        val startCal = Calendar.getInstance(tz).apply {
            timeInMillis = earliestIncomeMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        val cursor = startCal.clone() as Calendar
        while (true) {
            val periodYear = cursor.get(Calendar.YEAR)
            val periodMonth = cursor.get(Calendar.MONTH) // 0-based

            val dueCal = (cursor.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            if (nowMs < dueCal.timeInMillis) break // ay henüz bitmedi

            val desc = "KDV - ${turkishMonths[periodMonth]} $periodYear"
            if (!transactionDao.existsByDescription(desc)) {
                val vat = TaxCalculator.computeMonthVat(periodYear, periodMonth, allTransactions, tz)
                if (vat >= 0.01) {
                    transactionDao.insertTransaction(
                        TransactionEntity(
                            amount = roundToCents(vat),
                            type = "EXPENSE",
                            category = TransactionCategory.TAX_VAT,
                            description = desc,
                            paymentMethod = "CASH",
                            date = dueCal.timeInMillis,
                            isPending = true,
                            note = "Otomatik KDV kaydı (sonraki ayın 1. günü vade)"
                        )
                    )
                }
            }

            cursor.add(Calendar.MONTH, 1)
            if (cursor.timeInMillis > nowMs) break
        }
    }

    // ─── Gelir Vergisi ────────────────────────────────────────────────────

    private suspend fun postPendingIncomeTaxRecords(
        earliestIncomeMs: Long,
        nowMs: Long,
        allTransactions: List<TransactionEntity>
    ) {
        val firstYear = Calendar.getInstance(tz).apply { timeInMillis = earliestIncomeMs }.get(Calendar.YEAR)
        val thisYear = Calendar.getInstance(tz).apply { timeInMillis = nowMs }.get(Calendar.YEAR)

        for (year in firstYear..thisYear) {
            val dueCal = Calendar.getInstance(tz).apply {
                clear(); set(year + 1, Calendar.APRIL, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            }
            if (nowMs < dueCal.timeInMillis) continue // vadesi henüz gelmedi

            val desc = "Gelir Vergisi - $year"
            if (transactionDao.existsByDescription(desc)) continue

            val tax = TaxCalculator.computeYearIncomeTax(year, allTransactions, tz)
            if (tax < 0.01) continue

            transactionDao.insertTransaction(
                TransactionEntity(
                    amount = roundToCents(tax),
                    type = "EXPENSE",
                    category = TransactionCategory.TAX_INCOME,
                    description = desc,
                    paymentMethod = "CASH",
                    date = dueCal.timeInMillis,
                    isPending = true,
                    note = "Otomatik gelir vergisi kaydı (ertesi yıl 1 Nisan vade)"
                )
            )
        }
    }

    private fun roundToCents(value: Double): Double =
        Math.round(value * 100.0) / 100.0
}
