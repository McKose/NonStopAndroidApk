package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.gymapp.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

/**
 * Stok hareketleri — append-only, bu yüzden `@Update`/`@Delete` yoktur.
 *
 * Eldeki stok hareketlerin toplamıdır; mutlak bir sayaç tutulmaz.
 */
@Dao
interface StockMovementDao {

    /** Tek kayıt — senkronizasyon gönderiminin okuduğu yer. */
    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun getById(id: String): StockMovementEntity?

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

    // KALDIRILDI: `observeForProduct`. Tek ürünün hareket dökümünü verecek bir
    // ekran hiç olmadı; sorgu yazıldığı günden beri çağrılmıyordu. Böyle bir
    // ekran gerektiğinde geri eklemek tek satır — çağrılmayan bir sorguyu
    // tutmak ise onu her şema değişikliğinde güncel sanmak demek.

    /**
     * Sunucudan gelen satırı yazar: yoksa ekler, varsa üzerine yazar.
     *
     * Çekme tarafının tek yazma yolu. `@Insert` ile ayrı bir `@Update` yerine
     * tek çağrı olması bilinçli: hangisinin gerektiğine karar vermek için önce
     * okumak gerekirdi ve o okuma ile yazma arasında satır değişebilirdi.
     *
     * Yerelde gönderim bekleyen satırlar buraya hiç gelmiyor; o ayıklama
     * `PullEngine` içinde yapılıyor (yerel değişiklik sunucudakinden yenidir).
     */
    @Upsert
    suspend fun upsertFromServer(row: StockMovementEntity)
}

/** Ürün başına eldeki stok — gruplu sorgu sonucu. */
data class ProductStock(
    val productId: String,
    val onHand: Int,
)
