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
 * Paket yenilenirken kalan seansların ne olacağı.
 *
 * Bu bir **kullanıcı kararı**, uygulamanın varsayımı değil: salonun politikası
 * salona göre değişiyor ve aynı salonda üyeden üyeye de değişebiliyor. Antrenör
 * yenileme ekranında ikisinden birini seçiyor.
 *
 * Enum, `Boolean` yerine tercih edildi: `renewPackage(..., true)` çağrısında
 * `true`'nun neyi seçtiği çağrı yerinde okunmuyor.
 */
enum class SessionCarryOver {
    /** Kalan seanslar yeni paketin üstüne eklenir. */
    CARRY,

    /** Kalan seanslar silinir; üye yeni paketin seansıyla başlar. */
    DISCARD,
}

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

    /**
     * Yenilemeden sonraki seans kotası.
     *
     * Kararı **kullanıcı veriyor** ([SessionCarryOver]); burada yalnızca aritmetik
     * var. Önceden karar diye bir şey yoktu: yenileme kalan seansları koşulsuz
     * siliyordu. Üstelik tarih tarafı tam tersini yapıyordu — üyeliği bitmemiş
     * birinin kalan günleri devrediyor, kalan seansları siliniyordu. İki yarısı
     * birbiriyle çelişen tek bir işlemdi.
     *
     * Dönen değer hem `remainingSessions` hem `totalSessions` için kullanılıyor.
     * İkisinin ayrışması sessiz bir hata olurdu: seans iadesi
     * (`MemberDao.incrementSession`) tavan olarak `totalSessions`'a bakıyor, yani
     * tavan devreden seansları saymazsa iptal edilen bir randevunun hakkı geri
     * verilemezdi.
     *
     * @param newPackageSessions Yeni paketin seans sayısı; `null` sınırsız.
     * @param currentRemaining Üyenin şu anki kalan seansı; `null` sınırsız.
     */
    fun onRenewal(
        newPackageSessions: Int?,
        currentRemaining: Int?,
        carryOver: SessionCarryOver,
    ): Int? {
        // Yeni paket sınırsızsa sayılacak bir kota yok; devir de anlamsız.
        if (newPackageSessions == null) return null
        if (carryOver == SessionCarryOver.DISCARD) return newPackageSessions

        // Eski paket sınırsızsa (`null`) devredecek SAYILABİLİR bir hak yok.
        // "Sınırsız"ı bir sayıya çevirmenin doğru karşılığı olmadığı için sıfır
        // sayılıyor; alternatifi uydurulmuş bir sayı olurdu.
        val devreden = (currentRemaining ?: 0).coerceAtLeast(0)
        return newPackageSessions + devreden
    }

    // Seans düşümü ve iadesi bilinçli olarak **burada değil**: ikisi de
    // `MemberDao.decrementSession` / `incrementSession` içinde tek bir atomik
    // SQL UPDATE olarak duruyor. Kotlin'de yapmak oku-değiştir-yaz demek olurdu
    // ve iki eşzamanlı randevu aynı seansı düşebilirdi.
    //
    // Burada bir kez `consume`/`restore` olarak da yazılmıştı: kimse çağırmıyor,
    // yalnızca testleri vardı. Aynı kuralın ikinci bir kopyası, üstelik üretimde
    // koşan kopyanın kendisi test edilmezken — testler geçtiği için kural
    // doğrulanmış görünüyordu. Kopya kaldırıldı; SQL'i doğrulamanın yolu
    // veritabanı testi yazmak.
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

fun StaffRole.labelTr(): String = when (this) {
    StaffRole.ADMIN -> "Admin"
    StaffRole.MANAGER -> "Yönetici"
    StaffRole.TRAINER -> "Antrenör"
}

// NOT: [MemberManualStatus] için etiket yok — ekranda **türetilen** durum
// gösterilir ([MembershipState.labelTr]); kayıtlı manuel durum tek başına
// kullanıcıya yanlış bilgi verir (süresi dolmuş üye "Aktif" görünürdü).

fun PaymentMethod.labelTr(): String = when (this) {
    PaymentMethod.CASH -> "Nakit"
    PaymentMethod.CARD -> "Kart"
    PaymentMethod.MULTISPORT -> "MultiSpor"
}

fun SessionCarryOver.labelTr(): String = when (this) {
    SessionCarryOver.CARRY -> "Devret"
    SessionCarryOver.DISCARD -> "İptal et"
}

fun AppointmentState.labelTr(): String = when (this) {
    AppointmentState.SCHEDULED -> "Planlandı"
    AppointmentState.COMPLETED -> "Tamamlandı"
    AppointmentState.CANCELLED -> "İptal"
    AppointmentState.POSTPONED -> "Ertelendi"
    AppointmentState.NO_SHOW -> "Gelmedi"
}
