package com.gymapp.data.repository

import com.gymapp.data.local.dao.MemberPackageDao
import com.gymapp.data.local.entity.MemberPackageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberPackageRepository @Inject constructor(
    private val dao: MemberPackageDao
) {
    fun getAllForMember(memberId: Long): Flow<List<MemberPackageEntity>> = dao.getAllForMember(memberId)
    fun getActiveForMember(memberId: Long): Flow<List<MemberPackageEntity>> = dao.getActiveForMember(memberId)
    fun getHistoryForMember(memberId: Long): Flow<List<MemberPackageEntity>> = dao.getHistoryForMember(memberId)

    suspend fun getActiveForMemberOnce(memberId: Long) = dao.getActiveForMemberOnce(memberId)
    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun insert(entity: MemberPackageEntity): Long = dao.insert(entity)
    suspend fun update(entity: MemberPackageEntity) = dao.update(entity)
    suspend fun decrementSession(packageId: Long) = dao.decrementSession(packageId)
    suspend fun expireOverdue(): Int = dao.expireOverduePackages()
}
