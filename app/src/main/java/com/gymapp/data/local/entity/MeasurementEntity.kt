package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: Long,
    val dateMs: Long = System.currentTimeMillis(),
    val height: Double = 0.0,
    val weight: Double = 0.0,
    val shoulder: Double = 0.0,
    val chest: Double = 0.0,
    val waist: Double = 0.0,
    val hips: Double = 0.0,
    val leg: Double = 0.0,
    val arm: Double = 0.0, // Ek olarak kol ölçüsü
    val notes: String = ""
)
