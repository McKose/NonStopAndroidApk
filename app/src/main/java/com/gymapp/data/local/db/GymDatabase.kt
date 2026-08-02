package com.gymapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gymapp.data.local.dao.*
import com.gymapp.data.local.entity.*

@Database(
    entities = [
        MemberEntity::class,
        PackageEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        AppointmentEntity::class,
        StaffEntity::class,
        OrderEntity::class,
        MeasurementEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class GymDatabase : RoomDatabase() {

    abstract fun memberDao(): MemberDao
    abstract fun packageDao(): PackageDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun staffDao(): StaffDao
    abstract fun orderDao(): OrderDao
    abstract fun measurementDao(): MeasurementDao
}

// KALDIRILDI: `getInstance()` companion singleton'ı, Hilt'in sağladığıyla aynı isimli
// ("gym_database") ikinci bir Room örneği kurabiliyordu. İki örnek aynı dosyayı açtığında
// invalidation tracker kopar (Flow'lar güncellenmez) ve yazma kilitleri çakışır.
// Veritabanının tek kaynağı artık com.gymapp.di.DatabaseModule.
