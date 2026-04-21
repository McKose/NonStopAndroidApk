package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["memberId"]),
        Index(value = ["date"]),
        Index(value = ["type"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: Long? = null,
    val amount: Double,
    val type: String,
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: String = "CASH",
    val isPending: Boolean = false,
    val note: String? = null
)
