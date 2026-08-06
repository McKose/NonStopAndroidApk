package com.gymapp.data.repository

import com.gymapp.data.local.dao.PackageDao
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.domain.Now
import com.gymapp.domain.Ids
import com.gymapp.domain.Money
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageRepository @Inject constructor(
    private val packageDao: PackageDao
) {
    private val tenantId = Ids.DEFAULT_TENANT

    fun getAllPackages(): Flow<List<PackageEntity>> = packageDao.getAllPackages(tenantId)

    suspend fun getPackageById(id: String): PackageEntity? = packageDao.getPackageById(id)

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

        val nowMs = Now.epochMillis()
        val existing = packageId?.let { packageDao.getPackageById(it) }

        if (existing == null) {
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
                    updatedAtMs = nowMs,
                )
            )
            existing.id
        }
    }

    /** Tombstone siler; pakete bağlı üyelerin geçmişi öksüz kalmaz. */
    suspend fun deletePackage(packageId: String) =
        packageDao.softDelete(packageId, Now.epochMillis())
}
