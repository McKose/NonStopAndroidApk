package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymapp.domain.StaffRole

/**
 * Personel (eğitmen / yönetici / admin).
 *
 * Hedef biçime ek olarak üç düzeltme:
 *  - **`commissionBasisPoints`** — hakediş oranı artık `Double` kesir değil **baz puan**
 *    (4000 = %40). Kesirli oran `Double` çarpımında yuvarlama sapması üretiyordu ve
 *    "kesir mi yüzde mi" karışıklığı hakedişi 100 kat yanlış hesaplayabiliyordu.
 *  - **`role` enum** — serbest metin `"antrenör"` yerine [StaffRole]. Yetki kontrolü
 *    artık yazım farkına duyarlı değil.
 *  - **`hourlyRate` düştü** — hiçbir hesapta okunmuyordu; bir dönem hakediş oranı
 *    yanlışlıkla buraya yazıldığı için hakediş her zaman sıfır çıkıyordu.
 *
 * `nickname` tenant içinde tekil: giriş adı çakışması artık veritabanı düzeyinde
 * engelleniyor, yalnızca uygulama kodundaki kontrole güvenilmiyor.
 */
@Entity(
    tableName = "staff",
    indices = [
        Index(value = ["tenantId", "nickname"], unique = true),
        Index(value = ["updatedAtMs"]),
    ]
)
data class StaffEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    val fullName: String,

    val title: String,

    val role: StaffRole = StaffRole.TRAINER,

    /** Branş (Fitness, Reformer vb.) — serbest metin, raporlamada gruplama için. */
    val branch: String = "",

    /** Hakediş oranı baz puan cinsinden: 4000 = %40. */
    val commissionBasisPoints: Int = 0,

    /** Aylık maaş (kuruş). */
    val monthlySalaryMinor: Long = 0,

    val phone: String,

    val nickname: String,

    /** NOT (Faz 4): kimlik doğrulama sunucuya taşınınca bu kolon tamamen kalkacak. */
    val password: String = "",

    val isActive: Boolean = true,

    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
)
