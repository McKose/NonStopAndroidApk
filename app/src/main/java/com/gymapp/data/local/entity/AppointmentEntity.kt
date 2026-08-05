package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.TrainingType

/**
 * Antrenman randevusu.
 *
 * `orders` ve `measurements` ile aynı hedef biçim.
 *
 * İki ek iyileştirme:
 *  - `status`/`trainingType` serbest metin yerine **enum** oldu; ekranda gösterilen
 *    Türkçe etiketler artık veritabanına sızmıyor.
 *  - `isProcessed` bayrağı [settledAtMs] ile değiştirildi: "işlendi mi" sorusunun
 *    yanıtı **ne zaman işlendiği** bilgisini de taşıyor ve niyeti daha açık.
 *
 * [sessionValueMinor] hakediş matrahını **randevu oluşturulurken** dondurur.
 * Önceden matrah tamamlama anında üyenin güncel paketinden hesaplanıyordu; üye
 * arada paketini yenilerse aynı ders için farklı hakediş çıkıyordu.
 *
 * `memberId` / `staffId` hâlâ `Long`: o tablolar henüz dönüşmedi.
 */
@Entity(
    tableName = "appointments",
    indices = [
        Index(value = ["tenantId", "startTimeMs"]),
        Index(value = ["tenantId", "staffId", "startTimeMs"]),
        Index(value = ["tenantId", "memberId"]),
        Index(value = ["updatedAtMs"]),
    ]
)
data class AppointmentEntity(
    @PrimaryKey
    val id: String,

    val tenantId: String,

    val memberId: Long,
    val staffId: Long,

    val trainingType: TrainingType = TrainingType.FITNESS,

    val startTimeMs: Long,
    val endTimeMs: Long,

    val state: AppointmentState = AppointmentState.SCHEDULED,

    /** Hakediş matrahı, randevu anında dondurulur (kuruş). */
    val sessionValueMinor: Long = 0,

    /** Finansal etki uygulandığı an; `null` ise uygulanmamıştır. */
    val settledAtMs: Long? = null,

    val notes: String? = null,

    val createdAtMs: Long,
    val updatedAtMs: Long,
    val deletedAtMs: Long? = null,
)
