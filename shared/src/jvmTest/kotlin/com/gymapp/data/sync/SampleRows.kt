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
import com.gymapp.domain.StaffRole
import com.gymapp.domain.StockMovementReason
import com.gymapp.domain.TrainingType
import kotlinx.serialization.json.JsonObject

/**
 * Her tablo için tek bir örnek satır.
 *
 * İki test de buradan besleniyor: kolon adlarının şemayla örtüştüğünü sınayan
 * test ve gidiş-dönüşün kayıpsız olduğunu sınayan test. Örnekler iki yerde ayrı
 * yazılsaydı biri güncellenip diğeri unutulabilir ve testlerden biri artık
 * gerçekte kullanılan biçimi sınamıyor olurdu.
 *
 * **İsteğe bağlı alanlar bilinçli olarak dolu.** Boş bırakılsalardı, bir alanı
 * hiç yazmayan (ya da hiç okumayan) bir eşleme testlerden geçerdi: eksik olan
 * şeyle boş olan şey aynı görünürdü.
 */
internal object SampleRows {

    const val TENANT = "65409c76-0226-4d89-91a2-48c2ab0d1cab"

    val member = MemberEntity(
        id = "m1", tenantId = TENANT, fullName = "Ayşe", phone = "+905001112233",
        email = "a@b.c", birthDateMs = 1, activePackageId = "p1", totalSessions = 10,
        remainingSessions = 9, startDateMs = 1, endDateMs = 2,
        status = MemberManualStatus.FROZEN, paymentType = PaymentMethod.MULTISPORT,
        installmentCount = 3, packagePriceMinor = 100, discountMinor = 10,
        pricePaidMinor = 90, paymentStatus = "PAID", paymentDateMs = 1, notes = "not",
        healthRisks = "yok", riskLevel = "LOW", healthNotes = "yok",
        createdAtMs = 1, updatedAtMs = 2, deletedAtMs = 3,
    )

    val packageRow = PackageEntity(
        id = "p1", tenantId = TENANT, name = "Aylık", type = TrainingType.REFORMER,
        category = PackageCategory.DUET, validityDays = 30, sessionCount = 12,
        basePriceMinor = 100_000, isActive = false,
        createdAtMs = 1, updatedAtMs = 2, deletedAtMs = 3,
    )

    val product = ProductEntity(
        id = "pr1", tenantId = TENANT, name = "Su", category = "içecek",
        priceMinor = 1500, imageUrl = "http://x/y.png", isActive = false,
        createdAtMs = 1, updatedAtMs = 2, deletedAtMs = 3,
    )

    val appointment = AppointmentEntity(
        id = "a1", tenantId = TENANT, memberId = "m1", staffId = "s1",
        trainingType = TrainingType.FUNCTIONAL, startTimeMs = 1, endTimeMs = 2,
        state = AppointmentState.NO_SHOW, sessionValueMinor = 5000, settledAtMs = 3,
        notes = "not", createdAtMs = 1, updatedAtMs = 2, deletedAtMs = 3,
    )

    val staff = StaffEntity(
        id = "s1", tenantId = TENANT, fullName = "Mehmet", title = "Eğitmen",
        role = StaffRole.MANAGER, branch = "Fitness", commissionBasisPoints = 4000,
        monthlySalaryMinor = 1, phone = "+905001112233", nickname = "mehmet",
        authUserId = "458f1383-d7ef-474b-8e16-798bde768654", password = "gizli",
        isActive = false, createdAtMs = 1, updatedAtMs = 2, deletedAtMs = 3,
    )

    val order = OrderEntity(
        id = "o1", tenantId = TENANT, memberId = "m1", totalPriceMinor = 100,
        discountMinor = 10, finalPriceMinor = 90, paymentMethod = PaymentMethod.CARD,
        paymentStatus = "PAID", deliveryStatus = DeliveryStatus.POST_DELIVERY,
        dateMs = 1, notes = "not", createdAtMs = 1, updatedAtMs = 2, deletedAtMs = 3,
    )

    val measurement = MeasurementEntity(
        id = "me1", tenantId = TENANT, memberId = "m1", dateMs = 1,
        height = 170.5, weight = 70.25, shoulder = 1.0, chest = 2.0,
        waist = 3.0, hips = 4.0, leg = 5.0, arm = 6.0, notes = "not",
        createdAtMs = 1, updatedAtMs = 2, deletedAtMs = 3,
    )

    val ledgerEntry = LedgerEntryEntity(
        id = "l1", tenantId = TENANT, type = LedgerType.EXPENSE,
        category = LedgerCategory.COMMISSION, amountMinor = 100,
        paymentMethod = PaymentMethod.CARD, memberId = "m1", staffId = "s1",
        orderId = "o1", appointmentId = "a1", description = "açıklama",
        occurredAtMs = 1, reversesId = "l0", createdAtMs = 1,
    )

    val stockMovement = StockMovementEntity(
        id = "sm1", tenantId = TENANT, productId = "pr1", quantityDelta = -1,
        reason = StockMovementReason.RETURN, orderId = "o1", note = "not",
        occurredAtMs = 1, createdAtMs = 1,
    )

    /** Tablonun örnek satırının gönderilecek JSON hâli. */
    fun payload(table: SyncTable): JsonObject = when (table) {
        SyncTable.MEMBERS -> RowPayloads.of(member)
        SyncTable.PACKAGES -> RowPayloads.of(packageRow)
        SyncTable.PRODUCTS -> RowPayloads.of(product)
        SyncTable.APPOINTMENTS -> RowPayloads.of(appointment)
        SyncTable.STAFF -> RowPayloads.of(staff)
        SyncTable.ORDERS -> RowPayloads.of(order)
        SyncTable.MEASUREMENTS -> RowPayloads.of(measurement)
        SyncTable.LEDGER_ENTRIES -> RowPayloads.of(ledgerEntry)
        SyncTable.STOCK_MOVEMENTS -> RowPayloads.of(stockMovement)
    }
}
