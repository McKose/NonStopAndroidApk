package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType

/**
 * Üyelik paketi.
 *
 * `orders` / `measurements` / `appointments` / `products` ile aynı hedef biçim:
 * UUID anahtar, `tenantId`, zaman damgaları, tombstone silme, kuruş tutar, enum kolon.
 *
 * İki ek düzeltme:
 *  - **`sessionCount` artık nullable**: `-1` sihirli sayısı kalktı, `null` = sınırsız
 *    (abonman). Sentinel değer ekranda "-1 Seans" olarak sızabiliyordu ve her
 *    karşılaştırmada ayrıca kontrol edilmesi gerekiyordu.
 *  - **`serviceId` düştü**: hiçbir yerde okunmuyordu, yalnızca sabit `1L` yazılıyordu.
 */
@Entity(
    tableName = "gym_packages",
    indices = [
        Index(value = ["tenantId", "name"]),
        Index(value = ["updatedAtMs"]),
    ]
)
data class PackageEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    val name: String,

    val type: TrainingType = TrainingType.FITNESS,

    val category: PackageCategory = PackageCategory.INDIVIDUAL,

    /** Geçerlilik süresi (gün). */
    val validityDays: Int,

    /** Seans kotası; `null` ise sınırsız (abonman). */
    val sessionCount: Int? = null,

    /** Paket fiyatı (kuruş). */
    val basePriceMinor: Long,

    val isActive: Boolean = true,

    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
)
