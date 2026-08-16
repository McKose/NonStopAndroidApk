package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Cihazdaki bütün yerel veriyi siler — çıkışta çağrılır.
 *
 * ### Neden var
 * Çıkışta yalnızca jeton ve salon kimliği siliniyordu; **indirilmiş satırlar
 * cihazda kalıyordu.** ADMIN giriş yapar, senkronizasyon personel tablosunu
 * (maaşlar dahil) ve defteri indirir, çıkar; aynı cihazda TRAINER giriş yapar ve
 * bu satırları salt okunur olarak **görür**. Rol kontrolü yalnızca yazma
 * düğmelerini gizliyordu, veriyi değil.
 *
 * ### Neden Room'un `clearAllTables`'ı değil
 * O işlev Room'un ortak (KMP) yüzeyinde yok — yalnızca Android tarafında var.
 * Ortak koda yazmak, aynı davranışın iOS'ta da geçerli olmasını ve **testinin
 * gerçek SQLite üzerinde koşabilmesini** sağlıyor.
 *
 * ### Dikkat: yeni tablo eklenirse buraya da eklenmeli
 * Eksik bırakılan tablo tam olarak sızan tablo olur. `ClearAllTablesTest`
 * bu listeyi [com.gymapp.data.sync.SyncTable] üzerinden geziyor: senkronize
 * edilen dokuz tablodan biri unutulursa test düşer. Senkronize edilmeyen iki
 * defter tablosu (`sync_outbox`, `sync_pull_state`) ayrıca adıyla sınanıyor.
 */
@Dao
interface MaintenanceDao {

    @Transaction
    suspend fun wipeAll() {
        // Sıra önemsiz: yabancı anahtar kısıtı yok, hepsi tek transaction'da.
        deleteMembers()
        deletePackages()
        deleteProducts()
        deleteAppointments()
        deleteStaff()
        deleteOrders()
        deleteMeasurements()
        deleteLedgerEntries()
        deleteStockMovements()
        deleteSyncOutbox()
        deleteSyncPullState()
    }

    @Query("DELETE FROM gym_members") suspend fun deleteMembers()
    @Query("DELETE FROM gym_packages") suspend fun deletePackages()
    @Query("DELETE FROM products") suspend fun deleteProducts()
    @Query("DELETE FROM appointments") suspend fun deleteAppointments()
    @Query("DELETE FROM staff") suspend fun deleteStaff()
    @Query("DELETE FROM orders") suspend fun deleteOrders()
    @Query("DELETE FROM measurements") suspend fun deleteMeasurements()
    @Query("DELETE FROM ledger_entries") suspend fun deleteLedgerEntries()
    @Query("DELETE FROM stock_movements") suspend fun deleteStockMovements()
    @Query("DELETE FROM sync_outbox") suspend fun deleteSyncOutbox()
    @Query("DELETE FROM sync_pull_state") suspend fun deleteSyncPullState()

    /**
     * Bir tablodaki satır sayısı — yalnızca doğrulama için.
     *
     * Room sorguları derleme anında doğruladığı için tablo adı parametre
     * olamaz; bu yüzden sayım her tablo için ayrı. Testin
     * [com.gymapp.data.sync.SyncTable] üzerinde gezebilmesi için sayaçlar
     * `countOf` altında toplandı.
     */
    @Query("SELECT COUNT(*) FROM gym_members") suspend fun countMembers(): Int
    @Query("SELECT COUNT(*) FROM gym_packages") suspend fun countPackages(): Int
    @Query("SELECT COUNT(*) FROM products") suspend fun countProducts(): Int
    @Query("SELECT COUNT(*) FROM appointments") suspend fun countAppointments(): Int
    @Query("SELECT COUNT(*) FROM staff") suspend fun countStaff(): Int
    @Query("SELECT COUNT(*) FROM orders") suspend fun countOrders(): Int
    @Query("SELECT COUNT(*) FROM measurements") suspend fun countMeasurements(): Int
    @Query("SELECT COUNT(*) FROM ledger_entries") suspend fun countLedgerEntries(): Int
    @Query("SELECT COUNT(*) FROM stock_movements") suspend fun countStockMovements(): Int
    @Query("SELECT COUNT(*) FROM sync_outbox") suspend fun countSyncOutbox(): Int
    @Query("SELECT COUNT(*) FROM sync_pull_state") suspend fun countSyncPullState(): Int
}
