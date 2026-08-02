package com.gymapp.data.repository

import com.gymapp.data.local.dao.PostureCommentDao
import com.gymapp.data.local.entity.PostureCommentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostureCommentRepository @Inject constructor(
    private val dao: PostureCommentDao
) {
    fun getForMember(memberId: Long): Flow<List<PostureCommentEntity>> = dao.getForMember(memberId)
    suspend fun insert(comment: PostureCommentEntity): Long = dao.insert(comment)
    suspend fun update(comment: PostureCommentEntity) = dao.update(comment)
    suspend fun delete(comment: PostureCommentEntity) = dao.delete(comment)
}
