package com.gymapp.data.sync

import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.SyncPullStateEntity
import kotlinx.serialization.json.JsonObject

/**
 * Çekmenin yerel durumunu Room üzerinde tutar.
 *
 * Su işareti tablo başına: tablolar bağımsız ilerliyor ve biri hata alıp
 * durduğunda diğerlerinin ilerlemesi kaybolmamalı.
 */
class RoomPullLocalState(
    private val db: GymDatabase,
) : PullLocalState {

    override suspend fun lastPulledAtMs(tenantId: String, table: SyncTable): Long =
        db.syncPullStateDao().lastPulledAtMs(tenantId, table.tableName) ?: 0L

    override suspend fun savePulledAtMs(tenantId: String, table: SyncTable, atMs: Long) {
        db.syncPullStateDao().save(
            SyncPullStateEntity(
                tenantId = tenantId,
                entityTable = table.tableName,
                lastPulledAtMs = atMs,
            )
        )
    }

    override suspend fun pendingIds(tenantId: String, table: SyncTable): Set<String> =
        db.syncOutboxDao().pendingIds(tenantId, table.tableName).toSet()
}

/**
 * Sunucudan gelen satırı yerele yazar.
 *
 * Okunamayan satır için `false` dönüyor ve **yazılmıyor**: eksik alanı
 * varsayılanla doldurmak, sunucudaki doğru veriyi cihazda bozmak ve o bozuk
 * değeri bir sonraki gönderimde sunucuya geri yazmak olurdu.
 *
 * Yazma tek çağrıda (upsert): önce okuyup "var mı yok mu" diye karar vermek,
 * okuma ile yazma arasında satırın değişebileceği bir pencere açardı.
 */
class RoomRowWriter(
    private val db: GymDatabase,
) : LocalRowWriter {

    override suspend fun write(table: SyncTable, row: JsonObject): Boolean = when (table) {
        SyncTable.MEMBERS ->
            RowParsers.member(row)?.also { db.memberDao().upsertFromServer(it) } != null

        SyncTable.PACKAGES ->
            RowParsers.packageRow(row)?.also { db.packageDao().upsertFromServer(it) } != null

        SyncTable.PRODUCTS ->
            RowParsers.product(row)?.also { db.productDao().upsertFromServer(it) } != null

        SyncTable.APPOINTMENTS ->
            RowParsers.appointment(row)?.also { db.appointmentDao().upsertFromServer(it) } != null

        SyncTable.STAFF ->
            RowParsers.staff(row)?.also { db.staffDao().upsertFromServer(it) } != null

        SyncTable.ORDERS ->
            RowParsers.order(row)?.also { db.orderDao().upsertFromServer(it) } != null

        SyncTable.MEASUREMENTS ->
            RowParsers.measurement(row)?.also { db.measurementDao().upsertFromServer(it) } != null

        SyncTable.LEDGER_ENTRIES ->
            RowParsers.ledgerEntry(row)?.also { db.ledgerDao().upsertFromServer(it) } != null

        SyncTable.STOCK_MOVEMENTS ->
            RowParsers.stockMovement(row)?.also { db.stockMovementDao().upsertFromServer(it) } != null
    }
}
