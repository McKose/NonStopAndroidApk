package com.gymapp.data.sync

import com.gymapp.data.local.db.GymDatabase
import kotlinx.serialization.json.JsonObject

/**
 * Gönderilecek satırı yerel veritabanından okur.
 *
 * Kuyruk kaydı yalnızca bir işaretçi (tablo + kimlik); satırın içeriği gönderim
 * anında okunuyor. Bunun sonucu: bir satır kuyruğa girdikten sonra beş kez daha
 * değişse bile tek bir gönderimde **güncel** hâli gidiyor. Kuyruğa satırın
 * kopyası yazılsaydı her değişiklik ayrı bir gönderim olur, sıra ve son yazan
 * kuralı ayrıca yönetilmek zorunda kalırdı.
 *
 * Veritabanını bütün olarak alıyor, dokuz ayrı DAO'yu değil: eşleme zaten
 * tabloların tamamını kapsıyor, tek tek saymak yalnızca çağıran tarafta gürültü
 * olurdu.
 *
 * `null` dönüşü "satır yok" demek ve [SupabaseRemoteDataSource] bunu kalıcı hata
 * sayıyor. Tasarım gereği oluşmaması gereken bir durum — silmeler tombstone ile
 * yapıldığı için satırlar yerinde kalıyor — ama sessizce başarı saymak, veri
 * kaybını gizlemek olurdu.
 */
class LocalRowPayloadProvider(
    private val db: GymDatabase,
) : RowPayloadProvider {

    override suspend fun payload(table: SyncTable, entityId: String): JsonObject? =
        when (table) {
            SyncTable.MEMBERS ->
                db.memberDao().getMemberById(entityId)?.let { RowPayloads.of(it) }

            SyncTable.PACKAGES ->
                db.packageDao().getPackageById(entityId)?.let { RowPayloads.of(it) }

            SyncTable.PRODUCTS ->
                db.productDao().getById(entityId)?.let { RowPayloads.of(it) }

            SyncTable.APPOINTMENTS ->
                db.appointmentDao().getById(entityId)?.let { RowPayloads.of(it) }

            SyncTable.STAFF ->
                db.staffDao().getStaffById(entityId)?.let { RowPayloads.of(it) }

            SyncTable.ORDERS ->
                db.orderDao().getById(entityId)?.let { RowPayloads.of(it) }

            SyncTable.MEASUREMENTS ->
                db.measurementDao().getById(entityId)?.let { RowPayloads.of(it) }

            SyncTable.LEDGER_ENTRIES ->
                db.ledgerDao().getById(entityId)?.let { RowPayloads.of(it) }

            SyncTable.STOCK_MOVEMENTS ->
                db.stockMovementDao().getById(entityId)?.let { RowPayloads.of(it) }
        }
}
