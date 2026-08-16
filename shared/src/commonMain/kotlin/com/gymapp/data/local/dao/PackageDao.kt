package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.PackageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {

    @Query("""
        SELECT * FROM gym_packages
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
        ORDER BY name ASC
    """)
    fun getAllPackages(tenantId: String): Flow<List<PackageEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPackage(pkg: PackageEntity)

    @Update
    suspend fun updatePackage(pkg: PackageEntity)

    /**
     * Kimliğe göre tek paket — **silinmişler dahil**.
     *
     * Süzgeç bilinçli olarak yok. Burada `AND deletedAtMs IS NULL` vardı ve
     * projedeki dokuz kimlik sorgusundan tek süzeni buydu; gönderim yolu
     * ([LocalRowPayloadProvider]) tam bunu kullandığı için silinen paketin
     * içeriği üretilemiyor, gönderim `Permanent` hatayla duruyordu.
     *
     * Sonucu sessizdi: silme sunucuya hiç gitmiyor, paket panelde ve diğer
     * cihazlarda canlı kalıyor, personel satmaya devam ediyordu. Kuyruk kaydı da
     * hiç düşmediği için çekme tarafı o paketi kalıcı olarak atlıyor ve
     * sunucudaki hâli geri de inemiyordu.
     *
     * Aynı kural `MeasurementDao.getById` üzerinde de yazılı: "süzülseydi silinen
     * satır sunucuda sonsuza kadar canlı kalırdı."
     *
     * Çağıranın silinmişi elemesi gerekiyorsa bunu kendisi yapar; liste sorgusu
     * ([getAllPackages]) zaten eliyor.
     */
    @Query("SELECT * FROM gym_packages WHERE id = :id")
    suspend fun getPackageById(id: String): PackageEntity?

    /**
     * Tombstone silme.
     *
     * Paket fiziksel olarak silinirse ona bağlı üyelerin `activePackageId`'si
     * öksüz kalıyor ve paket adı ekranda kayboluyordu.
     */
    @Query("UPDATE gym_packages SET deletedAtMs = :nowMs, updatedAtMs = :nowMs WHERE id = :id")
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
    suspend fun upsertFromServer(row: PackageEntity)
}
