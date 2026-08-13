package com.gymapp.data.sync

import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.local.entity.MeasurementEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.local.entity.StockMovementEntity
import com.gymapp.domain.DeliveryStatus
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.StockMovementReason
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Gönderilen JSON'un sunucu şemasıyla birebir örtüştüğünü doğrular.
 *
 * ### Neden bu test var
 * [RowPayloads] içindeki kolon adları elle yazılmış metinler. Yanlış yazılan biri
 * derleme hatası vermez; hata ancak çalışma zamanında, PostgREST'in 400 yanıtıyla
 * ortaya çıkar. [HttpStatusMapping] bunu doğru şekilde **kalıcı** hata sayar,
 * yani kayıt kuyrukta kalır ve tur devam eder — uygulama sorunsuz çalışmaya devam
 * ederken o tablonun verisi hiçbir zaman sunucuya ulaşmaz. Kimsenin bakmadığı bir
 * `lastError` kolonu dışında hiçbir belirtisi olmaz.
 *
 * Aynı sessizlik ters yönde de geçerli: sunucu şemasına yeni bir zorunlu kolon
 * eklenip gönderime eklenmezse her gönderim reddedilir.
 *
 * Bu yüzden iddia iki taraflı ve **tam eşitlik**: gönderilen anahtar kümesi,
 * migrasyondaki kolon kümesinin aynısı olmalı. Eksik de fazla da hata.
 *
 * ### Kaynak neden migrasyon dosyası
 * Beklenen kolonları teste elle yazmak, testi eşlemenin ikinci bir kopyası yapardı
 * — ikisi birlikte yanlış olabilirdi. Tek gerçek kaynak sunucuya uygulanan SQL'in
 * kendisi, o yüzden test onu okuyup ayrıştırıyor.
 */
class RowPayloadColumnsTest {

    @Test
    fun `gonderilen anahtarlar migrasyondaki kolonlarla birebir ayni`() {
        val tables = parseSchema(migrationFiles())

        for (table in SyncTable.entries) {
            val columns = tables[table.tableName]
                ?: fail("Migrasyonda ${table.tableName} tablosu yok")

            val keys = samplePayload(table).keys

            assertEquals(
                columns.sorted(),
                keys.sorted(),
                "${table.tableName}: gönderilen alanlar ile sunucu kolonları uyuşmuyor.\n" +
                    "  Sunucuda olup gönderilmeyen: ${(columns - keys).sorted()}\n" +
                    "  Gönderilip sunucuda olmayan: ${(keys - columns).sorted()}",
            )
        }
    }

    /**
     * Personel şifresinin sunucuya gitmediğini ayrıca doğrular.
     *
     * Yukarıdaki eşitlik testi bunu zaten kapsıyor — sunucu tablosunda `password`
     * kolonu yok, dolayısıyla gönderilse fazlalık olarak yakalanırdı. Yine de ayrı
     * bir test olarak duruyor: biri ileride sunucuya `password` kolonu eklerse
     * eşitlik testi sessizce geçer, bu test geçmez. Korunan şey kolonların
     * uyumu değil, şifrelerin cihazda kalması kararı.
     */
    @Test
    fun `personel sifresi sunucuya gitmiyor`() {
        val keys = samplePayload(SyncTable.STAFF).keys
        assertTrue(
            keys.none { it.contains("password", ignoreCase = true) },
            "Personel gönderiminde şifre alanı var: $keys",
        )
    }

    // ─── Örnek satırlar ────────────────────────────────────────────────────
    //
    // Alanların değeri önemsiz, varlığı önemli: test yalnızca anahtar kümesine
    // bakıyor. Boş bırakılabilecek alanlar yine de dolduruluyor — eşleme bir
    // alanı `null` olduğunda atlasaydı, boş örnekle koşan bir test bunu eksik
    // kolon olarak değil "zaten yoktu" diye geçiştirebilirdi.

    private fun samplePayload(table: SyncTable) = when (table) {
        SyncTable.MEMBERS -> RowPayloads.of(
            MemberEntity(
                id = "m1", tenantId = TENANT, fullName = "Ayşe", phone = "+905001112233",
                email = "a@b.c", birthDateMs = 1, activePackageId = "p1", totalSessions = 10,
                remainingSessions = 9, startDateMs = 1, endDateMs = 2, installmentCount = 3,
                packagePriceMinor = 100, discountMinor = 10, pricePaidMinor = 90,
                paymentStatus = "PAID", paymentDateMs = 1, notes = "not",
                healthRisks = "yok", riskLevel = "LOW", healthNotes = "yok",
                createdAtMs = 1, updatedAtMs = 2, deletedAtMs = null,
            )
        )

        SyncTable.PACKAGES -> RowPayloads.of(
            PackageEntity(
                id = "p1", tenantId = TENANT, name = "Aylık", validityDays = 30,
                sessionCount = 12, basePriceMinor = 100_000,
                createdAtMs = 1, updatedAtMs = 2, deletedAtMs = null,
            )
        )

        SyncTable.PRODUCTS -> RowPayloads.of(
            ProductEntity(
                id = "pr1", tenantId = TENANT, name = "Su", category = "içecek",
                priceMinor = 1500, imageUrl = "http://x/y.png",
                createdAtMs = 1, updatedAtMs = 2, deletedAtMs = null,
            )
        )

        SyncTable.APPOINTMENTS -> RowPayloads.of(
            AppointmentEntity(
                id = "a1", tenantId = TENANT, memberId = "m1", staffId = "s1",
                startTimeMs = 1, endTimeMs = 2, sessionValueMinor = 5000,
                settledAtMs = 3, notes = "not",
                createdAtMs = 1, updatedAtMs = 2, deletedAtMs = null,
            )
        )

        SyncTable.STAFF -> RowPayloads.of(
            StaffEntity(
                id = "s1", tenantId = TENANT, fullName = "Mehmet", title = "Eğitmen",
                branch = "Fitness", commissionBasisPoints = 4000, monthlySalaryMinor = 1,
                phone = "+905001112233", nickname = "mehmet", password = "gizli",
                authUserId = "458f1383-d7ef-474b-8e16-798bde768654",
                createdAtMs = 1, updatedAtMs = 2, deletedAtMs = null,
            )
        )

        SyncTable.ORDERS -> RowPayloads.of(
            OrderEntity(
                id = "o1", tenantId = TENANT, memberId = "m1", totalPriceMinor = 100,
                discountMinor = 10, finalPriceMinor = 90, paymentMethod = PaymentMethod.CASH,
                paymentStatus = "PAID", deliveryStatus = DeliveryStatus.POST_DELIVERY,
                dateMs = 1, notes = "not",
                createdAtMs = 1, updatedAtMs = 2, deletedAtMs = null,
            )
        )

        SyncTable.MEASUREMENTS -> RowPayloads.of(
            MeasurementEntity(
                id = "me1", tenantId = TENANT, memberId = "m1", dateMs = 1,
                height = 170.0, weight = 70.0, shoulder = 1.0, chest = 2.0,
                waist = 3.0, hips = 4.0, leg = 5.0, arm = 6.0, notes = "not",
                createdAtMs = 1, updatedAtMs = 2, deletedAtMs = null,
            )
        )

        SyncTable.LEDGER_ENTRIES -> RowPayloads.of(
            LedgerEntryEntity(
                id = "l1", tenantId = TENANT, type = LedgerType.PAYMENT,
                category = LedgerCategory.MEMBERSHIP, amountMinor = 100,
                paymentMethod = PaymentMethod.CARD, memberId = "m1", staffId = "s1",
                orderId = "o1", appointmentId = "a1", description = "açıklama",
                occurredAtMs = 1, reversesId = "l0", createdAtMs = 1,
            )
        )

        SyncTable.STOCK_MOVEMENTS -> RowPayloads.of(
            StockMovementEntity(
                id = "sm1", tenantId = TENANT, productId = "pr1", quantityDelta = -1,
                reason = StockMovementReason.SALE, orderId = "o1", note = "not",
                occurredAtMs = 1, createdAtMs = 1,
            )
        )
    }

    // ─── Migrasyon dosyasının ayrıştırılması ───────────────────────────────

    /**
     * Migrasyon dosyalarını sırayla bulur.
     *
     * Tek bir dosya değil **hepsi** okunuyor: şema zamanla `alter table` ile de
     * değişiyor ve yalnızca `0002`'ye bakan bir test, sonradan eklenen bir kolonu
     * "sunucuda yok" sayardı. Dosya adları sıfır dolgulu olduğu için sözlük sırası
     * uygulama sırasıyla aynı.
     *
     * Çalışma dizinine güvenilmiyor: Gradle test görevi modül dizininde koşuyor
     * ama bu bir garanti değil ve IDE'den koşturulduğunda değişebiliyor. Yukarı
     * doğru yürüyerek aramak her iki durumda da çalışıyor.
     */
    private fun migrationFiles(): List<File> {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val migrations = File(dir, "supabase/migrations")
            if (migrations.isDirectory) {
                val files = migrations.listFiles { f -> f.extension == "sql" }
                    ?.sortedBy { it.name }
                    .orEmpty()
                if (files.isEmpty()) fail("supabase/migrations altında .sql yok: $migrations")
                return files
            }
            dir = dir.parentFile
        }
        fail("supabase/migrations bulunamadı (arama başlangıcı: ${File(".").absolutePath})")
    }

    /**
     * Migrasyonları sırayla uygulayıp son şemadaki kolonları çıkarır.
     *
     * Gerçek bir SQL çözümleyicisi değil; kapsam bu depodaki iki desenle sınırlı:
     * `create table ... ( ... )` ve `alter table ... add column ...`. Yeni bir
     * desen (kolon silme, yeniden adlandırma) girdiğinde bu ayrıştırıcı da
     * genişletilmeli — sessizce yanlış sonuç vermesin diye tanımadığı `alter`
     * biçimlerini yok saymak yerine hiç eşleştirmiyor.
     *
     * İki tuzağa dikkat edilmiş durumda:
     *  - **Yorumlar önce temizleniyor**, ama metin sabitlerinin içine bakmadan
     *    değil: `--` bir dizgenin içinde geçseydi satırın kalanını yutardı. Aynı
     *    hata bu projede bir kez yapıldı, tekrarlanmıyor.
     *  - **Virgülle bölme parantez derinliğine saygılı**: `check (x in ('A','B'))`
     *    içindeki virgüller kolon ayıracı değil.
     */
    private fun parseSchema(files: List<File>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()

        val createHeader = Regex(
            """create\s+table\s+(?:if\s+not\s+exists\s+)?public\.(\w+)\s*\(""",
            RegexOption.IGNORE_CASE,
        )
        val addColumn = Regex(
            """alter\s+table\s+(?:only\s+)?public\.(\w+)\s+add\s+column\s+""" +
                """(?:if\s+not\s+exists\s+)?(\w+)""",
            RegexOption.IGNORE_CASE,
        )

        for (file in files) {
            val clean = stripComments(file.readText())

            for (match in createHeader.findAll(clean)) {
                val open = match.range.last              // '(' konumu
                val body = clean.substring(open + 1, matchingParen(clean, open))
                // `if not exists` yüzünden aynı tablo iki kez görülebilir
                // (run.sh migrasyonları bilerek iki kez uyguluyor); ilk tanım
                // geçerli sayılıyor.
                result.getOrPut(match.groupValues[1]) { columnNames(body).toMutableList() }
            }

            for (match in addColumn.findAll(clean)) {
                val columns = result[match.groupValues[1]] ?: continue
                val name = match.groupValues[2].lowercase()
                if (name !in columns) columns += name
            }
        }
        return result
    }

    private fun stripComments(sql: String): String {
        val out = StringBuilder()
        var i = 0
        var inString = false
        while (i < sql.length) {
            val c = sql[i]
            when {
                inString -> {
                    out.append(c)
                    if (c == '\'') inString = false
                    i++
                }
                c == '\'' -> { out.append(c); inString = true; i++ }
                c == '-' && i + 1 < sql.length && sql[i + 1] == '-' -> {
                    while (i < sql.length && sql[i] != '\n') i++
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private fun matchingParen(text: String, openIndex: Int): Int {
        var depth = 0
        var i = openIndex
        var inString = false
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> if (c == '\'') inString = false
                c == '\'' -> inString = true
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        fail("Kapanmayan parantez, konum $openIndex")
    }

    private fun columnNames(body: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inString = false
        for (c in body) {
            when {
                inString -> { current.append(c); if (c == '\'') inString = false }
                c == '\'' -> { current.append(c); inString = true }
                c == '(' -> { depth++; current.append(c) }
                c == ')' -> { depth--; current.append(c) }
                c == ',' && depth == 0 -> { parts += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        parts += current.toString()

        return parts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { part ->
                val first = part.substringBefore(' ').substringBefore('(').lowercase()
                if (first in TABLE_CONSTRAINT_KEYWORDS) null else first
            }
    }

    private companion object {
        const val TENANT = "65409c76-0226-4d89-91a2-48c2ab0d1cab"

        /** Kolon değil, tablo düzeyi kısıt başlatan sözcükler. */
        val TABLE_CONSTRAINT_KEYWORDS = setOf(
            "unique", "primary", "foreign", "check", "constraint", "exclude", "like",
        )
    }
}
