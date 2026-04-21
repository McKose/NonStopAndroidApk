package com.gymapp.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.PackageEntity

@Database(
    entities = [
        MemberEntity::class,
        PackageEntity::class
    ],
    version = 1,
    exportSchema = true   // migrations için şema dışa aktarılıyor
)
abstract class GymDatabase : RoomDatabase() {

    abstract fun memberDao(): MemberDao

    companion object {
        @Volatile private var INSTANCE: GymDatabase? = null

        fun getInstance(context: Context): GymDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GymDatabase::class.java,
                    "gym_database"
                )
                    .fallbackToDestructiveMigration() // dev aşaması için; prod'da Migration yazılmalı
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
