package com.gymapp.di

import android.content.Context
import androidx.room.Room
import com.gymapp.data.local.dao.*
import com.gymapp.data.local.db.GymDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI modülü — Room Database ve DAO'ları sağlar.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * KALDIRILDI: `fallbackToDestructiveMigration()`.
     *
     * Şema geçişi sırasında geçici olarak açıktı; cutover bittiği ve şema v1'e
     * sıfırlandığı için artık kapalı. Yıkıcı geçiş açık kalsaydı ileride yazılmayı
     * unutulan bir migration **sessizce tüm veriyi silerdi** — hata vermek yerine.
     * Bundan sonra her şema değişikliği elle yazılmış bir migration ister.
     */
    @Provides
    @Singleton
    fun provideGymDatabase(@ApplicationContext context: Context): GymDatabase =
        Room.databaseBuilder(
            context,
            GymDatabase::class.java,
            "gym_database"
        ).build()

    @Provides
    @Singleton
    fun provideMemberDao(db: GymDatabase): MemberDao = db.memberDao()

    @Provides
    @Singleton
    fun providePackageDao(db: GymDatabase): PackageDao = db.packageDao()

    @Provides
    @Singleton
    fun provideProductDao(db: GymDatabase): ProductDao = db.productDao()

    @Provides
    @Singleton
    fun provideAppointmentDao(db: GymDatabase): AppointmentDao = db.appointmentDao()

    @Provides
    @Singleton
    fun provideStaffDao(db: GymDatabase): StaffDao = db.staffDao()

    @Provides
    @Singleton
    fun provideOrderDao(db: GymDatabase): OrderDao = db.orderDao()

    @Provides
    @Singleton
    fun provideMeasurementDao(db: GymDatabase): MeasurementDao = db.measurementDao()

    @Provides
    @Singleton
    fun provideLedgerDao(db: GymDatabase): LedgerDao = db.ledgerDao()

    @Provides
    @Singleton
    fun provideStockMovementDao(db: GymDatabase): StockMovementDao = db.stockMovementDao()
}
