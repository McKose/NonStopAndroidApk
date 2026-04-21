package com.gymapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Üyelik paketleri (Fitness, Fonksiyonel, Reformer)
 */
@Entity(tableName = "gym_packages")
data class PackageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    /** "FITNESS" | "FONKSİYONEL" | "REFORMER" */
    @ColumnInfo(name = "type")
    val type: String,

    /** "BİREYSEL" | "DÜET" | "GRUP" */
    @ColumnInfo(name = "category")
    val category: String = "BİREYSEL",

    /** Geçerlilik süresi (gün) */
    @ColumnInfo(name = "validity_days")
    val validityDays: Int,

    /** Seans sayısı (-1 ise sınırsız) */
    @ColumnInfo(name = "session_count")
    val sessionCount: Int = -1,

    /** Paket Fiyatı */
    @ColumnInfo(name = "base_price")
    val basePrice: Double,

    @ColumnInfo(name = "service_id")
    val serviceId: Long = 0,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)
