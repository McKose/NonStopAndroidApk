package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    /**
     * Stoğu **koşullu** düşer: yalnızca yeterli stok varsa günceller.
     *
     * @return güncellenen satır sayısı; `0` ise stok yetersizdi (yarış koşulu dahil).
     */
    @Query("UPDATE products SET stockCount = stockCount - :quantity WHERE id = :id AND stockCount >= :quantity")
    suspend fun decreaseStock(id: Long, quantity: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)
}
