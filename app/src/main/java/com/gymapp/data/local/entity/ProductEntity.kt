package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Markette satılan ürün.
 *
 * `orders`, `measurements` ve `appointments` ile aynı hedef biçim.
 *
 * `stockCount` kolonu **kaldırıldı**: eldeki stok artık `stock_movements`
 * toplamından türetiliyor. Mutlak sayaç, iki cihaz aynı anda satış yaptığında
 * birinin diğerini ezmesine ve bir satışın sessizce kaybolmasına yol açıyordu.
 */
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["tenantId", "name"]),
        Index(value = ["updatedAtMs"]),
    ]
)
data class ProductEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    val name: String,
    val category: String,

    /** Birim fiyat, kuruş cinsinden. */
    val priceMinor: Long,

    val imageUrl: String? = null,
    val isActive: Boolean = true,

    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
)
