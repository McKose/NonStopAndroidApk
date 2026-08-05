package com.gymapp.data.local.db

import android.content.Context
import androidx.room.Room

/**
 * Android tarafında veritabanını açar.
 *
 * Dosya uygulamanın kendi veritabanı dizininde tutulur; yolu Room'a tam olarak
 * vermek KMP'deki iOS karşılığıyla simetriyi koruyor.
 */
fun createGymDatabase(context: Context): GymDatabase {
    val dbFile = context.getDatabasePath(GYM_DATABASE_NAME)
    return Room.databaseBuilder<GymDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
    ).buildGymDatabase()
}
