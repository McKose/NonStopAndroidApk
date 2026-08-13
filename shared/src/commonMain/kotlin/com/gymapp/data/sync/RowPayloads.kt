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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Yerel satırların sunucuya gönderilecek JSON karşılığı.
 *
 * Eşleme elle yazılıyor çünkü iki taraf bilinçli olarak farklı adlandırma
 * kullanıyor: Kotlin tarafı `camelCase`, Postgres tarafı `snake_case`. Nedeni
 * 0002_data_tables.sql başında yazılı — tırnaksız Postgres tanımlayıcıları küçük
 * harfe katlandığı için `tenantId` gibi bir kolon her sorguda tırnak istemek
 * demekti ve tek bir unutulan tırnak sessiz hataya dönüşürdü.
 *
 * Bedeli: burada yanlış yazılan bir kolon adı derleme hatası vermez. PostgREST
 * 400 döner, [HttpStatusMapping] bunu kalıcı hata sayar ve kayıt kuyrukta
 * sonsuza kadar bekler — kimsenin okumadığı bir `lastError` ile. Bu yüzden
 * eşlemenin doğruluğu testle bağlanıyor: `RowPayloadColumnsTest` migrasyon
 * dosyasını okuyup her anahtarın gerçekten var olan bir kolona karşılık
 * geldiğini, ve zorunlu her kolonun gönderildiğini doğruluyor.
 *
 * ### `null` alanlar neden atlanmıyor
 * Gönderim upsert (`resolution=merge-duplicates`). PostgREST çakışma hâlinde
 * **yalnızca gövdede bulunan** kolonları günceller. Boş bir alanı atlamak,
 * güncellemede sunucudaki eski değerin olduğu gibi kalması demek olurdu: kullanıcı
 * bir üyenin e-postasını sildiğinde silme hiçbir zaman sunucuya ulaşmazdı. Bu
 * yüzden her kolon her seferinde yazılıyor, boş olanlar açık `null` olarak.
 */
internal object RowPayloads {

    fun of(row: PackageEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("name", row.name)
        put("type", row.type.name)
        put("category", row.category.name)
        put("validity_days", row.validityDays)
        put("session_count", row.sessionCount)
        put("base_price_minor", row.basePriceMinor)
        put("is_active", row.isActive)
        put("created_at_ms", row.createdAtMs)
        put("updated_at_ms", row.updatedAtMs)
        put("deleted_at_ms", row.deletedAtMs)
    }

    /**
     * Personel.
     *
     * `password` alanı **bilinçli olarak gönderilmiyor** ve sunucu tablosunda
     * karşılığı da yok. Kimlik doğrulama Supabase Auth'a ait; düz metin şifreleri
     * sunucuya kopyalamak çözmeye çalıştığımız sorunu büyütmek olurdu. Bu
     * atlamanın kaza sonucu geri gelmemesi için ayrıca test edilmiş durumda.
     */
    fun of(row: StaffEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("full_name", row.fullName)
        put("title", row.title)
        put("role", row.role.name)
        put("branch", row.branch)
        put("commission_basis_points", row.commissionBasisPoints)
        put("monthly_salary_minor", row.monthlySalaryMinor)
        put("phone", row.phone)
        put("nickname", row.nickname)
        put("is_active", row.isActive)
        put("created_at_ms", row.createdAtMs)
        put("updated_at_ms", row.updatedAtMs)
        put("deleted_at_ms", row.deletedAtMs)
    }

    /**
     * Üye.
     *
     * `health_risks` / `risk_level` / `health_notes` KVKK anlamında özel nitelikli
     * kişisel veri ve sunucuya gidiyor. Bilinçli: veriyi panelden görüntülemek
     * istenen şeyin kendisi. Erişim yalnızca salona bağlı kullanıcılarla sınırlı
     * (satır bazlı güvenlik) ve taşıma TLS üzerinde. Alan bazında şifreleme
     * gerekirse bu noktada eklenir — sunucu tarafına dokunmadan.
     */
    fun of(row: MemberEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("full_name", row.fullName)
        put("phone", row.phone)
        put("email", row.email)
        put("birth_date_ms", row.birthDateMs)
        put("active_package_id", row.activePackageId)
        put("total_sessions", row.totalSessions)
        put("remaining_sessions", row.remainingSessions)
        put("start_date_ms", row.startDateMs)
        put("end_date_ms", row.endDateMs)
        put("status", row.status.name)
        put("payment_type", row.paymentType.name)
        put("installment_count", row.installmentCount)
        put("package_price_minor", row.packagePriceMinor)
        put("discount_minor", row.discountMinor)
        put("price_paid_minor", row.pricePaidMinor)
        put("payment_status", row.paymentStatus)
        put("payment_date_ms", row.paymentDateMs)
        put("notes", row.notes)
        put("health_risks", row.healthRisks)
        put("risk_level", row.riskLevel)
        put("health_notes", row.healthNotes)
        put("created_at_ms", row.createdAtMs)
        put("updated_at_ms", row.updatedAtMs)
        put("deleted_at_ms", row.deletedAtMs)
    }

    fun of(row: ProductEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("name", row.name)
        put("category", row.category)
        put("price_minor", row.priceMinor)
        put("image_url", row.imageUrl)
        put("is_active", row.isActive)
        put("created_at_ms", row.createdAtMs)
        put("updated_at_ms", row.updatedAtMs)
        put("deleted_at_ms", row.deletedAtMs)
    }

    fun of(row: AppointmentEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("member_id", row.memberId)
        put("staff_id", row.staffId)
        put("training_type", row.trainingType.name)
        put("start_time_ms", row.startTimeMs)
        put("end_time_ms", row.endTimeMs)
        put("state", row.state.name)
        put("session_value_minor", row.sessionValueMinor)
        put("settled_at_ms", row.settledAtMs)
        put("notes", row.notes)
        put("created_at_ms", row.createdAtMs)
        put("updated_at_ms", row.updatedAtMs)
        put("deleted_at_ms", row.deletedAtMs)
    }

    fun of(row: OrderEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("member_id", row.memberId)
        put("total_price_minor", row.totalPriceMinor)
        put("discount_minor", row.discountMinor)
        put("final_price_minor", row.finalPriceMinor)
        put("payment_method", row.paymentMethod.name)
        put("payment_status", row.paymentStatus)
        put("delivery_status", row.deliveryStatus.name)
        put("date_ms", row.dateMs)
        put("notes", row.notes)
        put("created_at_ms", row.createdAtMs)
        put("updated_at_ms", row.updatedAtMs)
        put("deleted_at_ms", row.deletedAtMs)
    }

    fun of(row: MeasurementEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("member_id", row.memberId)
        put("date_ms", row.dateMs)
        put("height", row.height)
        put("weight", row.weight)
        put("shoulder", row.shoulder)
        put("chest", row.chest)
        put("waist", row.waist)
        put("hips", row.hips)
        put("leg", row.leg)
        put("arm", row.arm)
        put("notes", row.notes)
        put("created_at_ms", row.createdAtMs)
        put("updated_at_ms", row.updatedAtMs)
        put("deleted_at_ms", row.deletedAtMs)
    }

    /**
     * Defter kaydı.
     *
     * `deleted_at_ms` **yok** — defter append-only. Düzeltme silme ile değil ters
     * kayıtla (`reverses_id`) yapılıyor, dolayısıyla sunucu tablosunda da tombstone
     * kolonu yok. Buraya eklemek sunucuda "kolon bulunamadı" hatası verirdi.
     */
    fun of(row: LedgerEntryEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("type", row.type.name)
        put("category", row.category.name)
        put("amount_minor", row.amountMinor)
        put("payment_method", row.paymentMethod.name)
        put("member_id", row.memberId)
        put("staff_id", row.staffId)
        put("order_id", row.orderId)
        put("appointment_id", row.appointmentId)
        put("description", row.description)
        put("occurred_at_ms", row.occurredAtMs)
        put("reverses_id", row.reversesId)
        put("created_at_ms", row.createdAtMs)
    }

    /** Stok hareketi — defter gibi append-only, tombstone kolonu yok. */
    fun of(row: StockMovementEntity): JsonObject = buildJsonObject {
        put("id", row.id)
        put("tenant_id", row.tenantId)
        put("product_id", row.productId)
        put("quantity_delta", row.quantityDelta)
        put("reason", row.reason.name)
        put("order_id", row.orderId)
        put("note", row.note)
        put("occurred_at_ms", row.occurredAtMs)
        put("created_at_ms", row.createdAtMs)
    }
}
