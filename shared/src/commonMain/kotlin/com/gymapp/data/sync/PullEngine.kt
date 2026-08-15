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

/**
 * Turun neden erken bittiği.
 *
 * Sebep iki ayrı kararı belirliyor ve ikisi de sessizce yanlış olabilir:
 *
 *  - **Kalan tablolar denensin mi?** Ağ yoksa denemek sekiz gereksiz zaman aşımı
 *    demek. Ama tek bir bozuk satır, ilgisiz sekiz tablonun da bir tur boyunca
 *    hiç inmemesine yol açmamalı.
 *  - **Bir süre sonra kendiliğinden tekrar denensin mi?** Ağ geri gelir; bozuk
 *    satır kendiliğinden düzelmez. İkincisini tekrar denemek her turda aynı
 *    sonucu verir ve yalnızca pil harcar.
 *
 * İki bayrağı ayrı ayrı taşımak yerine burada isimlendirilmelerinin sebebi,
 * çağıran tarafta "hangi bayrak neden böyleydi" sorusunun cevabının kaybolması.
 */
enum class PullStop(
    val tryOtherTables: Boolean,
    val retryable: Boolean,
) {
    /** Ağ yok ya da sunucu yanıt vermiyor. */
    CONNECTION(tryOtherTables = false, retryable = true),

    /** Sunucu isteği reddetti ya da yanıtı okunamadı (yetki, bozuk yanıt). */
    REJECTED(tryOtherTables = false, retryable = false),

    /**
     * Su işareti ilerleyemiyor: ya okunamayan bir satır önü kesiyor ya da aynı
     * damgalı satır sayısı sayfa boyutunu aşıyor. İkisi de kod düzeltmesi
     * gerektiriyor; tekrar denemek aynı yere takılır.
     */
    BLOCKED(tryOtherTables = true, retryable = false),

    /** Bu turda bitmedi ama ilerleme oldu; sonraki tur kaldığı yerden devam eder. */
    INCOMPLETE(tryOtherTables = true, retryable = true),
}

/** Bir tablonun çekilme sonucu. */
data class PullOutcome(
    val applied: Int = 0,
    /** Yerelde bekleyen değişiklik olduğu için atlananlar. */
    val skipped: Int = 0,
    /** Okunamayan satırlar. */
    val unreadable: Int = 0,
    /** Tur erken bittiyse sebebi, bitmediyse `null`. */
    val stop: PullStop? = null,
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
 * ### Okunamayan satır su işaretini kilitliyor
 * Su işareti yalnızca **alınabilen** satırlardan hesaplanıyor ve okunamayan
 * satırların **en küçük** damgasını geçmiyor (ilk görülenin değil: sayfa sırası
 * bozuksa ikisi farklı olur). Geçseydi o satır bir daha hiç istenmezdi:
 * uygulamanın sonraki sürümü onu okuyabilir hâle gelse bile sunucudaki kayıt
 * cihaza inmezdi. Sonuç sessiz ve geri dönüşsüz olurdu — kullanıcı eksik olduğunu
 * fark edemeyeceği bir üye kaydı.
 *
 * Bedeli bilinçli: o tablo, satır okunabilir hâle gelene kadar olduğu yerde
 * duruyor ve arkasındaki satırlar da inmiyor. Yani hata gürültülü — ekranda
 * sebebiyle görünüyor ve düzeltilmeyi bekliyor. Alternatifi, bir satırı sessizce
 * kaybedip her şeyin yolunda göründüğü bir kurulumdu. Bu projede tercih, sessiz
 * kayıp yerine görünür duruş.
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
                    return PullOutcome(applied, skipped, unreadable, PullStop.CONNECTION, result.reason)

                is FetchResult.Permanent ->
                    return PullOutcome(applied, skipped, unreadable, PullStop.REJECTED, result.reason)

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

                    // Alınabilen satırların en büyük damgası.
                    var enBuyuk = since

                    // Okunamayan satırların en küçük damgası — su işaretinin
                    // geçemeyeceği sınır. Sayfa damga sırasında gelse de buna
                    // güvenilmiyor: sıra bozuksa "ilk bozuk satır" ile "en
                    // küçük damgalı bozuk satır" farklı olur ve arasındaki
                    // satırlar sessizce atlanırdı.
                    var engel: Long? = null

                    for (row in rows) {
                        val id = row.text("id")
                        val delta = row.text(table.deltaColumn)?.toLongOrNull()

                        val alindi = when {
                            id == null -> false
                            id in pending -> {
                                // Yerelde bekleyen değişiklik var; sunucudaki
                                // hâli eski. Atlanıyor ama okunamamış sayılmıyor:
                                // gönderim turu onu yukarı yazdıktan sonra güncel
                                // hâli zaten inecek, su işaretini durdurmanın
                                // anlamı yok.
                                skipped++
                                true
                            }
                            writer.write(table, row) -> {
                                applied++
                                true
                            }
                            else -> false
                        }

                        if (alindi) {
                            if (delta != null && delta > enBuyuk) enBuyuk = delta
                        } else {
                            unreadable++
                            // Damgası da okunamıyorsa su işareti hiç ilerlemesin:
                            // satırın nereye düştüğü bilinmiyor.
                            val sinir = delta ?: since
                            val onceki = engel
                            engel = if (onceki == null) sinir else minOf(onceki, sinir)
                        }
                    }

                    // Sorgu `>= su işareti` olduğu için engelin kendisine eşit
                    // kalmak yeterli: o satır bir sonraki turda yine gelir.
                    val yeni = engel?.let { minOf(enBuyuk, it).coerceAtLeast(since) } ?: enBuyuk
                    state.savePulledAtMs(tenantId, table, yeni)

                    if (engel != null) {
                        return PullOutcome(
                            applied, skipped, unreadable, PullStop.BLOCKED,
                            reason = "$unreadable satır okunamadı; indirme o satırda durdu.",
                        )
                    }
                    if (rows.size < pageSize) {
                        return PullOutcome(applied, skipped, unreadable)
                    }
                    if (yeni == since) {
                        // Sayfa dolu ama zaman ilerlemedi: bir sonraki istek
                        // aynı sayfayı getirirdi.
                        return PullOutcome(
                            applied, skipped, unreadable, PullStop.BLOCKED,
                            reason = "Aynı zaman damgalı satır sayısı sayfa boyutunu aşıyor.",
                        )
                    }
                    since = yeni
                }
            }
        }

        return PullOutcome(
            applied, skipped, unreadable, PullStop.INCOMPLETE,
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

/** Bütün tabloların çekilmesi. */
fun interface PullRunner {
    suspend fun pullAll(tenantId: String): PullSummary
}

data class PullSummary(
    val applied: Int = 0,
    val skipped: Int = 0,
    val unreadable: Int = 0,
    /** Tur erken bittiyse insan tarafından okunabilir sebebi. */
    val reason: String? = null,
    /**
     * Bir süre sonra kendiliğinden tekrar denemenin anlamı var mı?
     *
     * Yalnızca [reason] doluyken anlamlı. Birden çok tablo durduysa **herhangi
     * biri** tekrar denenebilirse tur da öyle sayılıyor: bozuk bir satır yüzünden
     * duran bir tablo, ağ kesintisiyle duran diğerinin tekrar denenmesini
     * engellememeli.
     */
    val retryable: Boolean = false,
) {
    /** Tur erken bitti mi. Duran her tablo bir gerekçe bırakıyor. */
    val stopped: Boolean get() = reason != null
}

/**
 * Tabloları sırayla çeker.
 *
 * ### Bir tablonun durması diğerlerini durdurur mu
 * Sebebe bağlı ([PullStop]). Ağ yoksa kalan sekiz tabloyu denemek sekiz gereksiz
 * zaman aşımı demek; tur orada bitiyor. Ama okunamayan tek bir satır o tabloya
 * özgü: ilk yazımda o da bütün turu bitiriyordu ve sonucu, ilgisiz sekiz tablonun
 * hiç inmemesiydi — bir üye kaydındaki hata yüzünden randevuların da gelmemesi
 * için bir sebep yok.
 *
 * Sıra `SyncTable` tanım sırası: üyeler ve paketler, onlara atıfta bulunan
 * randevu ve siparişlerden önce iniyor. Yerel şemada yabancı anahtar kısıtı yok,
 * yani sıra zorunlu değil; ama ekranların ara durumda tutarlı görünmesini
 * sağlıyor.
 */
class AllTablesPuller(private val engine: PullEngine) : PullRunner {

    override suspend fun pullAll(tenantId: String): PullSummary {
        var applied = 0
        var skipped = 0
        var unreadable = 0

        var ilkGerekce: String? = null
        var tekrarDenenebilir = false
        var duranSayisi = 0

        for (table in SyncTable.entries) {
            val outcome = engine.pullTable(tenantId, table)
            applied += outcome.applied
            skipped += outcome.skipped
            unreadable += outcome.unreadable

            val durus = outcome.stop ?: continue

            duranSayisi++
            tekrarDenenebilir = tekrarDenenebilir || durus.retryable
            if (ilkGerekce == null) {
                ilkGerekce = "${table.tableName} — ${outcome.reason ?: "durdu"}"
            }
            if (!durus.tryOtherTables) break
        }

        return PullSummary(
            applied, skipped, unreadable,
            // Tek gerekçe gösteriliyor; kaçının durduğu ayrıca söyleniyor.
            // Dokuz tablonun gerekçesini yan yana yazmak, ekranda okunmayan bir
            // metin bloğu üretirdi.
            reason = ilkGerekce?.let {
                if (duranSayisi > 1) "$it (ve ${duranSayisi - 1} tablo daha)" else it
            },
            retryable = tekrarDenenebilir,
        )
    }
}
