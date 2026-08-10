package com.gymapp.data.local.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.gymapp.data.local.dao.*
import com.gymapp.data.local.entity.*

/**
 * Uygulama veritabanı.
 *
 * **Şema sıfırlandı (v1).** Entity cutover'ı expand/contract yöntemiyle adım adım
 * yapıldı (v8–v14) ve her ara sürüm `fallbackToDestructiveMigration()` ile
 * geçiliyordu; uygulama henüz kullanımda olmadığı için bu güvenliydi. Cutover
 * tamamlandığına göre o ara sürümlerin bir değeri kalmadı: sürüm 1'e alındı,
 * eski şema anlık görüntüleri silindi ve yıkıcı geçiş kapatıldı.
 *
 * **Buradan sonrası katı migration disiplini**: her şema değişikliği bir sürüm
 * artışı ve elle yazılmış bir [androidx.room.migration.Migration] gerektirir.
 * `exportSchema = true` olduğu için her sürümün anlık görüntüsü `shared/schemas`
 * altında depoya işlenir ve geçişler test edilebilir.
 *
 * **v2**: gönderim kuyruğu (`sync_outbox`) eklendi — bkz. [MIGRATION_1_2].
 *
 * Tüm tablolar ortak biçimde:
 *  - **UUID birincil anahtar** — `autoGenerate` `Long` anahtarlar çok cihazlı
 *    senkronizasyonda çakışır (iki cihaz çevrimdışıyken aynı `id`'yi üretir).
 *  - **`tenantId`** — hesap/salon izolasyonu; web admin tarafındaki satır bazlı
 *    güvenlik politikası bunun üzerine kurulacak.
 *  - **`createdAtMs` / `updatedAtMs` / `deletedAtMs`** — delta senkronizasyon ve
 *    tombstone silme.
 *  - **Para kuruş cinsinden `Long`** — `Double` toplamlarda sapma yaratıyordu.
 *  - **Enum kolonlar** — serbest metin yerine tip güvenli değerler.
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
        // Append-only tablolar: düzeltme silme/güncelleme ile değil ters kayıtla yapılır.
        LedgerEntryEntity::class,
        StockMovementEntity::class,
        // Gönderim kuyruğu: yerel bir tablo, sunucuya kendisi senkronize edilmez.
        SyncOutboxEntity::class,
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(GymDatabaseConstructor::class)
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
    abstract fun syncOutboxDao(): SyncOutboxDao
}

/**
 * Room'un her platform için ürettiği örnekleme noktası.
 *
 * `actual` gövdeyi Room'un kod üreticisi yazar; burada yalnızca beklendiği
 * bildiriliyor. Ortak kodda `GymDatabase::class.java` gibi JVM'e özgü bir
 * yansıma (reflection) çağrısı yapılamayacağı için KMP'de bu yol izleniyor.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object GymDatabaseConstructor : RoomDatabaseConstructor<GymDatabase> {
    override fun initialize(): GymDatabase
}

// KALDIRILDI: `getInstance()` companion singleton'ı, DI'nin sağladığıyla aynı isimli
// ("gym_database") ikinci bir Room örneği kurabiliyordu. İki örnek aynı dosyayı açtığında
// invalidation tracker kopar (Flow'lar güncellenmez) ve yazma kilitleri çakışır.
// Veritabanının tek kaynağı platform tarafındaki tekil kurulum.
