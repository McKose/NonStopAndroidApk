package com.gymapp.data.local.db

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS tarafında veritabanını açar.
 *
 * Dosya `Documents` dizininde: iCloud yedeklemesine dahil olur ve uygulama
 * güncellemelerinde korunur. (`Caches` altında olsaydı sistem baskı altında
 * silebilirdi.)
 */
fun createGymDatabase(): GymDatabase =
    Room.databaseBuilder<GymDatabase>(
        name = "${documentDirectory()}/$GYM_DATABASE_NAME",
    ).buildGymDatabase()

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(url?.path) { "Documents dizini bulunamadı." }
}
