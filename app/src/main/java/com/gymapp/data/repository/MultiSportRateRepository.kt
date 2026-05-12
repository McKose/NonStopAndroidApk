package com.gymapp.data.repository

import com.gymapp.data.local.dao.MultiSportRateDao
import com.gymapp.data.local.entity.MultiSportRateEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultiSportRateRepository @Inject constructor(
    private val dao: MultiSportRateDao
) {
    fun observeAll(): Flow<List<MultiSportRateEntity>> = dao.getAll()
    suspend fun getCurrent(): MultiSportRateEntity? = dao.getCurrent()
    suspend fun getRateAt(atMs: Long): MultiSportRateEntity? = dao.getRateAt(atMs)

    /** Yeni tutar uygulanır; mevcut kayıt "supersededByMs" ile kapatılır. */
    suspend fun updateRate(newAmount: Double, note: String? = null): Long =
        dao.supersede(newAmount, note)
}
