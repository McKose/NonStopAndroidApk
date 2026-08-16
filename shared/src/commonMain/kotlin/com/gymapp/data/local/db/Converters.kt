package com.gymapp.data.local.db

import androidx.room.TypeConverter
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

/**
 * Room tip dönüştürücüleri — yalnızca enum'lar için.
 *
 * Parasal alanlar entity'lerde bilinçli olarak düz `Long` (kuruş) kolonu olarak
 * durur ve repository sınırında [com.gymapp.domain.Money]'ye dönüşür. Persistence
 * modelinin domain value class'larına bağlanmaması hem Room tarafını basit tutar
 * hem de KMP geçişinde entity katmanını serbest bırakır.
 *
 * Enum'lar `name` olarak saklanır (okunabilir ve senkronizasyonda kararlı).
 * Tanınmayan bir değerle karşılaşılırsa (ileri sürümden gelen veri) çökmek yerine
 * güvenli bir varsayılana düşülür.
 */
class Converters {

    @TypeConverter fun trainingTypeToName(value: TrainingType): String = value.name
    @TypeConverter fun nameToTrainingType(value: String): TrainingType =
        enumOrDefault(value, TrainingType.FITNESS)

    @TypeConverter fun packageCategoryToName(value: PackageCategory): String = value.name
    @TypeConverter fun nameToPackageCategory(value: String): PackageCategory =
        enumOrDefault(value, PackageCategory.INDIVIDUAL)

    @TypeConverter fun paymentStateToName(value: PaymentState): String = value.name
    @TypeConverter fun nameToPaymentState(value: String): PaymentState =
        // Tanınmayan değerde PENDING: olmayan bir tahsilatı varmış saymak,
        // saymamaktan çok daha pahalı.
        enumOrDefault(value, PaymentState.PENDING)

    @TypeConverter fun memberStatusToName(value: MemberManualStatus): String = value.name
    @TypeConverter fun nameToMemberStatus(value: String): MemberManualStatus =
        enumOrDefault(value, MemberManualStatus.ACTIVE)

    @TypeConverter fun paymentMethodToName(value: PaymentMethod): String = value.name
    @TypeConverter fun nameToPaymentMethod(value: String): PaymentMethod =
        enumOrDefault(value, PaymentMethod.CASH)

    @TypeConverter fun appointmentStateToName(value: AppointmentState): String = value.name
    @TypeConverter fun nameToAppointmentState(value: String): AppointmentState =
        enumOrDefault(value, AppointmentState.SCHEDULED)

    @TypeConverter fun staffRoleToName(value: StaffRole): String = value.name
    @TypeConverter fun nameToStaffRole(value: String): StaffRole =
        enumOrDefault(value, StaffRole.TRAINER)

    @TypeConverter fun deliveryStatusToName(value: DeliveryStatus): String = value.name
    @TypeConverter fun nameToDeliveryStatus(value: String): DeliveryStatus =
        enumOrDefault(value, DeliveryStatus.POST_DELIVERY)

    @TypeConverter fun ledgerTypeToName(value: LedgerType): String = value.name
    @TypeConverter fun nameToLedgerType(value: String): LedgerType =
        enumOrDefault(value, LedgerType.EXPENSE)

    @TypeConverter fun ledgerCategoryToName(value: LedgerCategory): String = value.name
    @TypeConverter fun nameToLedgerCategory(value: String): LedgerCategory =
        enumOrDefault(value, LedgerCategory.OTHER)

    @TypeConverter fun stockReasonToName(value: StockMovementReason): String = value.name
    @TypeConverter fun nameToStockReason(value: String): StockMovementReason =
        enumOrDefault(value, StockMovementReason.CORRECTION)
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    runCatching { enumValueOf<E>(name) }.getOrDefault(default)
