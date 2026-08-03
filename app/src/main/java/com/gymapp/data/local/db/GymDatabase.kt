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
 * v8: [LedgerEntryEntity] (append-only finans defteri) ve [StockMovementEntity]
 * (toplanabilir stok hareketleri) eklendi.
 *
 * v9: contract adımı — tüm yazıcılar deftere taşındığı için eski `transactions`
 * tablosu düşürüldü.
 *
 * v10: entity cutover başladı — `orders` hedef biçime geçti (UUID anahtar,
 * `tenantId`, zaman damgaları, kuruş tutarlar, enum kolonlar). Kalan tablolar
 * aynı desenle sırayla dönüşecek.
 */
@Database(
    entities = [
        MemberEntity::class,
        PackageEntity::class,
        ProductEntity::class,
        AppointmentEntity::class,
        StaffEntity::class,
        OrderEntity::class,
        MeasurementEntity::class,
        // v8 — yeni append-only tablolar
        LedgerEntryEntity::class,
        StockMovementEntity::class,
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class GymDatabase : RoomDatabase() {

    abstract fun memberDao(): MemberDao
    abstract fun packageDao(): PackageDao
    abstract fun productDao(): ProductDao
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
