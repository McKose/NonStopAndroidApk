package com.gymapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gymapp.worker.MemberExpirationWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt için @HiltAndroidApp zorunlu.
 * WorkManager'ı Hilt ile manuel başlatıyoruz (auto-init devre dışı).
 */
@HiltAndroidApp
class GymApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Üye bitiş tarihi kontrolünü günde 1 kez çalıştır
        MemberExpirationWorker.schedule(this)
    }
}
