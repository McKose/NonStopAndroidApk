package com.gymapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "member_id")
    val memberId: Long,
    
    @ColumnInfo(name = "staff_id")
    val staffId: Long, // Hangi personel ile antrenman yapılacak
    
    /** "FITNESS" | "FONKSİYONEL" | "REFORMER" */
    @ColumnInfo(name = "training_type")
    val trainingType: String = "FITNESS",
    
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,
    
    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long,
    
    @ColumnInfo(name = "status")
    val status: String = "SCHEDULED", // SCHEDULED, COMPLETED, CANCELLED, POSTPONED
    
    @ColumnInfo(name = "is_processed")
    val isProcessed: Boolean = false, // Finansal hakediş ve ders düşümü yapıldı mı?
    
    @ColumnInfo(name = "notes")
    val notes: String? = null
)
