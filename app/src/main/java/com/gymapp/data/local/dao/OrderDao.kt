package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gymapp.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    /** Silinmiş (tombstone) kayıtlar listelenmez. */
    @Query("""
        SELECT * FROM orders
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
        ORDER BY dateMs DESC
    """)
    fun observeAll(tenantId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: String): OrderEntity?

    @Insert
    suspend fun insert(order: OrderEntity)
}
