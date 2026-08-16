package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
     * Aynı satır için bekleyen kayıt varsa **yenisi eklenmiyor**: kayıt yalnızca
     * bir işaretçi, ikinci kayıt hiçbir bilgi taşımazdı. `enqueuedAtMs` de
     * korunuyor — satırın ilk değiştiği anı gösteriyor ve FIFO sırası buna
     * dayanıyor. Tazelenseydi sık düzenlenen bir satır sürekli kuyruğun sonuna
     * atılıp aç kalırdı.
     *
     * Ama var olan kaydın [SyncOutboxEntity.revision] değeri **artıyor**. Bu,
     * "gönderim başladıktan sonra satır tekrar değişti" bilgisinin taşındığı yer
     * ([removeIfUnchanged]).
     */
    @Transaction
    suspend fun enqueue(entry: SyncOutboxEntity) {
        // `insertIfAbsent` çakışmada -1 dönüyor; o durumda kayıt zaten var
        // demektir ve tek yapılacak şey revizyonu artırmak.
        if (insertIfAbsent(entry) == -1L) {
            bumpRevision(entry.tenantId, entry.entityTable, entry.entityId)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: SyncOutboxEntity): Long

    /** Bekleyen kaydın revizyonunu artırır: satır bir kez daha değişti. */
    @Query("""
        UPDATE sync_outbox
        SET revision = revision + 1
        WHERE tenantId = :tenantId AND entityTable = :entityTable AND entityId = :entityId
    """)
    suspend fun bumpRevision(tenantId: String, entityTable: String, entityId: String): Int

    /** Gönderilecek ilk [limit] kayıt, en eskiden yeniye. */
    @Query("""
        SELECT * FROM sync_outbox
        WHERE tenantId = :tenantId
        ORDER BY enqueuedAtMs ASC, id ASC
        LIMIT :limit
    """)
    suspend fun peek(tenantId: String, limit: Int): List<SyncOutboxEntity>

    /**
     * Gönderimi başarılı olan kaydı kuyruktan düşürür — **satır o sırada tekrar
     * değişmediyse**.
     *
     * Gönderim anlık değil: içerik okunur, istek gider, yanıt gelir. O pencerede
     * kullanıcı aynı satırı bir kez daha değiştirirse gönderilen içerik eskimiş
     * olur ve kaydın kuyrukta kalması gerekir; aksi hâlde ikinci değişiklik
     * sunucuya hiç gitmez.
     *
     * Ayırt edici [revision]. Önceden `enqueuedAtMs` kullanılıyordu ve buradaki
     * açıklama "tekrar değişmişse kayıt yenilenmiş olur" diyordu — ama
     * [enqueue] `IGNORE` ile eskiyi koruyor, yani damga hiç yenilenmiyordu. İki
     * KDoc birbiriyle çelişiyordu ve `IGNORE` kazanıyordu: koşul **her zaman**
     * eşleşiyor, koruma hiç çalışmıyordu.
     *
     * Sonucu sessizdi: kullanıcı "eşitlendi" görüyor, bekleyen sayaç sıfıra
     * iniyor, ama ikinci değişiklik kayboluyordu. Çekme tarafı da o satırı artık
     * korumadığı için (`pendingIds`) sunucudaki eski hâl üzerine yazıyordu.
     */
    @Query("DELETE FROM sync_outbox WHERE id = :id AND revision = :revision")
    suspend fun removeIfUnchanged(id: String, revision: Int): Int

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
