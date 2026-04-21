package com.gymapp

import com.gymapp.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceCalculationTest {

    private fun tx(
        id: Long,
        amount: Double,
        type: String,
        category: String = "OTHER",
        description: String = "",
        isPending: Boolean = false
    ) = TransactionEntity(
        id = id,
        memberId = null,
        amount = amount,
        type = type,
        category = category,
        description = description,
        isPending = isPending
    )

    @Test
    fun `calculate total income correctly`() {
        val transactions = listOf(
            tx(1, 100.0, "INCOME", "MEMBERSHIP", "Membership"),
            tx(2, 50.0, "INCOME", "MARKET", "Water"),
            tx(3, 30.0, "EXPENSE", "BILL", "Electricity")
        )

        val income = transactions.filter { it.type == "INCOME" && !it.isPending }.sumOf { it.amount }

        assertEquals(150.0, income, 0.0)
    }

    @Test
    fun `calculate total expense correctly`() {
        val transactions = listOf(
            tx(1, 100.0, "INCOME", "MEMBERSHIP", "Membership"),
            tx(2, 40.0, "EXPENSE", "RENT", "Rent"),
            tx(3, 20.0, "EXPENSE", "OTHER", "Cleaning")
        )

        val expense = transactions.filter { it.type == "EXPENSE" && !it.isPending }.sumOf { it.amount }

        assertEquals(60.0, expense, 0.0)
    }

    @Test
    fun `filter transactions by type correctly`() {
        val transactions = listOf(
            tx(1, 100.0, "INCOME", "MEMBERSHIP", "Membership"),
            tx(2, 50.0, "EXPENSE", "RENT", "Rent")
        )

        val incomeOnly = transactions.filter { it.type == "INCOME" }
        val expenseOnly = transactions.filter { it.type == "EXPENSE" }

        assertEquals(1, incomeOnly.size)
        assertEquals(1, expenseOnly.size)
    }

    @Test
    fun `pending transactions are excluded from income total`() {
        val transactions = listOf(
            tx(1, 100.0, "INCOME", description = "Paid"),
            tx(2, 200.0, "INCOME", description = "Pending", isPending = true)
        )

        val income = transactions.filter { it.type == "INCOME" && !it.isPending }.sumOf { it.amount }

        assertEquals(100.0, income, 0.0)
    }
}
