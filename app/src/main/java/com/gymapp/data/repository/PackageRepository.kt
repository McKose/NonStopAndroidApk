package com.gymapp.data.repository

import com.gymapp.data.local.dao.PackageDao
import com.gymapp.data.local.entity.PackageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageRepository @Inject constructor(
    private val packageDao: PackageDao
) {
    fun getAllPackages(): Flow<List<PackageEntity>> = packageDao.getAllPackages()

    suspend fun insertPackage(pkg: PackageEntity) = packageDao.insertPackage(pkg)

    suspend fun deletePackage(pkg: PackageEntity) = packageDao.deletePackage(pkg)

    suspend fun getPackageById(id: Long) = packageDao.getPackageById(id)
}
