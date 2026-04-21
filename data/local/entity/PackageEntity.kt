package com.gymapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Üyelik paketleri (ABONMAN veya DERS_PAKETI)
 */
@Entity(tableName = "gym_packages")
data class PackageEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    /** "ABONMAN" | "DERS_PAKETI" */
    @ColumnInfo(name = "type")
    val type: String,

    /** Geçerlilik süresi (gün) */
    @ColumnInfo(name = "validity_days")
    val validityDays: Int,

    /**
     * DÜZELTME #1 — ABONMAN tipinde -1 (sınırsız), DERS_PAKETI tipinde ≥1
     * Desktop'ta bu alan ABONMAN paketlerinde undefined kalıyordu.
     */
    @ColumnInfo(name = "session_count")
    val sessionCount: Int = -1,

    /** Baz fiyat (komisyon HARİÇ) */
    @ColumnInfo(name = "base_price")
    val basePrice: Double,

    @ColumnInfo(name = "service_id")
    val serviceId: Long,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)
