package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.InstallmentCommissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentCommissionDao {

    @Query("SELECT * FROM installment_commissions ORDER BY installmentCount ASC")
    fun getAll(): Flow<List<InstallmentCommissionEntity>>

    @Query("SELECT ratePercent FROM installment_commissions WHERE installmentCount = :count LIMIT 1")
    suspend fun getRate(count: Int): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InstallmentCommissionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<InstallmentCommissionEntity>)

    @Query("SELECT COUNT(*) FROM installment_commissions")
    suspend fun count(): Int
}
