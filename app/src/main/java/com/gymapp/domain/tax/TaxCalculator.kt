package com.gymapp.domain.tax

import com.gymapp.data.local.entity.TransactionCategory
import com.gymapp.data.local.entity.TransactionEntity
import java.util.Calendar
import java.util.TimeZone

/**
 * Türkiye 2026 vergi hesapları.
 *
 * KDV (Katma Değer Vergisi):
 *  - Sabit oran %20.
 *  - Ciro KDV dahildir → KDV = ciro × (0.20 / 1.20).
 *  - Kapsam: type=INCOME, isPending=false, kategori TAX_* dışındaki tüm kayıtlar.
 *
 * Gelir Vergisi (yıllık, aşamalı, 2026):
 *      0–190.000        : %15
 *      190.000–400.000  : 28.500  + üstü %20
 *      400.000–1.000.000: 70.500  + üstü %27
 *      1.000.000–2.300.000: 232.500 + üstü %35
 *      2.300.000+       : 687.500 + üstü %40
 *
 *  Matrah = (Gelir / 1.20) − vergi-dışı giderler.
 */
object TaxCalculator {

    const val VAT_RATE = 0.20

    data class QuarterResult(
        val quarter: Int,
        val vat: Double,
        val quarterIncomeTax: Double,
        val cumulativeTaxable: Double,
        val cardIncome: Double,
        val multiSportIncome: Double
    )

    fun progressiveIncomeTax(taxable: Double): Double {
        if (taxable <= 0.0) return 0.0
        return when {
            taxable <= 190_000.0   -> taxable * 0.15
            taxable <= 400_000.0   -> 28_500.0  + (taxable - 190_000.0) * 0.20
            taxable <= 1_000_000.0 -> 70_500.0  + (taxable - 400_000.0) * 0.27
            taxable <= 2_300_000.0 -> 232_500.0 + (taxable - 1_000_000.0) * 0.35
            else                   -> 687_500.0 + (taxable - 2_300_000.0) * 0.40
        }
    }

    fun computeMonthVat(year: Int, month: Int, transactions: List<TransactionEntity>, tz: TimeZone): Double {
        val (start, end) = monthRange(year, month, tz)
        val revenue = transactions
            .filter {
                it.type == "INCOME" && !it.isPending &&
                (it.paymentMethod == "CARD" || it.paymentMethod == "MULTISPORT") &&
                it.category != TransactionCategory.TAX_VAT &&
                it.category != TransactionCategory.TAX_INCOME &&
                it.date in start..end
            }
            .sumOf { it.amount }
        return revenue * (VAT_RATE / (1.0 + VAT_RATE))
    }

    fun computeYearTaxableBase(year: Int, transactions: List<TransactionEntity>, tz: TimeZone): Double {
        val (start, end) = yearRange(year, tz)
        val tx = transactions.filter { it.date in start..end && !it.isPending }
        val gross = tx
            .filter {
                it.type == "INCOME" &&
                (it.paymentMethod == "CARD" || it.paymentMethod == "MULTISPORT") &&
                it.category != TransactionCategory.TAX_VAT &&
                it.category != TransactionCategory.TAX_INCOME
            }
            .sumOf { it.amount }
        val expense = tx
            .filter {
                it.type == "EXPENSE" &&
                it.category != TransactionCategory.TAX_VAT &&
                it.category != TransactionCategory.TAX_INCOME
            }
            .sumOf { it.amount }
        val incomeExclVat = gross / (1.0 + VAT_RATE)
        return (incomeExclVat - expense).coerceAtLeast(0.0)
    }

    fun computeYearIncomeTax(year: Int, transactions: List<TransactionEntity>, tz: TimeZone): Double =
        progressiveIncomeTax(computeYearTaxableBase(year, transactions, tz))

    fun computeYear(year: Int, transactions: List<TransactionEntity>, tz: TimeZone): List<QuarterResult> {
        val result = mutableListOf<QuarterResult>()
        var prevCumulativeTax = 0.0
        for (q in 1..4) {
            val months = quarterMonths(q)
            val start = monthRange(year, months.first(), tz).first
            val end = monthRange(year, months.last(), tz).second

            val vat = months.sumOf { m -> computeMonthVat(year, m, transactions, tz) }

            val cardIncome = transactions.filter {
                it.type == "INCOME" && !it.isPending && it.paymentMethod == "CARD" && it.date in start..end
            }.sumOf { it.amount }

            val multiSportIncome = transactions.filter {
                it.type == "INCOME" && !it.isPending && it.paymentMethod == "MULTISPORT" && it.date in start..end
            }.sumOf { it.amount }

            val cumulativeTaxable = computePartialTaxableBase(year, months.last(), transactions, tz)
            val cumulativeTax = progressiveIncomeTax(cumulativeTaxable)
            val quarterTax = (cumulativeTax - prevCumulativeTax).coerceAtLeast(0.0)
            prevCumulativeTax = cumulativeTax
            result += QuarterResult(q, vat, quarterTax, cumulativeTaxable, cardIncome, multiSportIncome)
        }
        return result
    }

    private fun computePartialTaxableBase(
        year: Int,
        endMonthInclusive: Int,
        transactions: List<TransactionEntity>,
        tz: TimeZone
    ): Double {
        val (yearStart, _) = yearRange(year, tz)
        val end = monthRange(year, endMonthInclusive, tz).second
        val tx = transactions.filter { it.date in yearStart..end && !it.isPending }
        val gross = tx
            .filter {
                it.type == "INCOME" &&
                (it.paymentMethod == "CARD" || it.paymentMethod == "MULTISPORT") &&
                it.category != TransactionCategory.TAX_VAT &&
                it.category != TransactionCategory.TAX_INCOME
            }
            .sumOf { it.amount }
        val expense = tx
            .filter {
                it.type == "EXPENSE" &&
                it.category != TransactionCategory.TAX_VAT &&
                it.category != TransactionCategory.TAX_INCOME
            }
            .sumOf { it.amount }
        val incomeExclVat = gross / (1.0 + VAT_RATE)
        return (incomeExclVat - expense).coerceAtLeast(0.0)
    }

    private fun quarterMonths(q: Int): List<Int> = when (q) {
        1 -> listOf(0, 1, 2)
        2 -> listOf(3, 4, 5)
        3 -> listOf(6, 7, 8)
        4 -> listOf(9, 10, 11)
        else -> emptyList()
    }

    fun monthRange(year: Int, month: Int, tz: TimeZone): Pair<Long, Long> {
        val startCal = Calendar.getInstance(tz).apply {
            clear(); set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }

    fun yearRange(year: Int, tz: TimeZone): Pair<Long, Long> {
        val startCal = Calendar.getInstance(tz).apply {
            clear(); set(year, 0, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance(tz).apply {
            clear(); set(year + 1, 0, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MILLISECOND, -1)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }
}
