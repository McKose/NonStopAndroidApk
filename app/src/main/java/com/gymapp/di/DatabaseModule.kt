package com.gymapp.di

import android.content.Context
import androidx.room.Room
import com.gymapp.data.local.dao.*
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.db.MIGRATION_8_9
import com.gymapp.data.local.entity.InstallmentCommissionEntity
import com.gymapp.data.local.entity.MultiSportRateEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * Hilt DI modülü — Room Database ve DAO'ları sağlar.
 *
 * Database açıldıktan sonra installment_commissions tablosu boş ise 1..12 arası
 * varsayılan oranlarla seed edilir. multisport_rates boş ise 0 TL'lik cari kayıt
 * tohumlanır (kullanıcı Settings'ten düzenler).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideGymDatabase(@ApplicationContext context: Context): GymDatabase {
        val db = Room.databaseBuilder(
            context,
            GymDatabase::class.java,
            "gym_database"
        )
            .addMigrations(MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .build()

        seedScope.launch {
            // installment_commissions seed
            val insDao = db.installmentCommissionDao()
            if (insDao.count() == 0) {
                val defaults = listOf(
                    1 to 0.0,   2 to 3.0,   3 to 5.0,
                    4 to 6.0,   5 to 8.0,   6 to 10.0,
                    7 to 12.0,  8 to 13.0,  9 to 15.0,
                    10 to 17.0, 11 to 18.0, 12 to 20.0
                ).map { (c, r) -> InstallmentCommissionEntity(c, r) }
                insDao.upsertAll(defaults)
            }
            // multisport cari ücret seed (0 TL — kullanıcı düzenler)
            val msDao = db.multiSportRateDao()
            if (msDao.getCurrent() == null) {
                msDao.insert(MultiSportRateEntity(amount = 0.0))
            }
        }
        return db
    }

    @Provides @Singleton fun provideMemberDao(db: GymDatabase): MemberDao = db.memberDao()
    @Provides @Singleton fun providePackageDao(db: GymDatabase): PackageDao = db.packageDao()
    @Provides @Singleton fun provideProductDao(db: GymDatabase): ProductDao = db.productDao()
    @Provides @Singleton fun provideTransactionDao(db: GymDatabase): TransactionDao = db.transactionDao()
    @Provides @Singleton fun provideAppointmentDao(db: GymDatabase): AppointmentDao = db.appointmentDao()
    @Provides @Singleton fun provideStaffDao(db: GymDatabase): StaffDao = db.staffDao()
    @Provides @Singleton fun provideOrderDao(db: GymDatabase): OrderDao = db.orderDao()
    @Provides @Singleton fun provideMeasurementDao(db: GymDatabase): MeasurementDao = db.measurementDao()
    @Provides @Singleton fun provideMemberPackageDao(db: GymDatabase): MemberPackageDao = db.memberPackageDao()
    @Provides @Singleton fun provideMultiSportRateDao(db: GymDatabase): MultiSportRateDao = db.multiSportRateDao()
    @Provides @Singleton fun provideInstallmentCommissionDao(db: GymDatabase): InstallmentCommissionDao = db.installmentCommissionDao()
    @Provides @Singleton fun providePostureCommentDao(db: GymDatabase): PostureCommentDao = db.postureCommentDao()
}
