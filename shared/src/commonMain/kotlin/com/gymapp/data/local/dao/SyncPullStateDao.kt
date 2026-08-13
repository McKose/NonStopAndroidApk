package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gymapp.data.local.entity.SyncPullStateEntity

/** Çekme su işaretlerinin okunup yazılması. */
@Dao
interface SyncPullStateDao {

    /**
     * Bu tablodan en son hangi ana kadar okunduğu; hiç okunmadıysa `null`.
     *
     * `null` ile `0` bilinçli olarak ayrılmıyor — ikisi de "baştan oku" demek.
     */
    @Query("""
        SELECT lastPulledAtMs FROM sync_pull_state
        WHERE tenantId = :tenantId AND entityTable = :entityTable
    """)
    suspend fun lastPulledAtMs(tenantId: String, entityTable: String): Long?

    @Upsert
    suspend fun save(state: SyncPullStateEntity)
}
