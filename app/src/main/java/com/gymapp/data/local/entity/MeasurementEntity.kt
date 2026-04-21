package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["memberId"])]
)
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
    val arm: Double = 0.0,
    val notes: String = ""
)
