package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.PaymentMethod

/**
 * Append-only finans defteri.
 *
 * Kayıtlar **değiştirilemez ve silinemez**; düzeltme [reversesId] taşıyan bir
 * ters kayıtla yapılır. Bu tasarım üç sorunu birden çözer:
 *
 *  1. **Ödeme geri alma** mümkün olur — eski `transactions` tablosunda
 *     `updatePaymentStatus(false)` hiçbir şey yapmıyordu, çünkü geri alma yolu yoktu.
 *  2. **Denetim izi** bedavaya gelir; hangi kaydın neyi düzelttiği görülebilir.
 *  3. **Senkronizasyonda çakışma olmaz** — yalnızca `INSERT` var, dolayısıyla iki
 *     cihazın çevrimdışı kayıtları birleştirilirken karar vermek gerekmez.
 *
 * [amountMinor] her zaman **pozitif**tir; yön [type] ile ifade edilir.
 * Tutarlar kuruş cinsindendir; domain sınırında `Money`'ye dönüşür.
 */
@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["tenantId", "occurredAtMs"]),
        Index(value = ["tenantId", "memberId"]),
        Index(value = ["tenantId", "type", "occurredAtMs"]),
        Index(value = ["reversesId"]),
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    /** `CHARGE` borç doğurur, `PAYMENT` tahsilat, `EXPENSE` gider. */
    val type: LedgerType,

    val category: LedgerCategory,

    /** Kuruş cinsinden, **daima pozitif**. */
    val amountMinor: Long,

    val paymentMethod: PaymentMethod = PaymentMethod.CASH,

    // ─── İzlenebilirlik: kaydın hangi işlemden doğduğu ──────────────────────
    val memberId: String? = null,
    val staffId: String? = null,
    val orderId: String? = null,
    val appointmentId: String? = null,

    val description: String,

    /** İşin gerçekleştiği an (kaydın yazıldığı an değil). */
    val occurredAtMs: Long,

    /** Doluysa bu kayıt, işaret ettiği kaydı iptal eden ters kayıttır. */
    val reversesId: String? = null,

    val createdAtMs: Long,
)
