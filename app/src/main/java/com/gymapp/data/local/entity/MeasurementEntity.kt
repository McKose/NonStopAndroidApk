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
    /** Boy — cm */
    val height: Double = 0.0,
    /** Kilo — kg */
    val weight: Double = 0.0,
    /** Omuz — cm */
    val shoulder: Double = 0.0,
    /** Göğüs — cm */
    val chest: Double = 0.0,
    /** Karın — cm */
    val waist: Double = 0.0,
    /** Kalça — cm */
    val hips: Double = 0.0,
    /** Bacak — cm */
    val leg: Double = 0.0,
    /** Kol — cm */
    val arm: Double = 0.0,
    val notes: String = ""
)
