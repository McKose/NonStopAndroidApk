package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Finansal işlem kaydı.
 *
 * Genişletmeler:
 *  - staffId            : Hakediş giderlerinde hangi personele ait olduğunu izlemek için.
 *  - memberPackageId    : Paket ile ilgili gelir/gider kayıtlarını paket kaydına bağlamak için.
 *  - installmentSurchargeAmount : Kart ödemelerinde, paket fiyatının üzerine müşteriye
 *                                 yansıtılan taksit komisyonu (sadece referans amaçlı).
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["memberId"]),
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["staffId"]),
        Index(value = ["memberPackageId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: Long? = null,
    val staffId: Long? = null,
    val memberPackageId: Long? = null,
    val amount: Double,
    val type: String, // INCOME / EXPENSE
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: String = "CASH", // CASH / CARD / MULTISPORT
    val installmentCount: Int = 1,
    val installmentSurchargeAmount: Double = 0.0,
    val isPending: Boolean = false,
    val note: String? = null
)

/** Finans tarafında sık kullanılan sabit kategori anahtarları. */
object TransactionCategory {
    const val MEMBERSHIP = "MEMBERSHIP"
    const val MULTISPORT_SESSION = "MULTISPORT_SESSION"
    const val TRAINER_COMMISSION = "TRAINER_COMMISSION"
    const val SALARY = "SALARY"
    const val RENT = "RENT"
    const val UTILITY = "UTILITY"
    const val MARKET_SALE = "MARKET_SALE"
    // Otomatik vergi kayıtları (PENDING EXPENSE olarak yazılır)
    const val TAX_VAT = "TAX_VAT"
    const val TAX_INCOME = "TAX_INCOME"
    const val OTHER = "OTHER"
}
