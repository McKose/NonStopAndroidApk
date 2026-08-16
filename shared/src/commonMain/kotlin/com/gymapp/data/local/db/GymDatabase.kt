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
 * altına yazılıyor. `4.json` depoya işlendi ve `MigrationsTest` başlangıç
 * tablosunu **oradan** kuruyor — elle kopyalanan bir tanım gerçeğinden saparsa
 * test, üretimde hiç var olmayan bir şema üzerinde koşar ve geçişi doğrulamış
 * gibi görünürdü.
 *
 * Yeni bir sürüm eklendiğinde o sürümün dosyası da depoya alınmalı: derleme
 * sırasında üretiliyor, CI'da `room-schemas` yapıtı olarak indirilebiliyor.
 * Hem başlangıç hem hedef sürümün dosyası depoda olduğunda Room'un
 * `MigrationTestHelper` aracı da kullanılabilir hâle gelir; o araç geçişten
 * sonra Room'un kendi şema doğrulamasını da koşturuyor.
 *
 * **v2**: gönderim kuyruğu (`sync_outbox`) eklendi — bkz. [MIGRATION_1_2].
 *
 * **v4**: `sync_pull_state` eklendi — bkz. [MIGRATION_3_4]. Sunucudan çekmenin
 * nereye kadar ilerlediğini tabloya başına tutuyor.
 *
 * **v3**: `staff.authUserId` eklendi — bkz. [MIGRATION_2_3]. Giriş yapan
 * Supabase kullanıcısını yerel personel kaydına bağlıyor.
 *
 * **v5**: `staff.password` kaldırıldı — bkz. [MIGRATION_4_5]. Düz metin şifre
 * tutan, hiçbir yerde okunmayan ve kullanıcıyı yanıltan bir kolondu.
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
        // Çekme su işaretleri: hangi tablodan nereye kadar okunduğu.
        SyncPullStateEntity::class,
    ],
    version = 6,
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
    abstract fun syncPullStateDao(): SyncPullStateDao

    /** Çıkışta yerel veriyi silmek için; bkz. [MaintenanceDao]. */
    abstract fun maintenanceDao(): MaintenanceDao
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
