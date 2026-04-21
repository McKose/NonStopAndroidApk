package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["memberId"])]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: Long? = null,
    val totalPrice: Double,
    val discount: Double = 0.0,
    val finalPrice: Double,
    val paymentType: String,
    val paymentStatus: String,
    val deliveryStatus: String,
    val dateMs: Long = System.currentTimeMillis(),
    val notes: String? = null
)
