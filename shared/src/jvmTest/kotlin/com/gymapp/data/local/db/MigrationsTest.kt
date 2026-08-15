package com.gymapp.data.local.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Geçişlerin gerçek SQLite üzerinde sınanması.
 *
 * Geçiş hatası bu projede yapılabilecek en pahalı hatalardan biri: kullanıcının
 * telefonundaki veriyi bozar ya da uygulamayı açılışta düşürür ve geri dönüşü
 * yoktur. `fallbackToDestructiveMigration` bilinçli olarak kapalı olduğu için
 * yanlış bir geçiş sessizce veriyi silmez — ama açılışta çöker.
 *
 * ### Eski şema ELLE YAZILMIYOR
 * Başlangıç tablosu, Room'un dışa aktardığı `shared/schemas` altındaki sürüm
 * dosyasından okunuyor. İlk yazımda tablo tanımı elle kopyalanmıştı ve bu
 * sessiz bir risk: elle yazılan tanım gerçeğinden saparsa test, üretimde hiç
 * var olmayan bir şema üzerinde koşar ve geçişi doğrulamış gibi görünür.
 * (O ilk hâlin gerçekten doğru olduğu sonradan karşılaştırılarak teyit edildi,
 * ama doğruluğu şansa bağlıydı.)
 *
 * ### Neyi hâlâ kapsamıyor
 * Room'un kendi şema doğrulamasını. Onun için `MigrationTestHelper` gerekiyor
 * ve o araç hem başlangıç hem HEDEF sürümün şema dosyasını istiyor; hedef
 * sürümünki henüz depoda değil, derleme sırasında üretiliyor. Bu geçişte açık
 * küçük tutuldu: tablo yeniden kurulmuyor, tek bir `DROP COLUMN` yapılıyor,
 * dolayısıyla sonuç tam olarak "eski şema eksi bir kolon".
 */
class MigrationsTest {

    /**
     * v4 → v5 şifre kolonunu siliyor, kalan her şeye dokunmuyor.
     *
     * Üç iddia birlikte anlamlı. Yalnızca "kolon gitti" denseydi tabloyu
     * boşaltan ya da yeniden kuran bir geçiş de testi geçerdi.
     */
    @Test
    fun `v4 to v5 sifre kolonunu siler, kalan sema ve veri korunur`() = sqliteIle { baglanti ->
        val v4 = surumSemasi(4)
        val staff = v4.tablo("staff")

        baglanti.execSQL(staff.createSql)
        staff.indeksSqlleri.forEach { baglanti.execSQL(it) }

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

        // 1) Kolon listesi: şemadan türetiliyor, elle sayılmıyor. Böylece
        //    tabloya yeni bir kolon eklendiğinde bu test kendiliğinden güncel
        //    kalıyor.
        assertEquals(
            staff.kolonlar - "password",
            kolonlar(baglanti),
            "DROP COLUMN yalnızca şifreyi kaldırmalı; kolon sırası da korunmalı",
        )
        assertFalse("password" in kolonlar(baglanti), "Şifre kolonu silinmeliydi")

        // 2) İndeksler ayakta mı? Tabloyu yeniden kuran bir geçiş bunları
        //    sessizce düşürürdü ve sonuç, sorguları yavaşlatan ama hiçbir
        //    testi düşürmeyen bir şema olurdu. Ayrıca `staff_authUserId`
        //    TEKİL: düşerse aynı hesap iki personele bağlanabilir hâle gelir.
        assertEquals(
            staff.indeksAdlari.sorted(),
            indeksler(baglanti).sorted(),
            "Geçişten sonra indeksler kaybolmuş",
        )

        // 3) Satır ve içeriği yerinde mi?
        assertEquals(
            listOf("st-1", "Ayşe Yılmaz", "4000", "auth-1"),
            tekSatir(
                baglanti,
                "SELECT `id`, `fullName`, `commissionBasisPoints`, `authUserId` FROM `staff`",
            ),
        )
    }

    /**
     * Şifre kolonuna yazılmış veri gerçekten sorgulanamaz hâle geliyor.
     *
     * Kolon "gizlenip" veri yerinde kalsaydı, düz metin şifreyi kaldırma
     * amacının yarısı boşa giderdi.
     */
    @Test
    fun `silinen sifre artik sorgulanamiyor`() = sqliteIle { baglanti ->
        val staff = surumSemasi(4).tablo("staff")
        baglanti.execSQL(staff.createSql)
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
            val ifade = baglanti.prepare("SELECT `password` FROM `staff`")
            try {
                ifade.step()
            } finally {
                ifade.close()
            }
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

    // ─── Şema dosyasının okunması ───────────────────────────────────────────

    private class TabloSemasi(private val ham: kotlinx.serialization.json.JsonObject) {
        val createSql: String
            get() = ham.getValue("createSql").jsonPrimitive.content
                .replace("\${TABLE_NAME}", ham.getValue("tableName").jsonPrimitive.content)

        val kolonlar: List<String>
            get() = ham.getValue("fields").jsonArray
                .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }

        private val indeksler
            get() = ham["indices"]?.jsonArray.orEmpty()

        val indeksAdlari: List<String>
            get() = indeksler.map { it.jsonObject.getValue("name").jsonPrimitive.content }

        val indeksSqlleri: List<String>
            get() = indeksler.map {
                it.jsonObject.getValue("createSql").jsonPrimitive.content
                    .replace("\${TABLE_NAME}", ham.getValue("tableName").jsonPrimitive.content)
            }
    }

    private class Sema(private val entities: List<kotlinx.serialization.json.JsonObject>) {
        fun tablo(ad: String): TabloSemasi {
            val bulunan = entities.firstOrNull {
                it.getValue("tableName").jsonPrimitive.content == ad
            } ?: fail(
                "Şemada '$ad' tablosu yok. Bulunanlar: " +
                    entities.map { it.getValue("tableName").jsonPrimitive.content }
            )
            return TabloSemasi(bulunan)
        }
    }

    /**
     * Room'un dışa aktardığı sürüm şemasını okur.
     *
     * Dosya yoksa test **hata veriyor**, sessizce atlanmıyor: atlanan bir geçiş
     * testi, hiç olmayan bir testtir ve takım yine yeşil kalırdı.
     */
    private fun surumSemasi(surum: Int): Sema {
        val dosya = semaDosyasi(surum)
        val kok = Json.parseToJsonElement(dosya.readText()).jsonObject
        val db = kok.getValue("database").jsonObject
        assertEquals(
            surum, db.getValue("version").jsonPrimitive.content.toInt(),
            "Şema dosyası beklenen sürümü taşımıyor: ${dosya.path}",
        )
        return Sema(db.getValue("entities").jsonArray.map { it.jsonObject })
    }

    private fun semaDosyasi(surum: Int): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val semalar = File(dir, "shared/schemas")
            if (semalar.isDirectory) {
                // Alt dizin adı veritabanı sınıfının tam adı; aramak, o adı
                // teste sabitlemekten sağlam.
                val bulunan = semalar.walkTopDown().firstOrNull { it.name == "$surum.json" }
                return bulunan ?: fail(
                    "shared/schemas altında $surum.json yok. Bulunanlar: " +
                        semalar.walkTopDown().filter { it.extension == "json" }
                            .map { it.name }.toList()
                )
            }
            dir = dir.parentFile
        }
        fail("shared/schemas bulunamadı (arama başlangıcı: ${File(".").absolutePath})")
    }

    // ─── SQLite yardımcıları ────────────────────────────────────────────────

    private fun kolonlar(baglanti: SQLiteConnection): List<String> {
        val ifade = baglanti.prepare("PRAGMA table_info(`staff`)")
        try {
            return buildList {
                // PRAGMA table_info sütunları: 0=cid, 1=name, 2=type, ...
                while (ifade.step()) add(ifade.getText(1))
            }
        } finally {
            ifade.close()
        }
    }

    private fun indeksler(baglanti: SQLiteConnection): List<String> {
        val ifade = baglanti.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'staff' " +
                "AND name NOT LIKE 'sqlite_%'"
        )
        try {
            return buildList {
                while (ifade.step()) add(ifade.getText(0))
            }
        } finally {
            ifade.close()
        }
    }

    private fun tekSatir(baglanti: SQLiteConnection, sql: String): List<String> {
        val ifade = baglanti.prepare(sql)
        try {
            assertTrue(ifade.step(), "Satır bulunamadı")
            return (0 until ifade.getColumnCount()).map { ifade.getText(it) }
        } finally {
            ifade.close()
        }
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
