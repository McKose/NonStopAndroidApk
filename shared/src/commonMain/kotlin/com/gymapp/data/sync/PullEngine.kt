package com.gymapp.data.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Sunucudan değişiklik okuyan uç.
 *
 * [sinceMs] **dahil** (`>=`) sorgulanıyor, `>` değil. Sebebi zaman damgalarının
 * milisaniye olması: aynı milisaniyede yazılmış iki satırdan biri su işaretine
 * eşit kalır ve `>` ile bir daha hiç gelmezdi. Tekrar gelen satırlar zararsız,
 * çünkü yazma tarafı üzerine yazıyor.
 */
fun interface RemoteReader {
    suspend fun fetchChanges(table: SyncTable, sinceMs: Long, limit: Int): FetchResult
}

sealed interface FetchResult {
    data class Rows(val rows: List<JsonObject>) : FetchResult

    /** Ağ ya da sunucu sorunu; sonraki turda tekrar denenir. */
    data class Retryable(val reason: String) : FetchResult

    /** Tekrar denemekle düzelmeyecek bir sorun (yetki, bozuk yanıt). */
    data class Permanent(val reason: String) : FetchResult
}

/**
 * Gelen satırı yerelde yazan uç.
 *
 * Ayrı arayüz olması, çekme mantığının veritabanı olmadan sınanabilmesi için:
 * su işaretinin nasıl ilerlediği, hangi satırın atlandığı ve turun ne zaman
 * durduğu — sessizce yanlış olabilecek kararların hepsi burada, Room'da değil.
 *
 * `false` dönmesi "satır okunamadı" demek; eksik alanlı bir satır yazılmıyor
 * (bkz. [RowParsers]).
 */
fun interface LocalRowWriter {
    suspend fun write(table: SyncTable, row: JsonObject): Boolean
}

/**
 * Çekmenin yerel durumu: nereye kadar okunduğu ve hangi satırların gönderim
 * bekleyip beklemediği.
 */
interface PullLocalState {
    suspend fun lastPulledAtMs(tenantId: String, table: SyncTable): Long
    suspend fun savePulledAtMs(tenantId: String, table: SyncTable, atMs: Long)

    /**
     * Bu tabloda gönderim bekleyen satırların kimlikleri.
     *
     * Çekme bunları **atlıyor**: yerelde henüz gönderilmemiş bir değişiklik
     * varsa sunucudaki hâli eskidir, üzerine yazmak kullanıcının az önce
     * yaptığı değişikliği silmek olurdu.
     */
    suspend fun pendingIds(tenantId: String, table: SyncTable): Set<String>
}

/** Bir tablonun çekilme sonucu. */
data class PullOutcome(
    val applied: Int = 0,
    /** Yerelde bekleyen değişiklik olduğu için atlananlar. */
    val skipped: Int = 0,
    /** Okunamayan satırlar. */
    val unreadable: Int = 0,
    val stopped: Boolean = false,
    val reason: String? = null,
)

/**
 * Sunucudaki değişiklikleri cihaza indirir.
 *
 * ### Çakışma kuralı: yerel bekleyen değişiklik kazanır
 * Bir satır gönderim kuyruğundaysa sunucudaki hâli eskidir — kullanıcının az
 * önce yaptığı değişiklik henüz yukarı çıkmamış demektir. O satır atlanıyor;
 * gönderim turu onu sunucuya yazdıktan sonra bir sonraki çekmede zaten güncel
 * hâli gelir. Ters kurgu (sunucu kazanır) kullanıcının gözü önünde yaptığı
 * değişikliği geri alırdı.
 *
 * ### Su işareti neden satırlardan hesaplanıyor
 * "Şu ana kadar okudum" demek için cihaz saati kullanılamaz: zaman damgalarını
 * satırı **yazan cihaz** üretiyor ve iki cihazın saati sapabilir. Su işareti bu
 * yüzden gelen satırların en büyük damgası oluyor — cihaz saatinden tamamen
 * bağımsız.
 *
 * ### Tur neden sınırlı
 * Sayfa dolu geldiğinde bir sonraki sayfa isteniyor. Sayfadaki tüm satırların
 * damgası su işaretiyle aynıysa ilerleme olmuyor ve döngü sonsuza kadar aynı
 * sayfayı isterdi; bu durum ayrıca raporlanıyor. Sessizce durmak, verinin bir
 * kısmının hiç inmediğini gizlerdi.
 */
class PullEngine(
    private val reader: RemoteReader,
    private val writer: LocalRowWriter,
    private val state: PullLocalState,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val maxPages: Int = DEFAULT_MAX_PAGES,
) {

    suspend fun pullTable(tenantId: String, table: SyncTable): PullOutcome {
        var applied = 0
        var skipped = 0
        var unreadable = 0
        var since = state.lastPulledAtMs(tenantId, table)

        repeat(maxPages) {
            when (val result = reader.fetchChanges(table, since, pageSize)) {
                is FetchResult.Retryable ->
                    return PullOutcome(applied, skipped, unreadable, stopped = true, reason = result.reason)

                is FetchResult.Permanent ->
                    return PullOutcome(applied, skipped, unreadable, stopped = true, reason = result.reason)

                is FetchResult.Rows -> {
                    val rows = result.rows
                    if (rows.isEmpty()) {
                        // Su işareti yine de kaydediliyor: ilk turda 0'dan
                        // başlanmışsa ve hiç satır yoksa bir sonraki tur da
                        // 0'dan başlar — zararsız ama gereksiz.
                        state.savePulledAtMs(tenantId, table, since)
                        return PullOutcome(applied, skipped, unreadable)
                    }

                    val pending = state.pendingIds(tenantId, table)
                    var enBuyuk = since

                    for (row in rows) {
                        val id = row.text("id")
                        val delta = row.text(table.deltaColumn)?.toLongOrNull()
                        if (delta != null && delta > enBuyuk) enBuyuk = delta

                        when {
                            id == null -> unreadable++
                            id in pending -> skipped++
                            writer.write(table, row) -> applied++
                            else -> unreadable++
                        }
                    }

                    state.savePulledAtMs(tenantId, table, enBuyuk)

                    if (rows.size < pageSize) {
                        return PullOutcome(applied, skipped, unreadable)
                    }
                    if (enBuyuk == since) {
                        // Sayfa dolu ama zaman ilerlemedi: bir sonraki istek
                        // aynı sayfayı getirirdi.
                        return PullOutcome(
                            applied, skipped, unreadable, stopped = true,
                            reason = "Aynı zaman damgalı satır sayısı sayfa boyutunu aşıyor.",
                        )
                    }
                    since = enBuyuk
                }
            }
        }

        return PullOutcome(
            applied, skipped, unreadable, stopped = true,
            reason = "Çok fazla değişiklik var; indirme sürüyor.",
        )
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 200
        const val DEFAULT_MAX_PAGES = 50
    }
}

private fun JsonObject.text(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
