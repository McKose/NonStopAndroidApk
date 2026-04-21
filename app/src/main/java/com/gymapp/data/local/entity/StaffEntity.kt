package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fullName: String,
    val title: String, // Antrenör, Yönetici, Admin vb.
    val role: String = "antrenör", // admin, yönetici, antrenör
    val branch: String, // Fitness, Reformer vb.
    val hourlyRate: Double = 0.0,
    val commissionRate: Double = 0.0, // Örn: 0.40 for %40
    val monthlySalary: Double = 0.0,
    val phone: String,
    val nickname: String = "",
    val password: String = "",
    val isActive: Boolean = true
)
