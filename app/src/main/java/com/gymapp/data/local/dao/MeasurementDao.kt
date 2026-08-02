package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.MeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements WHERE memberId = :memberId ORDER BY dateMs DESC")
    fun getMeasurementsForMember(memberId: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: Long): MeasurementEntity?

    @Query("SELECT * FROM measurements WHERE memberId = :memberId ORDER BY dateMs DESC LIMIT 1")
    suspend fun getLatestForMember(memberId: Long): MeasurementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: MeasurementEntity): Long

    @Update
    suspend fun updateMeasurement(measurement: MeasurementEntity)

    @Delete
    suspend fun deleteMeasurement(measurement: MeasurementEntity)
}
