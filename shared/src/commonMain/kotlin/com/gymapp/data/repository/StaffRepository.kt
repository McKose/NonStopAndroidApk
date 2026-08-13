package com.gymapp.data.repository

import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.auth.requireTenantId
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.db.inTransaction
import com.gymapp.data.local.db.isUniqueConstraintViolation
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncTable
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
    private val database: GymDatabase,
    private val staffDao: StaffDao,
    private val syncQueue: SyncQueue,
    private val tenants: TenantProvider,
) {
    /**
     * Çalışılan salon; her sorgu ve her yazma bununla süzülüyor.
     *
     * Sabit `"default"` değeri kaldırıldı: salon kimliği artık oturumdan
     * geliyor ve sunucudaki `gyms.id` ile aynı. Sabit değer, tek salonlu
     * kurulumda çalışıyor gibi görünüp sunucuya gönderimde reddedilirdi —
     * `tenant_id` orada `uuid`.
     */
    private val tenantId: String
        get() = tenants.requireTenantId()

    fun getAllStaff(): Flow<List<StaffEntity>> = staffDao.getAllStaff(tenantId)

    suspend fun getById(staffId: String): StaffEntity? = staffDao.getStaffById(staffId)

    // KALDIRILDI: `getByNickname`. Tek çağıranı eski giriş akışıydı; kimlik
    // doğrulama Supabase Auth'a taşınınca kullanıcı adıyla arama gerekmiyor.
    // DAO'daki sorgu duruyor: personel yönetiminde kullanıcı adı çakışması
    // hâlâ kontrol ediliyor.

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
        authUserId: String? = null,
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

        // Supabase kullanıcı kimliği bir uuid; biçim burada doğrulanıyor çünkü
        // yanlış yazılmış bir değerin tek belirtisi "giriş yapıyorum ama kendi
        // derslerimi göremiyorum" olurdu — hiçbir hata mesajı üretmeyen,
        // teşhisi zor bir durum.
        val normalizedAuthUserId = authUserId?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        require(normalizedAuthUserId == null || UUID_REGEX.matches(normalizedAuthUserId)) {
            "Supabase kullanıcı kimliği geçerli bir uuid olmalı."
        }

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
        database.inTransaction {
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

            val savedId = try {
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
                            authUserId = normalizedAuthUserId,
                            // NOT: artık okunmuyor; kimlik doğrulama Supabase Auth'ta.
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
                            // Boş bırakıldığında mevcut bağlantı korunuyor:
                            // düzenleme ekranında alanı silmek, kişinin hesap
                            // bağlantısını farkında olmadan koparmamalı.
                            authUserId = normalizedAuthUserId ?: existing.authUserId,
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

            syncQueue.enqueue(SyncTable.STAFF, savedId, tenantId, nowMs)
            savedId
        }
    }

    /** Silme de bir değişiklik: tombstone satır kuyruğa girmezse silme senkronize olmaz. */
    suspend fun deleteStaff(staffId: String) {
        val nowMs = Now.epochMillis()
        database.inTransaction {
            staffDao.softDelete(staffId, nowMs)
            syncQueue.enqueue(SyncTable.STAFF, staffId, tenantId, nowMs)
        }
    }

    private companion object {
        /** 8-4-4-4-12 onaltılık; Supabase kullanıcı kimliklerinin biçimi. */
        val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        const val DEFAULT_PASSWORD = "123"
    }
}
