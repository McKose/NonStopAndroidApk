package com.gymapp.data.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bir tablonun durması diğerlerini de durdurur mu?
 *
 * Cevap sebebe bağlı ve ikisi de sessizce yanlış olabilir:
 *  - ağ yokken kalan sekiz tabloyu denemek, sekiz gereksiz zaman aşımı,
 *  - okunamayan tek bir satır yüzünden sekiz tabloyu durdurmak ise, ilgisiz
 *    verinin de bir tur boyunca hiç inmemesi.
 *
 * Motor sahte değil, gerçek [PullEngine]: sahte olsaydı durma sebeplerinin
 * gerçekten üretildiğini değil, testin onları doğru kurguladığını sınardık.
 */
class AllTablesPullerTest {

    private val tenant = "salon-1"

    private fun satir(id: String, deltaMs: Long, kolon: String): JsonObject =
        buildJsonObject {
            put("id", id)
            put(kolon, deltaMs)
        }

    /** Her tabloya ne döneceği ayrı ayrı verilen uç. */
    private class TabloBazliReader(
        private val yanitlar: Map<SyncTable, FetchResult>,
        private val varsayilan: FetchResult = FetchResult.Rows(emptyList()),
    ) : RemoteReader {
        val istenenTablolar = mutableListOf<SyncTable>()

        override suspend fun fetchChanges(table: SyncTable, sinceMs: Long, limit: Int): FetchResult {
            istenenTablolar += table
            return yanitlar[table] ?: varsayilan
        }
    }

    private class SahteWriter(private val okunamayanlar: Set<String> = emptySet()) : LocalRowWriter {
        val yazilanlar = mutableListOf<String>()

        override suspend fun write(table: SyncTable, row: JsonObject): Boolean {
            val id = row["id"].toString().trim('"')
            if (id in okunamayanlar) return false
            yazilanlar += id
            return true
        }
    }

    private class SahteState : PullLocalState {
        private val isaretler = mutableMapOf<SyncTable, Long>()

        override suspend fun lastPulledAtMs(tenantId: String, table: SyncTable) = isaretler[table] ?: 0L
        override suspend fun savePulledAtMs(tenantId: String, table: SyncTable, atMs: Long) {
            isaretler[table] = atMs
        }
        override suspend fun pendingIds(tenantId: String, table: SyncTable) = emptySet<String>()
    }

    private fun puller(reader: RemoteReader, writer: LocalRowWriter) =
        AllTablesPuller(PullEngine(reader, writer, SahteState(), pageSize = 10))

    // ─── Ağ sorunu: tur biter ───────────────────────────────────────────────

    /**
     * Ağ yoksa kalan tablolar denenmiyor.
     *
     * Aynı ağ, aynı sonuç: sekiz istek daha atmanın karşılığı yalnızca sekiz
     * zaman aşımı olurdu.
     */
    @Test
    fun `baglanti sorunu kalan tablolari denemez`() = runTest {
        val reader = TabloBazliReader(mapOf(SyncTable.MEMBERS to FetchResult.Retryable("ağ yok")))

        val ozet = puller(reader, SahteWriter()).pullAll(tenant)

        assertEquals(listOf(SyncTable.MEMBERS), reader.istenenTablolar, "İlk tablodan sonra durmalı")
        assertTrue(ozet.stopped)
        assertTrue(ozet.retryable, "Ağ geri gelince tekrar denenmeli")
        assertTrue(ozet.reason!!.contains("gym_members"), "Hangi tablo: ${ozet.reason}")
    }

    /** Yetki hatası da turu bitiriyor ama tekrar denenmiyor. */
    @Test
    fun `kalici sunucu hatasi turu bitirir ve tekrar denenmez`() = runTest {
        val reader = TabloBazliReader(
            mapOf(SyncTable.MEMBERS to FetchResult.Permanent("erişim reddedildi"))
        )

        val ozet = puller(reader, SahteWriter()).pullAll(tenant)

        assertEquals(listOf(SyncTable.MEMBERS), reader.istenenTablolar)
        assertFalse(ozet.retryable, "Yetki hatası tekrar denemekle düzelmez")
    }

    // ─── Bozuk satır: yalnızca kendi tablosunu durdurur ─────────────────────

    /**
     * Okunamayan bir üye satırı, randevuların inmesini engellemiyor.
     *
     * İlk yazımda engelliyordu: duran ilk tablo bütün turu bitiriyordu. Sonucu,
     * bir üye kaydındaki hata yüzünden sekiz ilgisiz tablonun da bir tur boyunca
     * hiç güncellenmemesiydi.
     */
    @Test
    fun `bozuk satir diger tablolari durdurmuyor`() = runTest {
        val reader = TabloBazliReader(
            mapOf(
                SyncTable.MEMBERS to FetchResult.Rows(
                    listOf(satir("bozuk", 20, "updated_at_ms"))
                ),
                SyncTable.APPOINTMENTS to FetchResult.Rows(
                    listOf(satir("randevu-1", 30, "updated_at_ms"))
                ),
            )
        )
        val writer = SahteWriter(okunamayanlar = setOf("bozuk"))

        val ozet = puller(reader, writer).pullAll(tenant)

        assertEquals(
            SyncTable.entries.toList(), reader.istenenTablolar,
            "Bozuk satır yalnızca kendi tablosunu durdurmalı",
        )
        assertTrue("randevu-1" in writer.yazilanlar, "Randevu yine de inmeliydi")
        assertEquals(1, ozet.applied)
        assertEquals(1, ozet.unreadable)
        assertTrue(ozet.stopped, "Sorun yine de bildirilmeli")
        assertFalse(ozet.retryable, "Satır kendiliğinden düzelmiyor")
    }

    /**
     * Birden çok tablo durduysa kaçının durduğu söyleniyor.
     *
     * Dokuz gerekçeyi yan yana yazmak ekranda okunmayan bir metin bloğu üretirdi;
     * yalnızca ilkini yazmak ise diğerlerini gizlerdi.
     */
    @Test
    fun `birden cok duran tablo sayilarak bildiriliyor`() = runTest {
        val reader = TabloBazliReader(
            mapOf(
                SyncTable.MEMBERS to FetchResult.Rows(listOf(satir("bozuk-1", 20, "updated_at_ms"))),
                SyncTable.ORDERS to FetchResult.Rows(listOf(satir("bozuk-2", 20, "updated_at_ms"))),
            )
        )
        val writer = SahteWriter(okunamayanlar = setOf("bozuk-1", "bozuk-2"))

        val ozet = puller(reader, writer).pullAll(tenant)

        assertEquals(2, ozet.unreadable)
        assertTrue(ozet.reason!!.contains("gym_members"), "İlk duran yazılmalı: ${ozet.reason}")
        assertTrue(ozet.reason!!.contains("1 tablo daha"), "Kalanlar sayılmalı: ${ozet.reason}")
    }

    /**
     * Duruşlardan **herhangi biri** tekrar denenebilirse tur da öyle sayılıyor.
     *
     * Ayrı ayrı bakılsaydı sıralamaya bağlı bir hata çıkardı: bozuk satır
     * yüzünden duran üye tablosu ilk sırada olduğu için "tekrar denemeye gerek
     * yok" denir, ağ kesintisiyle duran tablo hiç dikkate alınmazdı — ve ağ geri
     * geldiğinde kimse tekrar denemezdi.
     */
    @Test
    fun `bir tablo tekrar denenebilirse tur tekrar denenebilir`() = runTest {
        val reader = TabloBazliReader(
            mapOf(
                // Sırada önce gelen: tekrar denenmeyecek bir duruş.
                SyncTable.MEMBERS to FetchResult.Rows(listOf(satir("bozuk", 20, "updated_at_ms"))),
                // Sonra gelen: ağ.
                SyncTable.ORDERS to FetchResult.Retryable("ağ yok"),
            )
        )
        val writer = SahteWriter(okunamayanlar = setOf("bozuk"))

        val ozet = puller(reader, writer).pullAll(tenant)

        assertTrue(ozet.retryable, "Ağ kesintisi tekrar denemeyi gerektiriyor")
        assertTrue(
            SyncTable.ORDERS in reader.istenenTablolar,
            "Bozuk satır, sonraki tabloların denenmesini engellememeli",
        )
    }

    // ─── Sorunsuz tur ───────────────────────────────────────────────────────

    @Test
    fun `sorunsuz turda gerekce yok`() = runTest {
        val reader = TabloBazliReader(
            mapOf(SyncTable.MEMBERS to FetchResult.Rows(listOf(satir("a", 10, "updated_at_ms"))))
        )

        val ozet = puller(reader, SahteWriter()).pullAll(tenant)

        assertEquals(1, ozet.applied)
        assertEquals(0, ozet.unreadable)
        assertFalse(ozet.stopped)
        assertNull(ozet.reason)
        assertEquals(SyncTable.entries.toList(), reader.istenenTablolar, "Her tablo denenmiş olmalı")
    }
}
