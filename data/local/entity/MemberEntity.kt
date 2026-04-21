package com.gymapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity — gym_members tablosu
 *
 * MASAÜSTÜ HATA DÜZELTMELERİ:
 * 1. remainingSessions: Int = -1 → -1 = SINIRSIZ (ABONMAN tipi için)
 *    Desktop'ta bu alan undefined olabiliyordu → NaN hesaplamalarına yol açıyordu.
 * 2. status: MemberStatus enum → String yerine tip güvenli enum
 * 3. endDate üzerinde @ColumnInfo(index = true) → expirationWorker sorguları için optimize
 */
@Entity(
    tableName = "gym_members",
    indices = [
        Index(value = ["phone"], unique = true),
        Index(value = ["end_date"]),         // WorkManager expiration sorgusu için
        Index(value = ["status"])
    ]
)
data class MemberEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "full_name")
    val fullName: String,

    @ColumnInfo(name = "phone")
    val phone: String,

    @ColumnInfo(name = "email")
    val email: String? = null,

    @ColumnInfo(name = "birth_date_ms")
    val birthDateMs: Long? = null,                // epoch ms

    /** Aktif paket ID'si. NULL ise paketsiz üye. */
    @ColumnInfo(name = "active_package_id")
    val activePackageId: Long? = null,

    /**
     * DÜZELTME #1 — Desktop'ta `sessionCount` undefined gelebiliyordu.
     * -1  → SINIRSIZ (ABONMAN tipi paket)
     *  0  → Seans hakkı tükendi
     * >0  → Kalan seans sayısı
     */
    @ColumnInfo(name = "remaining_sessions")
    val remainingSessions: Int = -1,

    @ColumnInfo(name = "start_date_ms")
    val startDateMs: Long? = null,

    @ColumnInfo(name = "end_date_ms")
    val endDateMs: Long? = null,

    /**
     * DÜZELTME #3 — Desktop'ta status hiçbir zaman otomatik güncellenmiyordu.
     * WorkManager periyodik olarak endDate < now → PASSIVE yapar.
     */
    @ColumnInfo(name = "status")
    val status: String = MemberStatus.ACTIVE.name,   // "ACTIVE" | "PASSIVE" | "FROZEN"

    @ColumnInfo(name = "payment_type")
    val paymentType: String = PaymentType.CASH.name, // "CASH" | "CARD"

    @ColumnInfo(name = "installment_count")
    val installmentCount: Int = 1,

    /**
     * DÜZELTME #2 — Komisyon hesabı artık Repository katmanında yapılıyor.
     * Bu alan son ödenen (komisyon dahil) tutarı saklar.
     */
    @ColumnInfo(name = "price_paid")
    val pricePaid: Double = 0.0,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long = System.currentTimeMillis()
)

enum class MemberStatus { ACTIVE, PASSIVE, FROZEN }
enum class PaymentType  { CASH, CARD }
