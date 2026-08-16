package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymapp.domain.MemberManualStatus
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState

/**
 * Salon üyesi.
 *
 * Hedef biçime ek olarak üç düzeltme:
 *  - **Ölçüm alanları düştü** (`weight`, `height`, `shoulder`, `chest`, `waist`,
 *    `hips`, `leg`, `arm`, `bodyFatPercentage`). Aynı veriler [MeasurementEntity]
 *    içinde tarihli olarak tutuluyordu; üye satırındaki kopya hiçbir zaman
 *    yazılmıyor ama ekranlarda okunabiliyordu — iki kaynak, biri her zaman boş.
 *  - **Kullanılmayan sağlık bayrakları düştü** (`hasChronicDisease`, `hasAllergy`,
 *    `isHealthProfileCompleted`); hiçbir yerde set edilmiyordu.
 *  - **`totalSessions` / `remainingSessions` nullable** — `-1` sentinel'i yerine
 *    `null` = sınırsız (abonman).
 *
 * Silme artık `status = PASSIVE` ile değil **tombstone** (`deletedAtMs`) ile yapılır;
 * [MemberManualStatus.ARCHIVED] ise "silinmedi ama listelenmesin" anlamındaki
 * manuel durumdur.
 */
@Entity(
    tableName = "gym_members",
    indices = [
        Index(value = ["tenantId", "phone"], unique = true),
        Index(value = ["tenantId", "endDateMs"]),
        Index(value = ["tenantId", "status"]),
        Index(value = ["updatedAtMs"]),
    ]
)
data class MemberEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    val fullName: String,

    /** E.164 biçiminde normalize edilmiş numara (+90XXXXXXXXXX). */
    val phone: String,

    val email: String? = null,
    val birthDateMs: Long? = null,

    val activePackageId: String? = null,

    /** Paketin seans kotası; `null` ise sınırsız. */
    val totalSessions: Int? = null,

    /** Kalan seans; `null` ise sınırsız. */
    val remainingSessions: Int? = null,

    val startDateMs: Long? = null,
    val endDateMs: Long? = null,

    val status: MemberManualStatus = MemberManualStatus.ACTIVE,

    val paymentType: PaymentMethod = PaymentMethod.CASH,
    val installmentCount: Int = 1,

    /** Paket liste fiyatı (kuruş). */
    val packagePriceMinor: Long = 0,

    /** Uygulanan iskonto (kuruş). */
    val discountMinor: Long = 0,

    /** Vade farkı dahil ödenecek nihai tutar (kuruş). */
    val pricePaidMinor: Long = 0,

    /**
     * Tahsilat durumu; gerçek bakiye defterden türetilir, bu kolon gösterim
     * içindir.
     *
     * Serbest metindi ve `"PAID"` dışındaki her değer tahsilatı sessizce
     * atlıyordu. Saklanan metin aynı (`name`), göç gerekmedi.
     */
    val paymentStatus: PaymentState = PaymentState.PENDING,

    val paymentDateMs: Long? = null,

    val notes: String? = null,

    // ─── Sağlık bilgileri (KVKK: özel nitelikli kişisel veri) ────────────────
    val healthRisks: String? = null,
    val riskLevel: String = "LOW",
    val healthNotes: String? = null,

    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
)
