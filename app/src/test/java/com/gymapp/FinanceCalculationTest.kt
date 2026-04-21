package com.gymapp

import com.gymapp.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceCalculationTest {

    @Test
    fun `calculate total income correctly`() {
        val transactions = listOf(
            TransactionEntity(id = 1, memberId = 0, amount = 100.0, type = "PAYMENT", description = "Membership"),
            TransactionEntity(id = 2, memberId = 0, amount = 50.0, type = "SALE", description = "Water"),
            TransactionEntity(id = 3, memberId = 0, amount = 30.0, type = "EXPENSE", description = "Electricity")
        )

        val income = transactions.filter { it.type == "PAYMENT" || it.type == "SALE" }.sumOf { it.amount }
        
        assertEquals(150.0, income, 0.0)
    }

    @Test
    fun `calculate total expense correctly`() {
        val transactions = listOf(
            TransactionEntity(id = 1, memberId = 0, amount = 100.0, type = "PAYMENT", description = "Membership"),
            TransactionEntity(id = 2, memberId = 0, amount = 40.0, type = "EXPENSE", description = "Rent"),
            TransactionEntity(id = 3, memberId = 0, amount = 20.0, type = "EXPENSE", description = "Cleaning")
        )

        val expense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        
        assertEquals(60.0, expense, 0.0)
    }

    @Test
    fun `filter transactions by type correctly`() {
        val transactions = listOf(
            TransactionEntity(id = 1, memberId = 0, amount = 100.0, type = "PAYMENT", description = "Membership"),
            TransactionEntity(id = 2, memberId = 0, amount = 50.0, type = "EXPENSE", description = "Rent")
        )

        val incomeOnly = transactions.filter { it.type == "PAYMENT" || it.type == "SALE" }
        val expenseOnly = transactions.filter { it.type == "EXPENSE" }

        assertEquals(1, incomeOnly.size)
        assertEquals(1, expenseOnly.size)
    }
}
