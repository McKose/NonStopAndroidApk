package com.gymapp.data.local.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Veritabanı dosyasının adı; iki platformda da aynı. */
internal const val GYM_DATABASE_NAME = "gym_database.db"

/**
 * Kurulumun platformdan bağımsız kısmı.
 *
 * Dosyanın **nerede** açılacağı platforma göre değişir (Android'de uygulama
 * veritabanı dizini, iOS'ta Documents); geri kalan her şey — sürücü, sorgu
 * bağlamı, geçiş disiplini — ortak.
 *
 * Gömülü SQLite sürücüsü bilinçli tercih: sistem SQLite sürümü cihazdan cihaza
 * değişiyor ve aynı sorgu farklı davranabiliyor. Gömülü sürücüde iki platform da
 * birebir aynı motoru çalıştırır.
 *
 * `fallbackToDestructiveMigration` **yok**: yazılmayı unutulan bir migration
 * hata vermek yerine sessizce tüm veriyi silerdi.
 */
internal fun RoomDatabase.Builder<GymDatabase>.buildGymDatabase(): GymDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
