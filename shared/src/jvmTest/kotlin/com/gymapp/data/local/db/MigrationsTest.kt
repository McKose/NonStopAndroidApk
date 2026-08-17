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
 * ### Hedef şema da artık depoda
 * Geçişin sonucu `4.json`'dan türetilen bir beklentiyle değil, doğrudan
 * `5.json` ile karşılaştırılıyor. Fark önemli: türetilen beklenti yalnızca
 * geçişin *benim kafamdaki* sonucu ürettiğini gösterir. Room'un v5'te gerçekte
 * ne beklediğini ise ancak hedef şema söyleyebilir — uyuşmazsa uygulama
 * açılışta "Migration didn't properly handle" diye çöker ve bu, cihazdaki
 * veriyle birlikte gelen, geri dönüşü olmayan bir hatadır.
 *
 * ### Her geçişin hedef şeması artık depoda
 * `4.json`, `5.json` ve `6.json` işlenmiş durumda, yani her geçiş **hedef**
 * sürümün gerçek şemasıyla karşılaştırılıyor; hiçbiri türetilen bir beklentiye
 * dayanmıyor. Dosyanın depoda kalmaya devam etmesi CI'da sınanıyor: derleme
 * `@Database` sürümünün JSON'unu üretiyor ve işlenmemişse iş düşüyor. Önceden
 * bu sessizdi — sürüm artırılır, hiçbir şey şikâyet etmez ve eksiklik ancak
 * geçiş testi yazılmaya çalışılınca ortaya çıkardı (`6.json` tam bu yüzden
 * aylarca eksik kaldı).
 *
 * ### Neyi hâlâ kapsamıyor
 * Room'un `MigrationTestHelper` aracını. Kapsamadığı tek şey Room'un kendi
 * doğrulama koduyla aynı yoldan geçmek; buradaki iddialar aynı soruları
 * (kolonlar, indeksler, veri) doğrudan SQLite'a sorarak yanıtlıyor — üstelik
 * daha açık biçimde: hangi kolonun neden beklendiği tek tek yazıyor.
 *
 * `MigrationTestHelper`ın ekleyeceği tek şey Room'un `TableInfo`
 * karşılaştırmasını birebir kullanmak olurdu. Bunun bir farkı var ve burada
 * kayıtlı: Room kolonları **ada göre** doğruluyor, sıraya bakmıyor. v5→v6
 * testi tam bu ayrımın üzerinde duruyor.
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

        // 1) Sonuç, HEDEF sürümün şemasıyla birebir aynı olmalı.
        //
        //    Beklenti artık "v4 eksi password" diye hesaplanmıyor, `5.json`'dan
        //    okunuyor. Fark önemli: hesaplanan beklenti yalnızca geçişin benim
        //    kafamdaki sonucu ürettiğini gösterirdi. Room'un v5'te gerçekte ne
        //    beklediğini ise ancak hedef şema söyleyebilir — ve uyuşmazsa
        //    uygulama açılışta "Migration didn't properly handle" diye çöker.
        val v5Staff = surumSemasi(5).tablo("staff")
        assertEquals(
            v5Staff.kolonlar,
            kolonlar(baglanti),
            "Geçişin sonucu v5 şemasıyla aynı olmalı; kolon sırası dahil",
        )
        assertFalse("password" in kolonlar(baglanti), "Şifre kolonu silinmeliydi")

        // Kurgunun gerçekten bir şey sınadığının kontrolü: iki şema aynı
        // olsaydı yukarıdaki iddia boş olurdu.
        assertEquals(
            staff.kolonlar - "password", v5Staff.kolonlar,
            "v4 ile v5 arasındaki tek fark şifre kolonu olmalıydı",
        )

        // 2) İndeksler ayakta mı? Tabloyu yeniden kuran bir geçiş bunları
        //    sessizce düşürürdü ve sonuç, sorguları yavaşlatan ama hiçbir
        //    testi düşürmeyen bir şema olurdu. Ayrıca `staff_authUserId`
        //    TEKİL: düşerse aynı hesap iki personele bağlanabilir hâle gelir.
        assertEquals(
            v5Staff.indeksAdlari.sorted(),
            indeksler(baglanti).sorted(),
            "Geçişten sonra indeksler v5'teki hâliyle ayakta olmalı",
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
            6, zincir.last().endVersion,
            "Son geçiş, @Database(version = ...) değerine varmalı",
        )
    }

    /**
     * v5 → v6 kuyruğa `revision` kolonunu ekliyor, bekleyen kayıtları koruyor.
     *
     * Kolonun kendisi sessiz bir veri kaybını kapatıyor: gönderim sürerken satır
     * tekrar değiştiğinde kaydın kuyrukta kalmasını sağlayan ayırt edici bu.
     * Önceden bu iş `enqueuedAtMs`e yükleniyordu ama o damga bilinçli olarak
     * sabit (FIFO sırası ona dayanıyor), yani koruma hiç çalışmıyordu.
     *
     * Sonuç artık `6.json` ile karşılaştırılıyor — v4→v5 testinin `5.json`'a
     * bağlanmasıyla aynı sebep: türetilen bir beklenti yalnızca geçişin *benim
     * kafamdaki* sonucu ürettiğini gösterir, Room'un v6'da gerçekte ne
     * beklediğini ise ancak hedef şema söyleyebilir.
     *
     * ### Kolon SIRASI bilinçli olarak sıralı karşılaştırılmıyor
     * `ALTER TABLE ... ADD COLUMN` yeni kolonu tablonun **sonuna** koyuyor;
     * `6.json` ise `revision`'ı alan tanımındaki yerinde, `enqueuedAtMs` ile
     * `attemptCount` arasında listeliyor. Yani fiziksel sıra ile şemadaki sıra
     * ayrı — ve bu **sorun değil**, çünkü Room tabloyu ada göre doğruluyor
     * (`TableInfo` kolonları bir ada göre eşlenmiş küme; sıra eşitliğe girmiyor).
     *
     * Bu yüzden burada iki ayrı iddia var: kolon **kümesi** hedef şemayla aynı
     * olmalı (Room'un baktığı şey), fiziksel sıra ise "v5 sırası artı sona
     * eklenen kolon" olmalı (`ALTER TABLE`ın yaptığı şey). v4→v5 testinde sıra
     * birebir karşılaştırılabiliyor çünkü o geçiş tabloyu yeniden kuruyor.
     */
    @Test
    fun `v5 to v6 kuyruga revision ekler, bekleyen kayitlar korunur`() = sqliteIle { baglanti ->
        val v5 = surumSemasi(5)
        val kuyruk = v5.tablo("sync_outbox")

        baglanti.execSQL(kuyruk.createSql)
        kuyruk.indeksSqlleri.forEach { baglanti.execSQL(it) }

        baglanti.execSQL(
            """
            INSERT INTO `sync_outbox`
                (`id`, `tenantId`, `entityTable`, `entityId`, `enqueuedAtMs`,
                 `attemptCount`, `lastAttemptAtMs`, `lastError`)
            VALUES
                ('o-1', 'salon-1', 'gym_members', 'm-1', 1700, 2, 1800, 'ağ yok')
            """.trimIndent()
        )

        MIGRATION_5_6.migrate(baglanti)

        val v6Kuyruk = surumSemasi(6).tablo("sync_outbox")
        val sonucKolonlar = kolonlar(baglanti, "sync_outbox")

        // 1a) Kolon kümesi HEDEF şemayla aynı olmalı — Room'un doğruladığı şey bu.
        assertEquals(
            v6Kuyruk.kolonlar.sorted(),
            sonucKolonlar.sorted(),
            "Geçişin sonucu v6 şemasındaki kolonları vermeli",
        )

        // 1b) Fiziksel sıra: `ALTER TABLE` sona ekler. Şemadaki sırayla
        //     ayrışması beklenen ve zararsız bir fark (bkz. testin belgesi);
        //     ama ayrışmanın **bu** biçimde olduğu sabitleniyor: tabloyu
        //     yeniden kuran bir geçişe dönerse burası uyarır.
        assertEquals(
            kuyruk.kolonlar + "revision",
            sonucKolonlar,
            "`ALTER TABLE ADD COLUMN` kolonu sona eklemeli",
        )

        // 1c) Kurgunun gerçekten bir şey sınadığının kontrolü — iki ŞEMA
        //     dosyası karşılaştırılıyor: aralarındaki tek fark `revision`
        //     olmalı. Aynı olsalardı yukarıdaki iddialar boş kalırdı.
        assertFalse("revision" in kuyruk.kolonlar, "Kurgu bozuk: v5'te kolon olmamalıydı")
        assertEquals(
            (kuyruk.kolonlar + "revision").sorted(),
            v6Kuyruk.kolonlar.sorted(),
            "v5 ile v6 arasındaki tek fark `revision` olmalıydı",
        )

        // 2) Bekleyen kayıt duruyor ve alanları bozulmamış. Kuyruk kaydını
        //    kaybetmek, o satırın sunucuya hiç gitmemesi demek olurdu.
        assertEquals(
            listOf("o-1", "gym_members", "m-1", "1700", "2"),
            tekSatir(
                baglanti,
                "SELECT `id`, `entityTable`, `entityId`, `enqueuedAtMs`, `attemptCount` " +
                    "FROM `sync_outbox`",
            ),
        )

        // 3) Mevcut kayıtlar sıfırdan başlıyor: ilk gönderimlerinde normal
        //    biçimde düşsünler.
        assertEquals(
            listOf("0"),
            tekSatir(baglanti, "SELECT `revision` FROM `sync_outbox`"),
        )

        // 4) İndeksler ayakta. `(tenantId, entityTable, entityId)` TEKİL ve
        //    kuyruğa alma tam ona dayanıyor: düşerse aynı satır için birden çok
        //    kayıt birikir ve revizyon sayacı anlamsızlaşır.
        assertEquals(
            v6Kuyruk.indeksAdlari.sorted(),
            indeksler(baglanti, "sync_outbox").sorted(),
            "Geçişten sonra kuyruk indeksleri v6'daki hâliyle ayakta olmalı",
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

    private fun kolonlar(baglanti: SQLiteConnection, tablo: String = "staff"): List<String> {
        val ifade = baglanti.prepare("PRAGMA table_info(`$tablo`)")
        try {
            return buildList {
                // PRAGMA table_info sütunları: 0=cid, 1=name, 2=type, ...
                while (ifade.step()) add(ifade.getText(1))
            }
        } finally {
            ifade.close()
        }
    }

    private fun indeksler(baglanti: SQLiteConnection, tablo: String = "staff"): List<String> {
        val ifade = baglanti.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$tablo' " +
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
