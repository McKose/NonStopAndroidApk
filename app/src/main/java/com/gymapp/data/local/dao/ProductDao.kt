package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gymapp.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    /** Silinmiş (tombstone) kayıtlar listelenmez. */
    @Query("""
        SELECT * FROM products
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
        ORDER BY name ASC
    """)
    fun observeAll(tenantId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Insert
    suspend fun insert(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity)

    /**
     * Fiziksel silmez; tombstone işaretler.
     *
     * Ürün silinse bile geçmiş stok hareketleri ve satışları defterde durmaya
     * devam ediyor — fiziksel silme o kayıtları öksüz bırakırdı.
     */
    @Query("UPDATE products SET deletedAtMs = :nowMs, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun softDelete(id: String, nowMs: Long = System.currentTimeMillis())
}
