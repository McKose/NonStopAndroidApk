package com.gymapp.data.repository

import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.Ids
import com.gymapp.domain.Money
import com.gymapp.domain.StaffRole
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Personel kayıtları.
 *
 * Daha önce personel ekranı DAO'ya doğrudan erişip [StaffEntity]'yi kendisi
 * kuruyordu; kimlik, zaman damgası ve birim dönüşümü (yüzde → baz puan, TL → kuruş)
 * artık tek noktada.
 */
@Singleton
class StaffRepository @Inject constructor(
    private val staffDao: StaffDao
) {
    private val tenantId = Ids.DEFAULT_TENANT

    fun getAllStaff(): Flow<List<StaffEntity>> = staffDao.getAllStaff(tenantId)

    suspend fun getById(staffId: String): StaffEntity? = staffDao.getStaffById(staffId)

    suspend fun getByNickname(nickname: String): StaffEntity? =
        staffDao.getStaffByNickname(tenantId, nickname.trim())

    /**
     * Personeli kaydeder.
     *
     * @param staffId `null` ise yeni personel oluşturulur.
     * @param commissionBasisPoints hakediş oranı baz puan cinsinden (4000 = %40).
     */
    suspend fun saveStaff(
        staffId: String? = null,
        fullName: String,
        title: String,
        role: StaffRole,
        branch: String,
        commissionBasisPoints: Int,
        monthlySalary: Money,
        phone: String,
        nickname: String,
        password: String? = null,
        isActive: Boolean = true,
    ): Result<String> = runCatching {
        require(fullName.isNotBlank()) { "Ad soyad boş olamaz." }
        require(phone.isNotBlank()) { "Telefon boş olamaz." }

        val normalizedNickname = nickname.trim()
        require(normalizedNickname.isNotBlank()) { "Kullanıcı adı boş olamaz." }

        // Kullanıcı adı tenant içinde tekil; kontrolü burada yapıyoruz ki anlaşılır
        // mesaj dönebilelim, ama son söz veritabanındaki UNIQUE index'te.
        //
        // Silinmiş kayıtlar da aranıyor: tombstone satırları index'te durduğu için
        // görülmezlerse kayıt anlaşılmaz bir kısıt hatasıyla düşerdi.
        val clash = staffDao.findByNicknameIncludingDeleted(tenantId, normalizedNickname)
        if (clash != null && clash.id != staffId) {
            throw IllegalArgumentException(
                if (clash.deletedAtMs == null) {
                    "Bu kullanıcı adı zaten alınmış."
                } else {
                    "Bu kullanıcı adı silinmiş bir personele ait; farklı bir ad seçin."
                }
            )
        }

        val nowMs = System.currentTimeMillis()
        val existing = staffId?.let { staffDao.getStaffById(it) }

        try {
            if (existing == null) {
                val id = staffId ?: Ids.new()
                staffDao.insertStaff(
                    StaffEntity(
                        id = id,
                        tenantId = tenantId,
                        fullName = fullName.trim(),
                        title = title.trim(),
                        role = role,
                        branch = branch.trim(),
                        commissionBasisPoints = commissionBasisPoints,
                        monthlySalaryMinor = monthlySalary.coerceNonNegative().minor,
                        phone = phone.trim(),
                        nickname = normalizedNickname,
                        // NOT (Faz 4): şifre hash'lenmeli; kimlik doğrulama sunucuya taşınacak.
                        password = password ?: DEFAULT_PASSWORD,
                        isActive = isActive,
                        createdAtMs = nowMs,
                        updatedAtMs = nowMs,
                    )
                )
                id
            } else {
                staffDao.updateStaff(
                    existing.copy(
                        fullName = fullName.trim(),
                        title = title.trim(),
                        role = role,
                        branch = branch.trim(),
                        commissionBasisPoints = commissionBasisPoints,
                        monthlySalaryMinor = monthlySalary.coerceNonNegative().minor,
                        phone = phone.trim(),
                        nickname = normalizedNickname,
                        password = password ?: existing.password,
                        isActive = isActive,
                        updatedAtMs = nowMs,
                    )
                )
                existing.id
            }
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw IllegalArgumentException("Bu kullanıcı adı zaten alınmış.", e)
        }
    }

    suspend fun deleteStaff(staffId: String) =
        staffDao.softDelete(staffId, System.currentTimeMillis())

    private companion object {
        const val DEFAULT_PASSWORD = "123"
    }
}
