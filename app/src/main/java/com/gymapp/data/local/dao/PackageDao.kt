package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.PackageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {
    @Query("SELECT * FROM gym_packages ORDER BY name ASC")
    fun getAllPackages(): Flow<List<PackageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: PackageEntity)

    @Delete
    suspend fun deletePackage(pkg: PackageEntity)

    @Query("SELECT * FROM gym_packages WHERE id = :id")
    suspend fun getPackageById(id: Long): PackageEntity?
}
