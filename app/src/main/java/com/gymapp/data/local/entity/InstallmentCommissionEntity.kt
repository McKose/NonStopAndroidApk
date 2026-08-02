package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Taksit sayısına göre kart komisyon oranı.
 *
 * Müşteriye yansıma olarak kullanılır:
 * finalPrice = baseAfterDiscount * (1 + rate / 100)
 *
 * 1-12 arası her taksit için ayrı kayıt tutulur. Kullanıcı değiştirebilir.
 */
@Entity(tableName = "installment_commissions")
data class InstallmentCommissionEntity(
    @PrimaryKey
    val installmentCount: Int, // 1..12
    /** Yüzde cinsinden. 5.0 = %5. */
    val ratePercent: Double
)
