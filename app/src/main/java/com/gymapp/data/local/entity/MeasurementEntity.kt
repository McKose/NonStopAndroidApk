package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Üyenin vücut ölçümü kaydı.
 *
 * `orders` ile aynı hedef biçim: UUID anahtar, `tenantId`, zaman damgaları,
 * tombstone silme.
 *
 * Ölçüler (cm/kg) parasal olmadığı için `Double` kalıyor — kuruş dönüşümü
 * yalnızca para alanları için geçerli.
 */
@Entity(
    tableName = "measurements",
    indices = [
        Index(value = ["tenantId", "memberId", "dateMs"]),
        Index(value = ["updatedAtMs"]),
    ]
)
data class MeasurementEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    val memberId: String,

    val dateMs: Long,

    val height: Double = 0.0,
    val weight: Double = 0.0,
    val shoulder: Double = 0.0,
    val chest: Double = 0.0,
    val waist: Double = 0.0,
    val hips: Double = 0.0,
    val leg: Double = 0.0,
    val arm: Double = 0.0,

    val notes: String = "",

    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
)
