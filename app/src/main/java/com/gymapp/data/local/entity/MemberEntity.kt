package com.gymapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gym_members",
    indices = [
        Index(value = ["phone"], unique = true),
        Index(value = ["endDateMs"]),
        Index(value = ["status"])
    ]
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fullName: String,
    val phone: String,
    val email: String? = null,
    val birthDateMs: Long? = null,
    val activePackageId: Long? = null,
    val totalSessions: Int = -1,
    val remainingSessions: Int = -1,
    val startDateMs: Long? = null,
    val endDateMs: Long? = null,
    val status: String = MemberStatus.ACTIVE.name,
    val paymentType: String = PaymentType.CASH.name, // CASH, CARD, MULTISPORT
    val installmentCount: Int = 1,
    val packagePrice: Double = 0.0,
    val discount: Double = 0.0,
    val pricePaid: Double = 0.0, // (packagePrice - discount) + commission
    val paymentStatus: String = "PENDING", // PENDING, PAID
    val paymentDateMs: Long? = null,
    val notes: String? = null,

    // Sağlık Bilgileri
    val healthRisks: String? = null,
    val riskLevel: String = "LOW",
    val hasChronicDisease: Boolean = false,
    val hasAllergy: Boolean = false,
    val healthNotes: String? = null,
    val isHealthProfileCompleted: Boolean = false,

    // Fiziksel Ölçümler
    val weight: Double? = null,
    val height: Double? = null,
    val bodyFatPercentage: Double? = null,
    val shoulder: Double? = null,
    val chest: Double? = null,
    val waist: Double? = null,
    val hips: Double? = null,
    val leg: Double? = null,
    val arm: Double? = null,

    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

enum class MemberStatus { ACTIVE, PASSIVE, FROZEN }
enum class PaymentType  { CASH, CARD, MULTISPORT }
