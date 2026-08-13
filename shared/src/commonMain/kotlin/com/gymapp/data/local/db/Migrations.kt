package com.gymapp.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2: gönderim kuyruğu tablosu eklendi.
 *
 * Veri taşımıyor, yalnızca yeni bir tablo ve indeksleri açıyor — mevcut satırlar
 * etkilenmiyor. Kuyruk boş başlıyor: geçiş anında var olan satırlar sunucuya
 * gönderilmemiş sayılmıyor, çünkü senkronizasyonun kendisi henüz yok. İlk
 * gönderim akışı devreye girdiğinde başlangıç yüklemesi ayrı ele alınacak.
 *
 * DDL, Room'un aynı entity için üreteceği biçimle birebir yazıldı (kolon sırası,
 * NOT NULL'lar, indeks adları). Room veritabanını açarken beklediği şemayla
 * karşılaştırıp uyuşmazlıkta hata veriyor.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
                `id` TEXT NOT NULL,
                `tenantId` TEXT NOT NULL,
                `entityTable` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `enqueuedAtMs` INTEGER NOT NULL,
                `attemptCount` INTEGER NOT NULL,
                `lastAttemptAtMs` INTEGER,
                `lastError` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_sync_outbox_tenantId_entityTable_entityId` " +
                "ON `sync_outbox` (`tenantId`, `entityTable`, `entityId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_sync_outbox_tenantId_enqueuedAtMs` " +
                "ON `sync_outbox` (`tenantId`, `enqueuedAtMs`)"
        )
    }
}

/**
 * v2 → v3: personel satırına Supabase kullanıcı kimliği eklendi.
 *
 * Kimlik doğrulama Supabase Auth'a taşınınca giriş yapan kişiden elde edilen tek
 * kimlik `auth.users.id` oluyor. Uygulamanın buna ihtiyacı var çünkü randevu ve
 * hakediş kayıtları yerel `staff.id` değerine bakıyor: iki kimlik arasında bir
 * köprü olmadan "bugün benim derslerim" sorusu yanıtlanamaz.
 *
 * Kolon **nullable**: mevcut personel satırlarının henüz bir Supabase hesabı
 * olmayabilir ve bağlanana kadar uygulamanın çalışması gerekiyor. Zorunlu
 * yapmak, geçişte var olan her satır için uydurma bir değer yazmak demek olurdu.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `staff` ADD COLUMN `authUserId` TEXT")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_staff_authUserId` ON `staff` (`authUserId`)"
        )
    }
}

/**
 * v3 → v4: çekme su işaretleri tablosu eklendi.
 *
 * Veri taşımıyor. Tablo boş başlıyor ve bu doğru: su işareti yoksa her tablo
 * baştan okunuyor, yani ilk çekmede sunucudaki her satır iniyor. Yazma üzerine
 * yazdığı için tekrar inen satırlar zararsız.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_pull_state` (
                `tenantId` TEXT NOT NULL,
                `entityTable` TEXT NOT NULL,
                `lastPulledAtMs` INTEGER NOT NULL,
                PRIMARY KEY(`tenantId`, `entityTable`)
            )
            """.trimIndent()
        )
    }
}

/** Sürüm sırasına göre uygulanacak geçişler. */
internal val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
