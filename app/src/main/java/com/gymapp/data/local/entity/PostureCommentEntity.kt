package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Üye postür / duruş gözlem notu. Her yorum tarihlidir, böylelikle tarihsel
 * izleme yapılabilir (örn. "reformer dersleri sonrasında kamburluk düzeldi").
 */
@Entity(
    tableName = "posture_comments",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["memberId"]), Index(value = ["dateMs"])]
)
data class PostureCommentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memberId: Long,
    val dateMs: Long = System.currentTimeMillis(),
    val comment: String,
    val authorStaffId: Long? = null
)
