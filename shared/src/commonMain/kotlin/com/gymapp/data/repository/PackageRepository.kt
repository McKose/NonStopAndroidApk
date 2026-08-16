package com.gymapp.data.repository

import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.auth.requireTenantId
import com.gymapp.data.local.dao.PackageDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.db.inTransaction
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.Now
import com.gymapp.domain.Ids
import com.gymapp.domain.Money
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.flow.Flow

class PackageRepository(
    private val database: GymDatabase,
    private val packageDao: PackageDao,
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

    fun getAllPackages(): Flow<List<PackageEntity>> = packageDao.getAllPackages(tenantId)

    /**
     * Ekranlar için tek paket; **silinmişler hariç**.
     *
     * Süzgeç artık burada, DAO'da değil. DAO ham satırı veriyor çünkü gönderim
     * yolunun silinmiş satıra da erişmesi gerekiyor (bkz.
     * [com.gymapp.data.local.dao.PackageDao.getPackageById]); hangi satırın
     * kullanıcıya gösterileceği ise bir alan kuralı ve yeri burası.
     *
     * Süzgeç DAO'da kaldığı sürece ikisi aynı anda sağlanamıyordu ve gönderim
     * tarafı sessizce kaybediyordu.
     */
    suspend fun getPackageById(id: String): PackageEntity? =
        packageDao.getPackageById(id)?.takeIf { it.deletedAtMs == null }

    /**
     * Paketi kaydeder.
     *
     * Kimlik ve zaman damgaları burada üretilir; UI katmanı entity kurmaz.
     *
     * @param packageId `null` ise yeni paket oluşturulur.
     * @param sessionCount `null` ise sınırsız (abonman) paket.
     */
    suspend fun savePackage(
        packageId: String? = null,
        name: String,
        type: TrainingType,
        category: PackageCategory,
        basePrice: Money,
        validityDays: Int,
        sessionCount: Int?,
    ): Result<String> = runCatching {
        require(name.isNotBlank()) { "Paket adı boş olamaz." }
        require(validityDays > 0) { "Geçerlilik süresi en az 1 gün olmalıdır." }
        require(sessionCount == null || sessionCount > 0) { "Seans sayısı sıfırdan büyük olmalıdır." }

        database.inTransaction {
            val nowMs = Now.epochMillis()
            val existing = packageId?.let { packageDao.getPackageById(it) }

            val savedId = if (existing == null) {
                val id = packageId ?: Ids.new()
                packageDao.insertPackage(
                    PackageEntity(
                        id = id,
                        tenantId = tenantId,
                        name = name.trim(),
                        type = type,
                        category = category,
                        validityDays = validityDays,
                        sessionCount = sessionCount,
                        basePriceMinor = basePrice.coerceNonNegative().minor,
                        createdAtMs = nowMs,
                        updatedAtMs = nowMs,
                    )
                )
                id
            } else {
                packageDao.updatePackage(
                    existing.copy(
                        name = name.trim(),
                        type = type,
                        category = category,
                        validityDays = validityDays,
                        sessionCount = sessionCount,
                        basePriceMinor = basePrice.coerceNonNegative().minor,
                        // Silinmiş bir kimliğe kayıt = **canlandırma**; üye
                        // tarafındaki desenin aynısı (`registerMember`).
                        //
                        // Önceden DAO silinmişleri süzdüğü için `existing` null
                        // gelir, `insertPackage` denenir ve birincil anahtar
                        // çakışması ham SQLite hatası olarak dışarı çıkardı —
                        // `StaffRepository` bu sınıf hatayı çevirirken burada
                        // çevrilmiyordu. Artık o yol hiç oluşmuyor.
                        deletedAtMs = null,
                        updatedAtMs = nowMs,
                    )
                )
                existing.id
            }

            syncQueue.enqueue(SyncTable.PACKAGES, savedId, tenantId, nowMs)
            savedId
        }
    }

    /**
     * Tombstone siler; pakete bağlı üyelerin geçmişi öksüz kalmaz.
     *
     * Silme de bir değişiklik: tombstone satır kuyruğa girmezse silme sunucuya
     * hiç gitmez ve kayıt diğer cihazlarda yaşamaya devam eder.
     */
    suspend fun deletePackage(packageId: String): Result<Unit> = runCatching {
        val nowMs = Now.epochMillis()
        database.inTransaction {
            packageDao.softDelete(packageId, nowMs)
            syncQueue.enqueue(SyncTable.PACKAGES, packageId, tenantId, nowMs)
        }
    }
}
