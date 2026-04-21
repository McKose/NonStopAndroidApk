package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.MeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements WHERE memberId = :memberId ORDER BY dateMs DESC")
    fun getMeasurementsForMember(memberId: Long): Flow<List<MeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: MeasurementEntity): Long

    @Delete
    suspend fun deleteMeasurement(measurement: MeasurementEntity)
}
