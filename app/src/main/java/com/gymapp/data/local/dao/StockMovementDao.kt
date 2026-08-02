package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gymapp.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

/**
 * Stok hareketleri — append-only, bu yüzden `@Update`/`@Delete` yoktur.
 *
 * Eldeki stok hareketlerin toplamıdır; mutlak bir sayaç tutulmaz.
 */
@Dao
interface StockMovementDao {

    @Insert
    suspend fun insert(movement: StockMovementEntity)

    @Insert
    suspend fun insertAll(movements: List<StockMovementEntity>)

    /** Tek ürünün eldeki stoğu. */
    @Query("""
        SELECT COALESCE(SUM(quantityDelta), 0) FROM stock_movements
        WHERE tenantId = :tenantId AND productId = :productId
    """)
    suspend fun onHand(tenantId: String, productId: String): Int

    /** Tüm ürünlerin eldeki stoğu — ürün listesiyle birleştirmek için. */
    @Query("""
        SELECT productId, COALESCE(SUM(quantityDelta), 0) AS onHand
        FROM stock_movements
        WHERE tenantId = :tenantId
        GROUP BY productId
    """)
    fun observeOnHandByProduct(tenantId: String): Flow<List<ProductStock>>

    @Query("""
        SELECT * FROM stock_movements
        WHERE tenantId = :tenantId AND productId = :productId
        ORDER BY occurredAtMs DESC
    """)
    fun observeForProduct(tenantId: String, productId: String): Flow<List<StockMovementEntity>>
}

/** Ürün başına eldeki stok — gruplu sorgu sonucu. */
data class ProductStock(
    val productId: String,
    val onHand: Int,
)
