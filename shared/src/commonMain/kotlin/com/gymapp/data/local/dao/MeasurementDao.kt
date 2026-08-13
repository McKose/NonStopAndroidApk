package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.gymapp.data.local.entity.MeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    /** Silinmiş (tombstone) kayıtlar listelenmez. */
    @Query("""
        SELECT * FROM measurements
        WHERE tenantId = :tenantId AND memberId = :memberId AND deletedAtMs IS NULL
        ORDER BY dateMs DESC
    """)
    fun observeForMember(tenantId: String, memberId: String): Flow<List<MeasurementEntity>>

    /**
     * Tek kayıt — senkronizasyon gönderiminin okuduğu yer.
     *
     * Tombstone kayıtlar da dönüyor: silme de gönderilmesi gereken bir
     * değişiklik, süzülseydi silinen satır sunucuda sonsuza kadar canlı kalırdı.
     */
    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: String): MeasurementEntity?

    @Insert
    suspend fun insert(measurement: MeasurementEntity)

    /**
     * Kaydı fiziksel olarak silmez; tombstone işaretler.
     *
     * Silmenin de senkronize olması gerekiyor: fiziksel silme, diğer cihazda
     * kaydın "hiç silinmemiş" gibi geri gelmesine yol açardı.
     */
    @Query("UPDATE measurements SET deletedAtMs = :nowMs, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun softDelete(id: String, nowMs: Long)

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
    suspend fun upsertFromServer(row: MeasurementEntity)
}
