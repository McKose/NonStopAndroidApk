package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: Long?, // Null for Guest
    val totalPrice: Double,
    val discount: Double = 0.0,
    val finalPrice: Double,
    val paymentType: String, // CASH, CARD
    val paymentStatus: String, // PENDING, PAID
    val deliveryStatus: String, // PRE_DELIVERY, POST_DELIVERY
    val dateMs: Long = System.currentTimeMillis(),
    val notes: String? = null
)
