package com.gymapp.data.sync

import com.gymapp.domain.StaffRole
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [SyncTable.writableBy] ile sunucudaki kuralın aynı şeyi söylediğinin sınanması.
 *
 * Yetki iki yerde yazılı ve bu bilinçli: sunucu kuralı **zorunlu tutuyor**,
 * uygulamadaki kopya ise kullanıcıya reddedileceği bir işi hiç yaptırmıyor.
 * Kopya olmasaydı eğitmen fiyatı değiştirir, kayıt kuyruğa girer ve ancak
 * senkronizasyon turunda kalıcı 403 olarak geri dönerdi.
 *
 * İkisinin ayrışması **sessiz** bir hata: sunucuda `staff` kuralı gevşetilse ama
 * uygulamada gevşetilmese kimse fark etmez (uygulama gereğinden katı davranır);
 * tersi durumda ise uygulama düğmeyi açar ve kayıt kuyrukta ölür. Bu test o
 * ayrışmayı derleme sonrası ilk koşuda yakalıyor.
 *
 * **Sunucu dosyası kaynak sayılıyor**, uygulama değil: kuralı gerçekten uygulayan
 * taraf o. Test bu yüzden migrasyonu okuyup uygulamayı ona göre doğruluyor,
 * tersini değil.
 */
class SyncTableRolesTest {

    @Test
    fun `uygulamadaki yetki tablosu sunucudaki kuralla ayni`() {
        val beklenen = parseMigration()

        // Ayrıştırıcının gerçekten bir şey bulduğu önce kanıtlanıyor. Bu olmadan
        // desen bozulduğunda `beklenen` boş kalır, döngü hiç dönmez ve test
        // "geçer" — sınamak istediği şeyi hiç sınamadan.
        assertEquals(
            SyncTable.entries.map { it.tableName }.sorted(),
            beklenen.keys.sorted(),
            "Migrasyondaki tablo listesi SyncTable ile örtüşmüyor",
        )

        for (table in SyncTable.entries) {
            assertEquals(
                beklenen.getValue(table.tableName),
                table.writableBy,
                "'${table.tableName}' için yetki uyuşmuyor — " +
                    "sunucu: ${beklenen.getValue(table.tableName)}, uygulama: ${table.writableBy}",
            )
        }
    }

    /**
     * Kural gerçekten ayrım yapıyor olmalı.
     *
     * Yukarıdaki test, iki taraf da "herkes yazabilir" deseydi de geçerdi —
     * yani kuralın tamamen düşmesi fark edilmezdi. Bu iddia en az bir tablonun
     * kısıtlı olduğunu ve en az birinin serbest olduğunu ayrıca söylüyor.
     */
    @Test
    fun `yetki tablosu gercekten ayrim yapiyor`() {
        assertEquals(setOf(StaffRole.ADMIN), SyncTable.STAFF.writableBy)
        assertTrue(
            StaffRole.TRAINER in SyncTable.MEMBERS.writableBy,
            "Eğitmen üye kaydedemiyorsa uygulama işe yaramaz",
        )
        assertTrue(
            StaffRole.TRAINER !in SyncTable.PRODUCTS.writableBy,
            "Fiyat listesi eğitmene kapalı olmalı",
        )
    }

    // ─── Migrasyonun ayrıştırılması ─────────────────────────────────────────

    /**
     * `0004_role_based_access.sql` içindeki yetki eşlemesini çıkarır.
     *
     * Gerçek bir PL/pgSQL çözümleyicisi değil; o dosyadaki tek desene bakıyor:
     *
     * ```
     * hepsi constant text[] := array['ADMIN', 'MANAGER', 'TRAINER'];
     * ...
     * foreach t in array array['gym_packages', 'staff', ...]
     * ...
     * yazabilen := case t
     *     when 'staff' then array['ADMIN']
     *     ...
     *     else hepsi
     * end;
     * ```
     *
     * Desen bozulursa test **hata veriyor**, sessizce boş sonuç döndürmüyor:
     * ayrıştırıcının bir şey bulamaması, kuralın uyuştuğu anlamına gelmez.
     */
    private fun parseMigration(): Map<String, Set<StaffRole>> {
        val metin = stripComments(migrationFile().readText())

        val varsayilan = rolesFrom(
            Regex("""hepsi\s+constant\s+text\[\]\s*:=\s*array\[([^\]]*)\]""", RegexOption.IGNORE_CASE)
                .find(metin)
                ?.groupValues?.get(1)
                ?: fail("'hepsi constant text[] := array[...]' bulunamadı — desen değişmiş olabilir"),
        )
        if (varsayilan.isEmpty()) fail("Varsayılan rol kümesi boş çözümlendi")

        val tablolar = Regex("""foreach\s+t\s+in\s+array\s+array\[([^\]]*)\]""", RegexOption.IGNORE_CASE)
            .find(metin)
            ?.groupValues?.get(1)
            ?.let { textLiterals(it) }
            ?: fail("'foreach t in array array[...]' bulunamadı — desen değişmiş olabilir")
        if (tablolar.isEmpty()) fail("Tablo listesi boş çözümlendi")

        // `case t when '<tablo>' then array[...]` — yalnızca kısıtlı olanlar
        // burada listeleniyor, kalanı `else hepsi`.
        val istisnalar = Regex(
            """when\s+'(\w+)'\s*then\s+array\[([^\]]*)\]""",
            RegexOption.IGNORE_CASE,
        ).findAll(metin).associate { it.groupValues[1] to rolesFrom(it.groupValues[2]) }

        // İstisna listesinde tanınmayan bir tablo varsa sessizce yok saymak
        // yerine düşülüyor: adı yanlış yazılmış bir istisna, hiç uygulanmayan
        // bir kural demek.
        val tanimsiz = istisnalar.keys - tablolar.toSet()
        if (tanimsiz.isNotEmpty()) fail("Kural listesinde olmayan tablolar için istisna var: $tanimsiz")

        return tablolar.associateWith { istisnalar[it] ?: varsayilan }
    }

    /** `'ADMIN', 'MANAGER'` → rol kümesi. Tanınmayan rol testi düşürür. */
    private fun rolesFrom(icerik: String): Set<StaffRole> =
        textLiterals(icerik).map { ad ->
            runCatching { StaffRole.valueOf(ad) }
                .getOrElse { fail("Migrasyonda tanınmayan rol: '$ad'") }
        }.toSet()

    /** `'a', 'b'` biçimindeki metin sabitlerini ayıklar. */
    private fun textLiterals(icerik: String): List<String> =
        Regex("""'([^']*)'""").findAll(icerik).map { it.groupValues[1] }.toList()

    /**
     * Yorumlar temizleniyor.
     *
     * Şart: bu dosyadaki açıklamalar `array['ADMIN']` gibi örnekler içeriyor ve
     * temizlenmeseler gerçek kuralmış gibi eşleşirlerdi.
     */
    private fun stripComments(sql: String): String =
        sql.lineSequence().joinToString("\n") { it.substringBefore("--") }

    private fun migrationFile(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val aday = File(dir, "supabase/migrations/0004_role_based_access.sql")
            if (aday.isFile) return aday
            dir = dir.parentFile
        }
        fail("0004_role_based_access.sql bulunamadı (başlangıç: ${File(".").absolutePath})")
    }
}
