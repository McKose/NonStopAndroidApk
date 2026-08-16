package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.StaffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {

    @Query("""
        SELECT * FROM staff
        WHERE tenantId = :tenantId AND deletedAtMs IS NULL
        ORDER BY fullName ASC
    """)
    fun getAllStaff(tenantId: String): Flow<List<StaffEntity>>

    /**
     * `REPLACE` yerine `ABORT`: aynı kullanıcı adına sahip bir kayıt varsa
     * sessizce üzerine yazmak yerine hata döner, çağıran anlamlı mesaj üretir.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStaff(staff: StaffEntity)

    @Update
    suspend fun updateStaff(staff: StaffEntity)

    /**
     * Kimliğe göre tekil okuma — tombstone **filtrelenmez**.
     *
     * Yabancı anahtar çözümlemesi; filtrelenseydi eğitmeni sonradan silinmiş bir
     * randevu tamamlanamaz, dolayısıyla geçmiş hakediş hiç yazılamazdı.
     */
    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getStaffById(id: String): StaffEntity?

    // KALDIRILDI: `getStaffByNickname`. Tek amacı kullanıcı adıyla giriş
    // yapmaktı; kimlik doğrulama Supabase Auth'a taşındı. Çakışma kontrolü
    // aşağıdaki `findByNicknameIncludingDeleted` ile yapılıyor ve o duruyor.

    /**
     * Giriş yapan Supabase kullanıcısının personel kaydı — yalnızca **aktif**.
     *
     * Kimlik doğrulama sunucuda yapıldığı için giriş anında elimizde yalnızca
     * `auth.users.id` oluyor; randevu ve hakediş kayıtları ise yerel `staff.id`
     * değerine bakıyor. Bu sorgu iki kimliği birbirine bağlıyor.
     *
     * Silinmiş personel dönmüyor: hesabı hâlâ açık olan eski bir çalışan giriş
     * yapabilir ama kendini personel listesinde bulamamalı.
     */
    @Query("""
        SELECT * FROM staff
        WHERE tenantId = :tenantId AND authUserId = :authUserId AND deletedAtMs IS NULL
        LIMIT 1
    """)
    suspend fun findByAuthUserId(tenantId: String, authUserId: String): StaffEntity?

    /**
     * Aynı bağlantının **akış** hâli; oturum boyunca dinlenir.
     *
     * Tek seferlik okuma yetmiyordu: bağlantı girişte bir kez kuruluyor,
     * kurulamazsa oturum boyunca kurulamıyordu. Oysa personel kartı sonradan
     * doldurulabiliyor ve satır senkronizasyon turuyla sonradan inebiliyor —
     * ikisinde de kullanıcı çıkıp yeniden girene kadar kendi derslerini
     * göremiyordu. Bkz. [com.gymapp.data.auth.CurrentUser].
     */
    @Query("""
        SELECT * FROM staff
        WHERE tenantId = :tenantId AND authUserId = :authUserId AND deletedAtMs IS NULL
        LIMIT 1
    """)
    fun observeByAuthUserId(tenantId: String, authUserId: String): Flow<StaffEntity?>

    /**
     * Kullanıcı adı çakışma kontrolü — silinmiş kayıtlar **dahil**.
     *
     * UNIQUE index tombstone satırlarını da kapsıyor; silinmiş bir personelin
     * kullanıcı adı görülmezse kayıt anlaşılmaz bir kısıt hatasıyla düşerdi.
     */
    @Query("""
        SELECT * FROM staff
        WHERE tenantId = :tenantId AND nickname = :nickname
        LIMIT 1
    """)
    suspend fun findByNicknameIncludingDeleted(tenantId: String, nickname: String): StaffEntity?

    /** Tombstone silme; personele bağlı randevular ve hakediş kayıtları öksüz kalmaz. */
    @Query("UPDATE staff SET deletedAtMs = :nowMs, updatedAtMs = :nowMs WHERE id = :id")
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
    suspend fun upsertFromServer(row: StaffEntity)
}
