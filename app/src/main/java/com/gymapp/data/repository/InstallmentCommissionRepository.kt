package com.gymapp.data.repository

import com.gymapp.data.local.dao.InstallmentCommissionDao
import com.gymapp.data.local.entity.InstallmentCommissionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallmentCommissionRepository @Inject constructor(
    private val dao: InstallmentCommissionDao
) {
    fun observeAll(): Flow<List<InstallmentCommissionEntity>> = dao.getAll()

    suspend fun getRate(count: Int): Double = dao.getRate(count) ?: 0.0

    suspend fun upsert(count: Int, ratePercent: Double) {
        require(count in 1..12) { "Taksit sayısı 1-12 arası olmalı" }
        require(ratePercent in 0.0..100.0) { "Oran 0-100 arasında olmalı" }
        dao.upsert(InstallmentCommissionEntity(count, ratePercent))
    }

    suspend fun upsertAll(entities: List<InstallmentCommissionEntity>) = dao.upsertAll(entities)
}
