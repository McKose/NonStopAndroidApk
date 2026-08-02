package com.gymapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gymapp.data.local.dao.*
import com.gymapp.data.local.entity.*

/**
 * Şema geçişi **expand/contract** (paralel değişim) yöntemiyle yapılıyor:
 * yeni tablolar mevcut olanların yanına eklenir, kullanım kademe kademe taşınır,
 * en sonda eskiler düşürülür. Böylece her adımda derlenebilir ve test edilebilir
 * bir durum korunur.
 *
 * v8'de eklenenler: [LedgerEntryEntity] (append-only finans defteri) ve
 * [StockMovementEntity] (toplanabilir stok hareketleri).
 */
@Database(
    entities = [
        MemberEntity::class,
        PackageEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        AppointmentEntity::class,
        StaffEntity::class,
        OrderEntity::class,
        MeasurementEntity::class,
        // v8 — yeni append-only tablolar
        LedgerEntryEntity::class,
        StockMovementEntity::class,
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class GymDatabase : RoomDatabase() {

    abstract fun memberDao(): MemberDao
    abstract fun packageDao(): PackageDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun staffDao(): StaffDao
    abstract fun orderDao(): OrderDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun stockMovementDao(): StockMovementDao
}

// KALDIRILDI: `getInstance()` companion singleton'ı, Hilt'in sağladığıyla aynı isimli
// ("gym_database") ikinci bir Room örneği kurabiliyordu. İki örnek aynı dosyayı açtığında
// invalidation tracker kopar (Flow'lar güncellenmez) ve yazma kilitleri çakışır.
// Veritabanının tek kaynağı artık com.gymapp.di.DatabaseModule.
