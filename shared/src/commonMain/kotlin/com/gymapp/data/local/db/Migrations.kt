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

/** Sürüm sırasına göre uygulanacak geçişler. */
internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
