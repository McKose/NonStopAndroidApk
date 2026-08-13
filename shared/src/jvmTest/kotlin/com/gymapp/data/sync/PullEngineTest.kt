package com.gymapp.data.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Çekme turunun kararları.
 *
 * Veritabanı ve ağ yok; sınanan şey su işaretinin nasıl ilerlediği, hangi
 * satırın atlandığı ve turun ne zaman durduğu. Üçü de sessizce yanlış
 * olabilecek kararlar: yanlış olduklarında uygulama çalışmaya devam eder,
 * yalnızca verinin bir kısmı hiç inmez ya da kullanıcının değişikliği geri
 * alınır.
 */
class PullEngineTest {

    private val tenant = "salon-1"

    private fun satir(id: String, deltaMs: Long, kolon: String = "updated_at_ms"): JsonObject =
        buildJsonObject {
            put("id", id)
            put(kolon, deltaMs)
        }

    /** Sırayla verilen sayfaları döndüren, istenen `since` değerlerini kaydeden uç. */
    private class SahteReader(
        private val sayfalar: List<FetchResult>,
    ) : RemoteReader {
        val istenenSince = mutableListOf<Long>()
        var cagriSayisi = 0

        override suspend fun fetchChanges(table: SyncTable, sinceMs: Long, limit: Int): FetchResult {
            istenenSince += sinceMs
            cagriSayisi++
            return sayfalar.getOrElse(cagriSayisi - 1) { FetchResult.Rows(emptyList()) }
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

    private class SahteState(
        var suIsareti: Long = 0,
        val bekleyen: Set<String> = emptySet(),
    ) : PullLocalState {
        val kaydedilenler = mutableListOf<Long>()

        override suspend fun lastPulledAtMs(tenantId: String, table: SyncTable) = suIsareti
        override suspend fun savePulledAtMs(tenantId: String, table: SyncTable, atMs: Long) {
            suIsareti = atMs
            kaydedilenler += atMs
        }
        override suspend fun pendingIds(tenantId: String, table: SyncTable) = bekleyen
    }

    // ─── Temel akış ─────────────────────────────────────────────────────────

    @Test
    fun `gelen satirlar yazilir ve su isareti ilerler`() = runTest {
        val reader = SahteReader(listOf(FetchResult.Rows(listOf(satir("a", 10), satir("b", 20)))))
        val writer = SahteWriter()
        val state = SahteState()

        val sonuc = PullEngine(reader, writer, state, pageSize = 10).pullTable(tenant, SyncTable.MEMBERS)

        assertEquals(PullOutcome(applied = 2), sonuc)
        assertEquals(listOf("a", "b"), writer.yazilanlar)
        assertEquals(20L, state.suIsareti, "Su işareti en büyük damga olmalı")
    }

    /**
     * Su işareti **satırlardan** hesaplanıyor, cihaz saatinden değil.
     *
     * Damgaları satırı yazan cihaz üretiyor; iki cihazın saati saparsa yerel
     * saate dayanan bir su işareti aradaki satırları sonsuza kadar atlardı.
     */
    @Test
    fun `su isareti gelen en buyuk damgadan alinir`() = runTest {
        val reader = SahteReader(listOf(FetchResult.Rows(listOf(satir("a", 500), satir("b", 100)))))
        val state = SahteState(suIsareti = 50)

        PullEngine(reader, SahteWriter(), state, pageSize = 10).pullTable(tenant, SyncTable.MEMBERS)

        assertEquals(500L, state.suIsareti)
    }

    /**
     * Sorgu su işaretine **eşit** olanları da istiyor.
     *
     * `>` olsaydı aynı milisaniyede yazılmış iki satırdan biri su işaretine eşit
     * kalır ve bir daha hiç gelmezdi. Tekrar gelenler zararsız: yazma üzerine
     * yazıyor.
     */
    @Test
    fun `sorgu su isaretinden itibaren yapilir`() = runTest {
        val reader = SahteReader(listOf(FetchResult.Rows(emptyList())))
        val state = SahteState(suIsareti = 1234)

        PullEngine(reader, SahteWriter(), state).pullTable(tenant, SyncTable.MEMBERS)

        assertEquals(listOf(1234L), reader.istenenSince)
    }

    @Test
    fun `bos sonuc degisiklik uretmez`() = runTest {
        val writer = SahteWriter()

        val sonuc = PullEngine(SahteReader(listOf(FetchResult.Rows(emptyList()))), writer, SahteState())
            .pullTable(tenant, SyncTable.MEMBERS)

        assertEquals(PullOutcome(), sonuc)
        assertTrue(writer.yazilanlar.isEmpty())
    }

    // ─── Çakışma kuralı ─────────────────────────────────────────────────────

    /**
     * Gönderim bekleyen satırın üzerine yazılmıyor.
     *
     * O satır için yerelde henüz yukarı çıkmamış bir değişiklik var; sunucudaki
     * hâli eski. Üzerine yazmak, kullanıcının az önce yaptığı değişikliği gözü
     * önünde geri almak olurdu.
     */
    @Test
    fun `yerelde bekleyen satir atlanir`() = runTest {
        val reader = SahteReader(
            listOf(FetchResult.Rows(listOf(satir("a", 10), satir("bekleyen", 20), satir("c", 30))))
        )
        val writer = SahteWriter()
        val state = SahteState(bekleyen = setOf("bekleyen"))

        val sonuc = PullEngine(reader, writer, state, pageSize = 10).pullTable(tenant, SyncTable.MEMBERS)

        assertEquals(2, sonuc.applied)
        assertEquals(1, sonuc.skipped)
        assertEquals(listOf("a", "c"), writer.yazilanlar)
        // Atlanan satır da su işaretini ilerletiyor: bir sonraki turda tekrar
        // gelmesi zararsız ama gereksiz olurdu.
        assertEquals(30L, state.suIsareti)
    }

    @Test
    fun `okunamayan satir sayilir ve turu durdurmaz`() = runTest {
        val reader = SahteReader(listOf(FetchResult.Rows(listOf(satir("a", 10), satir("bozuk", 20)))))
        val writer = SahteWriter(okunamayanlar = setOf("bozuk"))

        val sonuc = PullEngine(reader, writer, SahteState(), pageSize = 10)
            .pullTable(tenant, SyncTable.MEMBERS)

        assertEquals(1, sonuc.applied)
        assertEquals(1, sonuc.unreadable)
        assertTrue(!sonuc.stopped, "Tek bozuk satır turu durdurmamalı")
    }

    // ─── Sayfalama ──────────────────────────────────────────────────────────

    @Test
    fun `dolu sayfa sonrasi bir sonraki sayfa istenir`() = runTest {
        val reader = SahteReader(
            listOf(
                FetchResult.Rows(listOf(satir("a", 10), satir("b", 20))),
                FetchResult.Rows(listOf(satir("c", 30))),
            )
        )
        val writer = SahteWriter()

        val sonuc = PullEngine(reader, writer, SahteState(), pageSize = 2)
            .pullTable(tenant, SyncTable.MEMBERS)

        assertEquals(3, sonuc.applied)
        assertEquals(listOf(0L, 20L), reader.istenenSince, "İkinci sayfa yeni su işaretinden istenmeli")
    }

    /**
     * Sayfa dolu ama zaman ilerlemiyorsa duruluyor.
     *
     * Aynı damgalı satır sayısı sayfa boyutunu aşarsa bir sonraki istek aynı
     * sayfayı getirirdi. Sessizce "bitti" demek, verinin geri kalanının hiç
     * inmediğini gizlerdi.
     */
    @Test
    fun `ilerlemeyen sayfa donguye girmez`() = runTest {
        val ayniDamga = listOf(satir("a", 100), satir("b", 100))
        val reader = SahteReader(List(10) { FetchResult.Rows(ayniDamga) })
        val state = SahteState(suIsareti = 100)

        val sonuc = PullEngine(reader, SahteWriter(), state, pageSize = 2)
            .pullTable(tenant, SyncTable.MEMBERS)

        assertTrue(sonuc.stopped)
        assertEquals(1, reader.cagriSayisi, "İkinci kez istenmemeli")
        assertTrue(sonuc.reason!!.contains("Aynı zaman damgalı"), "Gerekçe: ${sonuc.reason}")
    }

    @Test
    fun `sayfa sayisi sinirli`() = runTest {
        // Her sayfa dolu ve damga ilerliyor: sınır olmasa sonsuza kadar sürerdi.
        val reader = object : RemoteReader {
            var n = 0L
            override suspend fun fetchChanges(table: SyncTable, sinceMs: Long, limit: Int): FetchResult {
                n++
                return FetchResult.Rows(listOf(satir("r$n", n * 10)))
            }
        }

        val sonuc = PullEngine(reader, SahteWriter(), SahteState(), pageSize = 1, maxPages = 4)
            .pullTable(tenant, SyncTable.MEMBERS)

        assertEquals(4, sonuc.applied)
        assertTrue(sonuc.stopped)
        assertEquals(4, reader.n)
    }

    // ─── Hatalar ────────────────────────────────────────────────────────────

    @Test
    fun `gecici hata turu durdurur ve su isaretini ilerletmez`() = runTest {
        val reader = SahteReader(listOf(FetchResult.Retryable("ağ yok")))
        val state = SahteState(suIsareti = 42)

        val sonuc = PullEngine(reader, SahteWriter(), state).pullTable(tenant, SyncTable.MEMBERS)

        assertTrue(sonuc.stopped)
        assertEquals("ağ yok", sonuc.reason)
        assertEquals(42L, state.suIsareti)
        assertTrue(state.kaydedilenler.isEmpty(), "Hiçbir şey okunmadıysa su işareti yazılmamalı")
    }

    @Test
    fun `kalici hata turu durdurur`() = runTest {
        val reader = SahteReader(listOf(FetchResult.Permanent("erişim reddedildi")))

        val sonuc = PullEngine(reader, SahteWriter(), SahteState()).pullTable(tenant, SyncTable.MEMBERS)

        assertTrue(sonuc.stopped)
        assertEquals("erişim reddedildi", sonuc.reason)
    }

    // ─── Tabloya özgü zaman kolonu ──────────────────────────────────────────

    /**
     * Append-only tablolarda zaman ekseni `created_at_ms`.
     *
     * Defter ve stok hareketlerinde `updated_at_ms` kolonu yok. Motor sabit
     * kolon adı kullansaydı su işareti hiç ilerlemez ve o iki tablo her turda
     * baştan okunurdu.
     */
    @Test
    fun `defter kaydinda created_at_ms kullanilir`() = runTest {
        val reader = SahteReader(
            listOf(FetchResult.Rows(listOf(satir("l1", 77, kolon = "created_at_ms"))))
        )
        val state = SahteState()

        PullEngine(reader, SahteWriter(), state, pageSize = 10)
            .pullTable(tenant, SyncTable.LEDGER_ENTRIES)

        assertEquals(77L, state.suIsareti)
    }
}
