package com.gymapp.data.repository

import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.db.isUniqueConstraintViolation
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.Now
import com.gymapp.domain.Ids
import com.gymapp.domain.Money
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.Rate
import com.gymapp.domain.StaffRole
import kotlinx.coroutines.flow.Flow

/**
 * Personel kayıtları.
 *
 * Daha önce personel ekranı DAO'ya doğrudan erişip [StaffEntity]'yi kendisi
 * kuruyordu; kimlik, zaman damgası ve birim dönüşümü (yüzde → baz puan, TL → kuruş)
 * artık tek noktada.
 */
class StaffRepository(
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

        // Oran aralığı burada doğrulanıyor, ekranda değil. Şu an tek çağrı yeri
        // `Rate.ofPercent` ile zaten 0–100 arasına kırpıyor; kural ekranda kalırsa
        // ikinci bir çağrı yeri eklendiğinde sessizce delinir. Aralık dışı bir
        // değer randevu tamamlanırken hatalı hakediş yazardı.
        require(commissionBasisPoints in 0..Rate.SCALE) {
            "Hakediş oranı %0 ile %100 arasında olmalıdır."
        }

        val normalizedNickname = nickname.trim()
        require(normalizedNickname.isNotBlank()) { "Kullanıcı adı boş olamaz." }

        // Numara cep numarasıysa üyelerdeki gibi E.164'e çevrilir; değilse (sabit
        // hat olabilir) olduğu gibi saklanır. Üye tarafı cep numarası **zorunlu**
        // kılıyor çünkü orada tekillik kısıtı numaraya bağlı; personelde tekillik
        // kullanıcı adında olduğu için aynı zorunluluk getirilmedi.
        val normalizedPhone = PhoneNumber.normalizeTr(phone) ?: phone.trim()

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

        val nowMs = Now.epochMillis()
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
                        phone = normalizedPhone,
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
                        phone = normalizedPhone,
                        nickname = normalizedNickname,
                        password = password ?: existing.password,
                        isActive = isActive,
                        updatedAtMs = nowMs,
                    )
                )
                existing.id
            }
        } catch (e: Exception) {
            if (e.isUniqueConstraintViolation()) {
                throw IllegalArgumentException("Bu kullanıcı adı zaten alınmış.", e)
            }
            throw e
        }
    }

    suspend fun deleteStaff(staffId: String) =
        staffDao.softDelete(staffId, Now.epochMillis())

    private companion object {
        const val DEFAULT_PASSWORD = "123"
    }
}
