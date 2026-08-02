package com.gymapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StaffEntity::class,
            parentColumns = ["id"],
            childColumns = ["staff_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["member_id"]),
        Index(value = ["staff_id"]),
        Index(value = ["start_time_ms"])
    ]
)
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "member_id")
    val memberId: Long,

    @ColumnInfo(name = "staff_id")
    val staffId: Long,

    /** "FITNESS" | "FONKSİYONEL" | "REFORMER" */
    @ColumnInfo(name = "training_type")
    val trainingType: String = "FITNESS",

    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,

    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long,

    @ColumnInfo(name = "status")
    val status: String = "SCHEDULED",

    @ColumnInfo(name = "is_processed")
    val isProcessed: Boolean = false,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)
