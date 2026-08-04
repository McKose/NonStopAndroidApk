package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymapp.domain.DeliveryStatus
import com.gymapp.domain.PaymentMethod

/**
 * Market siparişi.
 *
 * Entity cutover'ın **ilk** tablosu; hedef biçim burada kuruluyor ve diğer
 * tablolara aynı desen uygulanacak:
 *
 *  - **UUID birincil anahtar** — `autoGenerate` `Long` anahtarlar çok cihazlı
 *    senkronizasyonda çakışır (iki cihaz çevrimdışıyken aynı `id`'yi üretir).
 *  - **`tenantId`** — hesap/salon izolasyonu; web admin tarafındaki satır bazlı
 *    güvenlik politikası bunun üzerine kurulacak.
 *  - **`updatedAtMs` / `deletedAtMs`** — delta senkronizasyon ve tombstone silme.
 *  - **Para kuruş cinsinden `Long`** — `Double` toplamlarda sapma yaratıyordu.
 *  - **Enum kolonlar** — serbest metin yerine tip güvenli değerler.
 *
 * `orders` bilinçli olarak ilk seçildi: kimliğine yalnızca `stock_movements` ve
 * `ledger_entries` referans veriyor ve ikisi de zaten metin kimlik kullanıyor,
 * dolayısıyla bu dönüşüm başka hiçbir tabloyu değiştirmeye zorlamıyor.
 *
 * `memberId` hâlâ `Long`: üye tablosu henüz dönüşmedi, o dilimde `String` olacak.
 */
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["tenantId", "dateMs"]),
        Index(value = ["tenantId", "memberId"]),
        Index(value = ["updatedAtMs"]),
    ]
)
data class OrderEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    /** Misafir satışında `null`. */
    val memberId: Long? = null,

    val totalPriceMinor: Long,
    val discountMinor: Long = 0,
    val finalPriceMinor: Long,

    val paymentMethod: PaymentMethod,

    /** "PAID" | "PENDING" — üye ödeme durumu gibi ileride defterden türetilecek. */
    val paymentStatus: String,

    val deliveryStatus: DeliveryStatus,

    val dateMs: Long,
    val notes: String? = null,

    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
)
