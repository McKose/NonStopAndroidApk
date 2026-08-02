package com.gymapp.data.local.dao

import androidx.room.*
import com.gymapp.data.local.entity.PostureCommentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostureCommentDao {

    @Query("SELECT * FROM posture_comments WHERE memberId = :memberId ORDER BY dateMs DESC")
    fun getForMember(memberId: Long): Flow<List<PostureCommentEntity>>

    @Insert
    suspend fun insert(comment: PostureCommentEntity): Long

    @Update
    suspend fun update(comment: PostureCommentEntity)

    @Delete
    suspend fun delete(comment: PostureCommentEntity)
}
