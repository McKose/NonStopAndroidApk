package com.gymapp.data.sync

import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Silinen satır **hâlâ gönderilebilmeli**.
 *
 * Silme bu projede tombstone: satır yerinde kalıyor, `deletedAtMs` doluyor ve
 * satır kuyruğa alınıyor. Gönderim anında satır yerelden okunuyor
 * ([LocalRowPayloadProvider]); okuma tombstone'ları süzerse `null` döner ve
 * [SupabaseRemoteDataSource] bunu **kalıcı hata** sayar.
 *
 * Sonucu sessiz ve geri dönüşsüz: silme sunucuya hiç gitmez, kayıt panelde ve
 * diğer cihazlarda canlı kalır, kuyruk kaydı hiç düşmez ve `pendingIds` o satırı
 * çekmede de kalıcı olarak atlar — yani sunucudaki hâli geri de inemez. Hiçbir
 * yerde hata görünmez; tek belirti sıfıra inmeyen bir bekleyen kayıt sayacı.
 *
 * Bu tam olarak `gym_packages`'ta olan şeydi: dokuz kimlik sorgusundan **yalnızca
 * biri** `AND deletedAtMs IS NULL` içeriyordu ve gönderim yolu tam onu
 * kullanıyordu.
 *
 * Bu yüzden test tek bir tabloyu değil **[SyncTable.entries]'in tamamını**
 * geziyor: hatayı düzeltmek bir satır, sınıfı kapatmak bu test. Yarın onuncu
 * tablo eklenirse ya da bir sorguya süzgeç geri konursa burada düşer.
 */
class TombstonePayloadTest {

    private val db: GymDatabase = createTestDatabase()
    private val payloads = LocalRowPayloadProvider(db)

    @AfterTest
    fun kapat() {
        db.close()
    }

    /**
     * Örnek satırların hepsi zaten `deletedAtMs = 3` taşıyor, yani silinmiş
     * sayılıyorlar. Sunucudan gelen satırı yazan yol (`upsertFromServer`)
     * kullanılıyor: bu yol süzgeç uygulamıyor ve testin kurgusunu, sınadığı
     * kodun kendisine bağlamıyor.
     */
    private suspend fun silinmisSatiriYaz(table: SyncTable): String = when (table) {
        SyncTable.MEMBERS -> SampleRows.member.also { db.memberDao().upsertFromServer(it) }.id
        SyncTable.PACKAGES -> SampleRows.packageRow.also { db.packageDao().upsertFromServer(it) }.id
        SyncTable.PRODUCTS -> SampleRows.product.also { db.productDao().upsertFromServer(it) }.id
        SyncTable.APPOINTMENTS ->
            SampleRows.appointment.also { db.appointmentDao().upsertFromServer(it) }.id
        SyncTable.STAFF -> SampleRows.staff.also { db.staffDao().upsertFromServer(it) }.id
        SyncTable.ORDERS -> SampleRows.order.also { db.orderDao().upsertFromServer(it) }.id
        SyncTable.MEASUREMENTS ->
            SampleRows.measurement.also { db.measurementDao().upsertFromServer(it) }.id
        // Defter ve stok hareketleri append-only: `deletedAtMs` kolonu yok,
        // düzeltme ters kayıtla yapılıyor. Yine de listede duruyorlar ki
        // `SyncTable.entries` üzerinden geçen bu döngü eksiksiz kalsın.
        SyncTable.LEDGER_ENTRIES ->
            SampleRows.ledgerEntry.also { db.ledgerDao().upsertFromServer(it) }.id
        SyncTable.STOCK_MOVEMENTS ->
            SampleRows.stockMovement.also { db.stockMovementDao().upsertFromServer(it) }.id
    }

    @Test
    fun `silinen satirin gonderilecek icerigi hala uretilebiliyor`() = runTest {
        val basarisiz = mutableListOf<String>()

        for (table in SyncTable.entries) {
            val id = silinmisSatiriYaz(table)
            val icerik = payloads.payload(table, id)
            if (icerik == null) {
                basarisiz += table.tableName
            }
        }

        if (basarisiz.isNotEmpty()) {
            fail(
                "Şu tabloların silinen satırı gönderilemiyor: ${basarisiz.joinToString()}. " +
                    "Kimlik sorgusu tombstone'ları süzüyor olmalı — süzgeci kaldırın, " +
                    "yoksa silme sunucuya hiç gitmez ve kuyruk kaydı hiç düşmez."
            )
        }
    }

    /**
     * Kurgunun gerçekten **silinmiş** satır kullandığının kontrolü.
     *
     * Bu olmasaydı yukarıdaki test, satırlar hiç silinmemiş olsa bile geçerdi —
     * yani hiçbir şey sınamayan bir test olurdu.
     *
     * Okuma için `getMemberById` seçildi: o sorgu tombstone süzmüyor ve
     * süzmemesi de ayrıca sınanıyor (ilk test). Silinmiş paketi okuyacak bir
     * sorgu üzerinden doğrulamak, kurgunun kontrolünü düzeltilen kodun kendisine
     * bağlamak olurdu.
     */
    @Test
    fun `kurgu gercekten silinmis satir kullaniyor`() = runTest {
        // Tombstone kolonu olan tabloların örnekleri silinmiş olmalı.
        val tombstoneluOrnekler = mapOf(
            "gym_members" to SampleRows.member.deletedAtMs,
            "gym_packages" to SampleRows.packageRow.deletedAtMs,
            "products" to SampleRows.product.deletedAtMs,
            "appointments" to SampleRows.appointment.deletedAtMs,
            "staff" to SampleRows.staff.deletedAtMs,
            "orders" to SampleRows.order.deletedAtMs,
            "measurements" to SampleRows.measurement.deletedAtMs,
        )
        for ((ad, damga) in tombstoneluOrnekler) {
            assertNotNull(damga, "$ad örneği silinmiş olmalıydı; yoksa test bir şey sınamıyor")
        }

        // Ve yazıldıktan sonra veritabanında da silinmiş görünmeli.
        silinmisSatiriYaz(SyncTable.MEMBERS)
        val uye = db.memberDao().getMemberById("m1") ?: fail("Örnek üye yazılamadı")
        assertNotNull(uye.deletedAtMs, "Satır veritabanına silinmiş olarak yazılmalıydı")
    }

    /**
     * Gerçekten var olmayan satır için `null` dönmeye devam etmeli.
     *
     * Süzgeci kaldırmanın "her zaman içerik üret" hâline dönüşmediğini sınıyor:
     * o durumda gerçek bir veri kaybı (satır yerelden yok olmuş) sessizce
     * başarılı sayılırdı.
     */
    @Test
    fun `olmayan satir icin icerik uretilmiyor`() = runTest {
        for (table in SyncTable.entries) {
            assertTrue(
                payloads.payload(table, "boyle-bir-kimlik-yok") == null,
                "${table.tableName}: var olmayan satır için içerik üretilmemeli",
            )
        }
    }
}
