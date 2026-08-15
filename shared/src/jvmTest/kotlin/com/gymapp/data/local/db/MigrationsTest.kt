package com.gymapp.data.local.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Geçişlerin gerçek SQLite üzerinde sınanması.
 *
 * Geçiş hatası, bu projede yapılabilecek en pahalı hatalardan biri: kullanıcının
 * telefonundaki veriyi bozar ya da uygulamayı açılışta düşürür ve geri dönüşü
 * yoktur. `fallbackToDestructiveMigration` bilinçli olarak kapalı olduğu için
 * yanlış bir geçiş sessizce veriyi silmez — ama açılışta çöker.
 *
 * ### Neyi kapsıyor
 * Geçişin **SQL etkisi**: hangi kolon gitti, hangi veri kaldı. Gerçek SQLite
 * üzerinde koşuyor, taklit değil.
 *
 * ### Neyi KAPSAMIYOR
 * Room'un şema doğrulamasını. Room bir veritabanını açarken gerçek tablo
 * yapısını beklediğiyle karşılaştırıyor ve uyuşmazsa hata veriyor. Onu sınamak
 * için Room'un `MigrationTestHelper` aracı ve **dışa aktarılmış şema
 * dosyaları** (`shared/schemas/*.json`) gerekiyor; o dosyalar şu an depoda
 * değil, yalnızca CI yapıtı olarak üretiliyor.
 *
 * Bu boşluk `MIGRATION_4_5` için bilinçli olarak küçük tutuldu: geçiş tabloyu
 * yeniden kurmuyor, tek bir `DROP COLUMN` yapıyor. Kalan her şey Room'un
 * kurduğu hâliyle duruyor, dolayısıyla sonuç tam olarak "v4 eksi bu kolon".
 * Tabloyu yeniden kuran bir geçiş yazılacaksa **önce şemalar depoya alınmalı**;
 * orada kolon sırası ya da indeks adı sapması gerçek bir risk.
 */
class MigrationsTest {

    /**
     * v4 → v5 şifre kolonunu siliyor, kalan veriye dokunmuyor.
     *
     * İki iddia birlikte anlamlı: yalnızca "kolon gitti" denseydi tabloyu
     * boşaltan bir geçiş de testi geçerdi.
     */
    @Test
    fun `v4 to v5 sifre kolonunu siler, veriyi korur`() = sqliteIle { baglanti ->
        v4StaffTablosuKur(baglanti)

        baglanti.execSQL(
            """
            INSERT INTO `staff`
                (`id`, `tenantId`, `fullName`, `title`, `role`, `branch`,
                 `commissionBasisPoints`, `monthlySalaryMinor`, `phone`,
                 `nickname`, `authUserId`, `password`, `isActive`,
                 `createdAtMs`, `updatedAtMs`, `deletedAtMs`)
            VALUES
                ('st-1', 'salon-1', 'Ayşe Yılmaz', 'Eğitmen', 'TRAINER', 'Fitness',
                 4000, 3000000, '+905321112233', 'ayse', 'auth-1', 'gizli-sifre', 1,
                 100, 200, NULL)
            """.trimIndent()
        )

        assertTrue("password" in kolonlar(baglanti), "Kurgu bozuk: v4'te kolon olmalıydı")

        MIGRATION_4_5.migrate(baglanti)

        val sonrasi = kolonlar(baglanti)
        assertFalse("password" in sonrasi, "Şifre kolonu silinmeliydi")

        // Kalan kolonların hepsi duruyor mu? Yalnızca şifrenin gitmesi gerekiyor.
        assertEquals(
            listOf(
                "id", "tenantId", "fullName", "title", "role", "branch",
                "commissionBasisPoints", "monthlySalaryMinor", "phone",
                "nickname", "authUserId", "isActive",
                "createdAtMs", "updatedAtMs", "deletedAtMs",
            ),
            sonrasi,
            "DROP COLUMN yalnızca şifreyi kaldırmalı; kolon sırası da korunmalı",
        )

        // Satır ve içeriği yerinde mi?
        val satir = tekSatir(baglanti, "SELECT `id`, `fullName`, `commissionBasisPoints`, `authUserId` FROM `staff`")
        assertEquals(listOf("st-1", "Ayşe Yılmaz", "4000", "auth-1"), satir)
    }

    /**
     * Şifre kolonuna yazılmış veri gerçekten diskten gidiyor.
     *
     * `DROP COLUMN` kolonu tablo tanımından çıkarıyor; bu testin sorduğu şey
     * değerin sorgulanamaz hâle gelip gelmediği. Kolon "gizlenip" veri yerinde
     * kalsaydı, düz metin şifreyi kaldırma amacının yarısı boşa giderdi.
     */
    @Test
    fun `silinen sifre artik sorgulanamiyor`() = sqliteIle { baglanti ->
        v4StaffTablosuKur(baglanti)
        baglanti.execSQL(
            """
            INSERT INTO `staff`
                (`id`, `tenantId`, `fullName`, `title`, `role`, `branch`,
                 `commissionBasisPoints`, `monthlySalaryMinor`, `phone`,
                 `nickname`, `authUserId`, `password`, `isActive`,
                 `createdAtMs`, `updatedAtMs`, `deletedAtMs`)
            VALUES ('st-2', 'salon-1', 'B', 'E', 'TRAINER', 'F', 0, 0, '+9050', 'b',
                    NULL, 'cok-gizli', 1, 1, 1, NULL)
            """.trimIndent()
        )

        MIGRATION_4_5.migrate(baglanti)

        val hata = runCatching {
            baglanti.prepare("SELECT `password` FROM `staff`").use { it.step() }
        }.exceptionOrNull()

        assertTrue(hata != null, "Silinen kolon hâlâ sorgulanabiliyor")
    }

    /** Geçiş listesi sürüm sırasına göre ve boşluksuz olmalı. */
    @Test
    fun `gecis zinciri kesintisiz`() {
        val zincir = ALL_MIGRATIONS.sortedBy { it.startVersion }
        zincir.forEachIndexed { i, gecis ->
            assertEquals(
                gecis.startVersion + 1, gecis.endVersion,
                "Geçişler tek sürüm atlamalı: ${gecis.startVersion}→${gecis.endVersion}",
            )
            if (i > 0) {
                assertEquals(
                    zincir[i - 1].endVersion, gecis.startVersion,
                    "Sürüm zincirinde boşluk var — atlanan sürümdeki cihaz açılışta çöker",
                )
            }
        }
        // Zincirin sonu veritabanının bugünkü sürümüne varmalı. Varmazsa,
        // sürümü artırıp geçişi yazmayı unutmuşuz demektir.
        assertEquals(
            5, zincir.last().endVersion,
            "Son geçiş, @Database(version = ...) değerine varmalı",
        )
    }

    // ─── Yardımcılar ────────────────────────────────────────────────────────

    /**
     * v4'teki `staff` tablosunun tanımı.
     *
     * Room'un ürettiği DDL ile aynı olacak şekilde elle yazıldı. Şema dosyaları
     * depoda olsaydı buna gerek kalmazdı (bkz. sınıf açıklaması).
     */
    private fun v4StaffTablosuKur(baglanti: SQLiteConnection) {
        baglanti.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `staff` (
                `id` TEXT NOT NULL,
                `tenantId` TEXT NOT NULL,
                `fullName` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `branch` TEXT NOT NULL,
                `commissionBasisPoints` INTEGER NOT NULL,
                `monthlySalaryMinor` INTEGER NOT NULL,
                `phone` TEXT NOT NULL,
                `nickname` TEXT NOT NULL,
                `authUserId` TEXT,
                `password` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAtMs` INTEGER NOT NULL,
                `updatedAtMs` INTEGER NOT NULL,
                `deletedAtMs` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }

    private fun kolonlar(baglanti: SQLiteConnection): List<String> =
        baglanti.prepare("PRAGMA table_info(`staff`)").use { ifade ->
            buildList {
                while (ifade.step()) add(ifade.getText(1))
            }
        }

    private fun tekSatir(baglanti: SQLiteConnection, sql: String): List<String> =
        baglanti.prepare(sql).use { ifade ->
            assertTrue(ifade.step(), "Satır bulunamadı")
            (0 until ifade.getColumnCount()).map { ifade.getText(it) }
        }

    /** Bellek içi veritabanı açar, iş bitince kapatır. */
    private fun sqliteIle(govde: (SQLiteConnection) -> Unit) {
        val baglanti = BundledSQLiteDriver().open(":memory:")
        try {
            govde(baglanti)
        } finally {
            baglanti.close()
        }
    }
}
