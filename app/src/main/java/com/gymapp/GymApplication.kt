package com.gymapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt için `@HiltAndroidApp` zorunlu.
 * WorkManager Hilt ile manuel başlatılıyor (auto-init manifest'te devre dışı).
 *
 * KALDIRILDI: `MemberExpirationWorker`. Üyelik durumu artık okuma anında bitiş
 * tarihinden **türetiliyor** ([com.gymapp.domain.Membership.stateOf]); gecelik iş
 * hem gereksizdi hem de süresi dolan üyeyi kalıcı olarak arşivliyordu. Fabrika
 * yerinde kalıyor: Faz 4'teki senkronizasyon kuyruğu (outbox) bir işçi kullanacak.
 */
@HiltAndroidApp
class GymApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
