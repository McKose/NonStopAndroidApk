package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gymapp.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

/**
 * Gönderim kuyruğu erişimi.
 *
 * Kuyrukta `@Update` yok: bir kayıt ya gönderilir ve silinir, ya da deneme
 * sayacı artar. Kaydın hangi satırı işaret ettiği hiç değişmez.
 */
@Dao
interface SyncOutboxDao {

    /**
     * Satırı kuyruğa alır.
     *
     * `IGNORE`: aynı satır için bekleyen kayıt varsa yenisi eklenmez ve
     * **eskisi korunur**. Kayıt yalnızca bir işaretçi olduğu için ikinci kayıt
     * hiçbir bilgi taşımazdı; eskiyi korumak da satırın ilk değiştiği anı
     * ([SyncOutboxEntity.enqueuedAtMs]) saklar, böylece FIFO sırası bozulmaz.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(entry: SyncOutboxEntity)

    /** Gönderilecek ilk [limit] kayıt, en eskiden yeniye. */
    @Query("""
        SELECT * FROM sync_outbox
        WHERE tenantId = :tenantId
        ORDER BY enqueuedAtMs ASC, id ASC
        LIMIT :limit
    """)
    suspend fun peek(tenantId: String, limit: Int): List<SyncOutboxEntity>

    /**
     * Gönderimi başarılı olan kaydı kuyruktan düşürür.
     *
     * [enqueuedAtMs] koşulu bilinçli: gönderim sürerken aynı satır tekrar
     * değişmişse kuyruktaki kayıt yenilenmiş olur ve bu silme **eşleşmez**,
     * dolayısıyla yeni değişiklik kuyrukta kalır. Koşulsuz silseydik gönderim
     * penceresinde yapılan değişiklik sessizce kaybolurdu.
     */
    @Query("DELETE FROM sync_outbox WHERE id = :id AND enqueuedAtMs = :enqueuedAtMs")
    suspend fun removeIfUnchanged(id: String, enqueuedAtMs: Long): Int

    /** Başarısız denemeyi kaydeder; kayıt kuyrukta kalır. */
    @Query("""
        UPDATE sync_outbox
        SET attemptCount = attemptCount + 1,
            lastAttemptAtMs = :nowMs,
            lastError = :error
        WHERE id = :id
    """)
    suspend fun recordFailure(id: String, nowMs: Long, error: String?)

    /**
     * Bu tabloda gönderim bekleyen satırların kimlikleri.
     *
     * Çekme tarafı bunları atlıyor: yerelde henüz yukarı çıkmamış bir değişiklik
     * varsa sunucudaki hâli eskidir ve üzerine yazmak kullanıcının az önce
     * yaptığı değişikliği silmek olurdu.
     */
    @Query("""
        SELECT entityId FROM sync_outbox
        WHERE tenantId = :tenantId AND entityTable = :entityTable
    """)
    suspend fun pendingIds(tenantId: String, entityTable: String): List<String>

    /** Bekleyen kayıt sayısı — "senkronize edilmemiş değişiklik var" göstergesi. */
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE tenantId = :tenantId")
    fun observePendingCount(tenantId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE tenantId = :tenantId")
    suspend fun pendingCount(tenantId: String): Int
}
