package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: Long = 0,
    val amount: Double,
    val type: String, // "INCOME", "EXPENSE"
    val category: String, // "MEMBERSHIP", "MARKET", "SALARY", "RENT", "BILL", "PURCHASE", "OTHER"
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: String = "CASH", // "CASH", "CARD", "MULTISPORT"
    val isPending: Boolean = false,
    val note: String? = null
)
