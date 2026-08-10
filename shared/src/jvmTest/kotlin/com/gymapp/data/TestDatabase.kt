package com.gymapp.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gymapp.data.local.db.GymDatabase
import kotlinx.coroutines.Dispatchers

/**
 * Test için bellek içi veritabanı.
 *
 * Gerçek SQLite üzerinde koşuyor — sahte (fake) DAO değil. Bu testlerin varlık
 * sebebi tam olarak bu: düzeltilen hataların çoğu Kotlin tarafında değil **SQL
 * ve transaction semantiğinde**ydi (kategori süzgeci, atomiklik, tombstone'lar).
 * Sahte DAO'lar o katmanı hiç çalıştırmaz, dolayısıyla o hataları göremezdi —
 * nitekim `SessionQuota` testleri tam olarak böyle yanılmıştı: üretimde koşan SQL
 * kuralı test edilmezken, koşmayan Kotlin kopyasının testleri geçiyordu.
 *
 * Her test kendi veritabanını açar; paylaşılan durum yok.
 */
fun createTestDatabase(): GymDatabase =
    Room.inMemoryDatabaseBuilder<GymDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
