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

/**
 * v4 → v5: `staff.password` kolonu kaldırıldı.
 *
 * Kolon düz metin şifre tutuyordu ve kimlik doğrulama Supabase Auth'a
 * taşındığından beri **hiçbir yerde okunmuyordu**. Daha kötüsü, personel
 * ekranındaki alan hâlâ doldurulabiliyordu: salon sahibi personele şifre
 * belirlediğini sanıyor, o şifreyle hiç kimse giriş yapamıyordu. Yani kolon
 * yalnızca ölü değil, yanıltıcıydı.
 *
 * `DROP COLUMN` bilinçli — tabloyu yeniden kurup kopyalamak yerine.
 * Yeniden kurma deseni Room'un beklediği şemayı elle yeniden üretmeyi
 * gerektiriyor (kolon sırası, tipler, indeks adları); bir harf sapması
 * cihazda "Migration didn't properly handle" ile açılışta çökme demek.
 * `DROP COLUMN` ise kalan her şeyi Room'un kurduğu hâliyle bırakıyor,
 * dolayısıyla sonuç tam olarak "v4 eksi bu kolon" oluyor.
 *
 * SQLite 3.35 gerektiriyor; gömülü sürücü kullanıldığı için sürüm cihazdan
 * cihaza değişmiyor (bkz. `DatabaseFactory`) — sistem SQLite'ı kullanılsaydı
 * eski cihazlarda bu ifade çalışmazdı.
 *
 * Veri kaybı yok: silinen tek şey zaten kullanılmayan şifre alanı.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `staff` DROP COLUMN `password`")
    }
}

/**
 * v5 → v6: `sync_outbox.revision` kolonu eklendi.
 *
 * Kuyruğun "gönderim sürerken satır tekrar değişti" koruması yoktu.
 * `SyncEngine` bunu `enqueuedAtMs`in tazelenmesine dayandırıyordu, ama kuyruğa
 * alma çakışmada eski kaydı **koruyor** (ve korumalı: FIFO sırası o damgaya
 * dayanıyor, tazelenseydi sık düzenlenen satır kuyruğun sonuna atılıp aç
 * kalırdı). İki KDoc birbiriyle çelişiyordu; `IGNORE` kazanıyor ve koşul her
 * zaman eşleşiyordu.
 *
 * Sonucu sessiz veri kaybıydı: gönderim penceresinde yapılan ikinci değişiklik
 * kuyruktan düşüyor, kullanıcı "eşitlendi" görüyor, iki cihaz kalıcı olarak
 * ayrışıyordu.
 *
 * Ayrı bir sayaç bu yüzden gerekli: `enqueuedAtMs` sıra için sabit kalıyor,
 * `revision` ise değişikliği sayıyor.
 *
 * `DEFAULT 0` bilinçli: kuyrukta bekleyen mevcut kayıtlar sıfırdan başlıyor ve
 * ilk gönderimlerinde normal biçimde düşüyorlar. Geçiş anında gönderim
 * sürmediği için ara durum oluşmuyor.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `sync_outbox` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/** Sürüm sırasına göre uygulanacak geçişler. */
internal val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
