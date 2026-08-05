package com.gymapp.di

import android.content.Context
import com.gymapp.data.local.dao.*
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.db.createGymDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI modülü — veritabanı ve DAO'ları sağlar.
 *
 * Veritabanının **kurulumu** artık burada değil, `:shared` modülünde: şema,
 * sorgular ve sürücü ayarları iki platformda ortak. Burada kalan yalnızca
 * Android tarafındaki yaşam döngüsü bağlaması (tekil örnek, uygulama context'i).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGymDatabase(@ApplicationContext context: Context): GymDatabase =
        createGymDatabase(context)

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
