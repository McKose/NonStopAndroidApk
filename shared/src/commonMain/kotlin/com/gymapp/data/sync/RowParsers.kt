package com.gymapp.data.sync

import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.local.entity.MeasurementEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.local.entity.StockMovementEntity
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.DeliveryStatus
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.MemberManualStatus
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import com.gymapp.domain.StaffRole
import com.gymapp.domain.StockMovementReason
import com.gymapp.domain.TrainingType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Sunucudan gelen satırların yerel biçime çevrilmesi.
 *
 * [RowPayloads]'ın tersi ve **aynı kolon adlarını** kullanıyor. İki yönün ayrı
 * ayrı yazılması kaçınılmaz (biri JSON üretir, diğeri okur) ama birbirinden
 * sapmaları mümkün: bir kolon adı yalnızca bir tarafta değiştirilirse gönderim
 * çalışmaya devam eder, okuma sessizce varsayılan değer üretir.
 *
 * Bu yüzden `RowRoundTripTest` her tablo için gidiş-dönüş yapıyor: satır JSON'a
 * çevrilip geri okunduğunda **birebir aynı** olmalı. Tek taraflı bir değişiklik
 * o testi düşürüyor.
 *
 * ### Eksik alan neden hata
 * Zorunlu bir alan okunamadığında satır `null` dönüyor, varsayılan değerle
 * doldurulmuyor. Varsayılan üretmek, sunucudaki gerçek veriyi sessizce yanlış
 * bir değerle değiştirmek olurdu — üstelik o yanlış değer bir sonraki gönderimde
 * sunucuya geri yazılırdı.
 */
internal object RowParsers {

    fun member(row: JsonObject): MemberEntity? = runCatching {
        MemberEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            fullName = row.str("full_name") ?: return null,
            phone = row.str("phone") ?: return null,
            email = row.strOrNull("email"),
            birthDateMs = row.longOrNull("birth_date_ms"),
            activePackageId = row.strOrNull("active_package_id"),
            totalSessions = row.intOrNull("total_sessions"),
            remainingSessions = row.intOrNull("remaining_sessions"),
            startDateMs = row.longOrNull("start_date_ms"),
            endDateMs = row.longOrNull("end_date_ms"),
            status = row.enum("status", MemberManualStatus.ACTIVE),
            paymentType = row.enum("payment_type", PaymentMethod.CASH),
            installmentCount = row.intOrNull("installment_count") ?: 1,
            packagePriceMinor = row.longOrNull("package_price_minor") ?: 0,
            discountMinor = row.longOrNull("discount_minor") ?: 0,
            pricePaidMinor = row.longOrNull("price_paid_minor") ?: 0,
            paymentStatus = row.enum("payment_status", PaymentState.PENDING),
            paymentDateMs = row.longOrNull("payment_date_ms"),
            notes = row.strOrNull("notes"),
            healthRisks = row.strOrNull("health_risks"),
            riskLevel = row.str("risk_level") ?: "LOW",
            healthNotes = row.strOrNull("health_notes"),
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
            updatedAtMs = row.longOrNull("updated_at_ms") ?: return null,
            deletedAtMs = row.longOrNull("deleted_at_ms"),
        )
    }.getOrNull()

    fun packageRow(row: JsonObject): PackageEntity? = runCatching {
        PackageEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            name = row.str("name") ?: return null,
            type = row.enum("type", TrainingType.FITNESS),
            category = row.enum("category", PackageCategory.INDIVIDUAL),
            validityDays = row.intOrNull("validity_days") ?: return null,
            sessionCount = row.intOrNull("session_count"),
            basePriceMinor = row.longOrNull("base_price_minor") ?: return null,
            isActive = row.boolOrNull("is_active") ?: true,
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
            updatedAtMs = row.longOrNull("updated_at_ms") ?: return null,
            deletedAtMs = row.longOrNull("deleted_at_ms"),
        )
    }.getOrNull()

    fun product(row: JsonObject): ProductEntity? = runCatching {
        ProductEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            name = row.str("name") ?: return null,
            category = row.str("category") ?: return null,
            priceMinor = row.longOrNull("price_minor") ?: return null,
            imageUrl = row.strOrNull("image_url"),
            isActive = row.boolOrNull("is_active") ?: true,
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
            updatedAtMs = row.longOrNull("updated_at_ms") ?: return null,
            deletedAtMs = row.longOrNull("deleted_at_ms"),
        )
    }.getOrNull()

    fun appointment(row: JsonObject): AppointmentEntity? = runCatching {
        AppointmentEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            memberId = row.str("member_id") ?: return null,
            staffId = row.str("staff_id") ?: return null,
            trainingType = row.enum("training_type", TrainingType.FITNESS),
            startTimeMs = row.longOrNull("start_time_ms") ?: return null,
            endTimeMs = row.longOrNull("end_time_ms") ?: return null,
            state = row.enum("state", AppointmentState.SCHEDULED),
            sessionValueMinor = row.longOrNull("session_value_minor") ?: 0,
            settledAtMs = row.longOrNull("settled_at_ms"),
            notes = row.strOrNull("notes"),
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
            updatedAtMs = row.longOrNull("updated_at_ms") ?: return null,
            deletedAtMs = row.longOrNull("deleted_at_ms"),
        )
    }.getOrNull()

    /** Personel. */
    fun staff(row: JsonObject): StaffEntity? = runCatching {
        StaffEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            fullName = row.str("full_name") ?: return null,
            title = row.str("title") ?: return null,
            role = row.enum("role", StaffRole.TRAINER),
            branch = row.str("branch") ?: "",
            commissionBasisPoints = row.intOrNull("commission_basis_points") ?: 0,
            monthlySalaryMinor = row.longOrNull("monthly_salary_minor") ?: 0,
            phone = row.str("phone") ?: return null,
            nickname = row.str("nickname") ?: return null,
            authUserId = row.strOrNull("auth_user_id"),
            isActive = row.boolOrNull("is_active") ?: true,
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
            updatedAtMs = row.longOrNull("updated_at_ms") ?: return null,
            deletedAtMs = row.longOrNull("deleted_at_ms"),
        )
    }.getOrNull()

    fun order(row: JsonObject): OrderEntity? = runCatching {
        OrderEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            memberId = row.strOrNull("member_id"),
            totalPriceMinor = row.longOrNull("total_price_minor") ?: return null,
            discountMinor = row.longOrNull("discount_minor") ?: 0,
            finalPriceMinor = row.longOrNull("final_price_minor") ?: return null,
            paymentMethod = row.enum("payment_method", PaymentMethod.CASH),
            // Eksik/tanınmayan değerde PENDING. Önceden satır okunamaz
            // sayılıyordu; okunamayan satır artık tabloyu durdurduğu için
            // (tombstone düzeltmesi) bu, tek bir bozuk alanın bütün siparişleri
            // durdurması demek olurdu. PENDING güvenli taraf: olmayan bir
            // tahsilatı varmış saymıyor.
            paymentStatus = row.enum("payment_status", PaymentState.PENDING),
            deliveryStatus = row.enum("delivery_status", DeliveryStatus.PRE_DELIVERY),
            dateMs = row.longOrNull("date_ms") ?: return null,
            notes = row.strOrNull("notes"),
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
            updatedAtMs = row.longOrNull("updated_at_ms") ?: return null,
            deletedAtMs = row.longOrNull("deleted_at_ms"),
        )
    }.getOrNull()

    fun measurement(row: JsonObject): MeasurementEntity? = runCatching {
        MeasurementEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            memberId = row.str("member_id") ?: return null,
            dateMs = row.longOrNull("date_ms") ?: return null,
            height = row.doubleOrNull("height") ?: 0.0,
            weight = row.doubleOrNull("weight") ?: 0.0,
            shoulder = row.doubleOrNull("shoulder") ?: 0.0,
            chest = row.doubleOrNull("chest") ?: 0.0,
            waist = row.doubleOrNull("waist") ?: 0.0,
            hips = row.doubleOrNull("hips") ?: 0.0,
            leg = row.doubleOrNull("leg") ?: 0.0,
            arm = row.doubleOrNull("arm") ?: 0.0,
            notes = row.str("notes") ?: "",
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
            updatedAtMs = row.longOrNull("updated_at_ms") ?: return null,
            deletedAtMs = row.longOrNull("deleted_at_ms"),
        )
    }.getOrNull()

    fun ledgerEntry(row: JsonObject): LedgerEntryEntity? = runCatching {
        LedgerEntryEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            type = row.enum("type", LedgerType.CHARGE),
            category = row.enum("category", LedgerCategory.OTHER),
            amountMinor = row.longOrNull("amount_minor") ?: return null,
            paymentMethod = row.enum("payment_method", PaymentMethod.CASH),
            memberId = row.strOrNull("member_id"),
            staffId = row.strOrNull("staff_id"),
            orderId = row.strOrNull("order_id"),
            appointmentId = row.strOrNull("appointment_id"),
            description = row.str("description") ?: return null,
            occurredAtMs = row.longOrNull("occurred_at_ms") ?: return null,
            reversesId = row.strOrNull("reverses_id"),
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
        )
    }.getOrNull()

    fun stockMovement(row: JsonObject): StockMovementEntity? = runCatching {
        StockMovementEntity(
            id = row.str("id") ?: return null,
            tenantId = row.str("tenant_id") ?: return null,
            productId = row.str("product_id") ?: return null,
            quantityDelta = row.intOrNull("quantity_delta") ?: return null,
            reason = row.enum("reason", StockMovementReason.CORRECTION),
            orderId = row.strOrNull("order_id"),
            note = row.strOrNull("note"),
            occurredAtMs = row.longOrNull("occurred_at_ms") ?: return null,
            createdAtMs = row.longOrNull("created_at_ms") ?: return null,
        )
    }.getOrNull()
}

// ─── Alan okuma yardımcıları ───────────────────────────────────────────────
//
// Hepsi `null` toleranslı: PostgREST boş kolonları `null` olarak döndürüyor ve
// beklenmedik bir tip istisna değil `null` üretmeli. Satırın geçerli olup
// olmadığına karar veren yer çağıran taraf.

private fun JsonObject.raw(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return runCatching { element.jsonPrimitive.content }.getOrNull()
}

/** Boş metni de `null` sayar; zorunlu alanlar için. */
private fun JsonObject.str(key: String): String? = raw(key)?.takeIf { it.isNotEmpty() }

/**
 * Boş metni **korur**.
 *
 * İsteğe bağlı alanlarda `""` ile `null` farklı: kullanıcı bir notu silmiş
 * olabilir. İkisini birleştirmek, silinen notun geri gelmesine yol açardı.
 */
private fun JsonObject.strOrNull(key: String): String? = raw(key)

private fun JsonObject.longOrNull(key: String): Long? = raw(key)?.toLongOrNull()
private fun JsonObject.intOrNull(key: String): Int? = raw(key)?.toIntOrNull()
private fun JsonObject.doubleOrNull(key: String): Double? = raw(key)?.toDoubleOrNull()
private fun JsonObject.boolOrNull(key: String): Boolean? = raw(key)?.toBooleanStrictOrNull()

/**
 * Enum okur; tanınmayan değerde [fallback].
 *
 * Sunucuda `check` kısıtları olduğu için tanınmayan bir değer normalde
 * oluşmuyor. Yine de çökmek yerine varsayılana düşülüyor: tek bir bozuk satır
 * bütün senkronizasyonu durdurmamalı.
 */
private inline fun <reified T : Enum<T>> JsonObject.enum(key: String, fallback: T): T {
    val text = raw(key) ?: return fallback
    return runCatching { enumValueOf<T>(text) }.getOrDefault(fallback)
}
