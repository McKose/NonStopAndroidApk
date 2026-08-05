package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymapp.domain.StockMovementReason

/**
 * Append-only stok hareketi.
 *
 * Stok, ürün üzerinde mutlak bir sayaç olarak tutulduğunda iki cihaz aynı anda
 * satış yaptığında hangi sayacın kazanacağına karar vermek gerekir ve bir satış
 * sessizce kaybolur. Hareketler ise **toplanabilir** (commutative) olduğu için
 * çevrimdışı kayıtlar sırasından bağımsız olarak doğru toplamı verir.
 *
 * Eldeki stok = ilgili ürünün tüm [quantityDelta] değerlerinin toplamı.
 */
@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["tenantId", "productId"]),
        Index(value = ["tenantId", "occurredAtMs"]),
        Index(value = ["orderId"]),
    ]
)
data class StockMovementEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    val productId: String,

    /** Pozitif = giriş (alım, iade), negatif = çıkış (satış, fire). */
    val quantityDelta: Int,

    val reason: StockMovementReason,

    /** Satıştan doğduysa ilgili sipariş. */
    val orderId: String? = null,

    val note: String? = null,

    val occurredAtMs: Long,

    val createdAtMs: Long,
)
