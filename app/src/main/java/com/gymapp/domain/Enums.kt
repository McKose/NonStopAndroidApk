package com.gymapp.domain

/**
 * Domain sözlüğü — tek doğruluk kaynağı.
 *
 * Bu değerler önceden serbest metin olarak dolaşıyordu ve derleyici yazım
 * farklarını yakalayamıyordu: paket türü `"Fitness"`, randevu türü `"FITNESS"`
 * yazılıyor ve hiçbir zaman eşleşmiyordu; `"ABONMAN"` hiçbir yerde üretilmeyen
 * ölü bir koşuldu; testler üretimde bulunmayan `"PAYMENT"`/`"SALE"` tiplerini
 * kullanıyordu.
 *
 * Türkçe etiketler yalnızca UI katmanında üretilir; veritabanına **asla**
 * görünen metin yazılmaz.
 */

/** Ders/paket branşı. */
enum class TrainingType { FITNESS, FUNCTIONAL, REFORMER }

/** Paket katılım biçimi. */
enum class PackageCategory { INDIVIDUAL, DUET, GROUP }

/** Üyenin manuel olarak atanan durumu. Süre bitişi buradan değil, tarihten türetilir. */
enum class MemberManualStatus { ACTIVE, FROZEN, ARCHIVED }

/** Ödeme aracı. */
enum class PaymentMethod { CASH, CARD, MULTISPORT }

/** Randevunun yaşam döngüsü. */
enum class AppointmentState { SCHEDULED, COMPLETED, CANCELLED, POSTPONED, NO_SHOW }

/** Personel yetki seviyesi. */
enum class StaffRole { ADMIN, MANAGER, TRAINER }

/** Sipariş teslim durumu. */
enum class DeliveryStatus { PRE_DELIVERY, POST_DELIVERY }

/**
 * Finans defteri kayıt yönü.
 *
 * `CHARGE` tahakkuk (borç doğuran), `PAYMENT` tahsilat, `EXPENSE` gider.
 * Ciro raporları **tahsilat** üzerinden hesaplanır (nakit esaslı muhasebe).
 */
enum class LedgerType { CHARGE, PAYMENT, EXPENSE }

/** Finans kaydının konusu. */
enum class LedgerCategory {
    MEMBERSHIP,
    MARKET,
    COMMISSION,
    SALARY,
    RENT,
    BILL,
    PURCHASE,
    OTHER,
}

/** Stok hareketinin sebebi. */
enum class StockMovementReason { PURCHASE, SALE, CORRECTION, RETURN }

/**
 * Paket seans kotası.
 *
 * `-1` sihirli sayısı yerine nullable alan kullanılır: `null` = sınırsız (abonman).
 * Bu, "sınırsız paket ekranda `-1 Seans` görünüyor" hatasını yapısal olarak
 * imkânsız kılar.
 */
object SessionQuota {
    fun isUnlimited(sessionCount: Int?): Boolean = sessionCount == null

    fun hasSessionsLeft(remaining: Int?): Boolean = remaining == null || remaining > 0

    /** Seans düşümü; sınırsız kotada değişiklik olmaz, sıfırın altına inilmez. */
    fun consume(remaining: Int?): Int? = when {
        remaining == null -> null
        remaining > 0 -> remaining - 1
        else -> remaining
    }

    /** Randevu geri alındığında seans iadesi; kota tavanını aşmaz. */
    fun restore(remaining: Int?, total: Int?): Int? = when {
        remaining == null -> null
        total != null && remaining >= total -> remaining
        else -> remaining + 1
    }
}

// ─── Görünen etiketler ──────────────────────────────────────────────────────
// Enum değerleri veritabanında; Türkçe karşılıkları yalnızca ekranda kullanılır.

fun TrainingType.labelTr(): String = when (this) {
    TrainingType.FITNESS -> "Fitness"
    TrainingType.FUNCTIONAL -> "Fonksiyonel"
    TrainingType.REFORMER -> "Reformer"
}

fun PackageCategory.labelTr(): String = when (this) {
    PackageCategory.INDIVIDUAL -> "Bireysel"
    PackageCategory.DUET -> "Düet"
    PackageCategory.GROUP -> "Grup"
}

fun AppointmentState.labelTr(): String = when (this) {
    AppointmentState.SCHEDULED -> "Planlandı"
    AppointmentState.COMPLETED -> "Tamamlandı"
    AppointmentState.CANCELLED -> "İptal"
    AppointmentState.POSTPONED -> "Ertelendi"
    AppointmentState.NO_SHOW -> "Gelmedi"
}
