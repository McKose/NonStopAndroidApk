package com.gymapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Üyenin satın aldığı paket kaydı. Bir üyenin birden fazla aktif paketi olabilir
 * (yenileme senaryosu: ikisi aynı anda aktif, üyenin bitiş tarihi = iki paketin
 * toplam süresi). Süresi veya seansı dolan paket HISTORY durumuna alınır.
 *
 * Status değerleri:
 *   - ACTIVE   : Hâlâ geçerli (süre + seans yeterli)
 *   - HISTORY  : Süresi dolmuş veya seansı tükenmiş
 *   - CANCELLED: İptal / iade edilmiş (ileride kullanılacak)
 */
@Entity(
    tableName = "member_packages",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["member_id"]),
        Index(value = ["package_id"]),
        Index(value = ["status"]),
        Index(value = ["end_date_ms"])
    ]
)
data class MemberPackageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "member_id")
    val memberId: Long,

    @ColumnInfo(name = "package_id")
    val packageId: Long,

    @ColumnInfo(name = "package_name_snapshot")
    val packageNameSnapshot: String,

    @ColumnInfo(name = "package_type")
    val packageType: String, // FITNESS / REFORMER / FONKSIYONEL / ABONMAN

    @ColumnInfo(name = "total_sessions")
    val totalSessions: Int, // -1 = sınırsız

    @ColumnInfo(name = "remaining_sessions")
    val remainingSessions: Int,

    @ColumnInfo(name = "start_date_ms")
    val startDateMs: Long,

    @ColumnInfo(name = "end_date_ms")
    val endDateMs: Long,

    @ColumnInfo(name = "package_price")
    val packagePrice: Double,

    @ColumnInfo(name = "discount")
    val discount: Double = 0.0,

    @ColumnInfo(name = "installment_surcharge")
    val installmentSurcharge: Double = 0.0,

    @ColumnInfo(name = "price_paid")
    val pricePaid: Double, // (base-discount)+surcharge

    @ColumnInfo(name = "payment_type")
    val paymentType: String, // CASH/CARD/MULTISPORT

    @ColumnInfo(name = "installment_count")
    val installmentCount: Int = 1,

    @ColumnInfo(name = "payment_status")
    val paymentStatus: String = "PENDING", // PAID / PENDING

    @ColumnInfo(name = "payment_date_ms")
    val paymentDateMs: Long? = null,

    @ColumnInfo(name = "status")
    val status: String = "ACTIVE", // ACTIVE / HISTORY / CANCELLED

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long = System.currentTimeMillis()
)
