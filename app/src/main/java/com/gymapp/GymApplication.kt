package com.gymapp

import android.app.Application
import com.gymapp.di.appModule
import com.gymapp.di.databaseModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Bağımlılık grafiği burada kuruluyor.
 *
 * Hilt yerine Koin: Hilt yalnızca JVM/Android'de çalışan bir ek açıklama
 * işlemcisine dayanıyor, dolayısıyla repository katmanı ortak koda (KMP)
 * taşınamıyordu. Koin sıradan Kotlin kodu olduğu için iOS tarafından da
 * kullanılabiliyor.
 *
 * KALDIRILDI: WorkManager kurulumu. Tek işçi olan `MemberExpirationWorker`
 * kalktığında (üyelik durumu artık okuma anında bitiş tarihinden türetiliyor)
 * geriye hiçbir işçisi olmayan bir fabrika kalmıştı. Faz 4'teki senkronizasyon
 * kuyruğu gerçekten bir işçiye ihtiyaç duyduğunda onu kullanan kodla birlikte
 * geri gelecek.
 */
class GymApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@GymApplication)
            modules(databaseModule, appModule)
        }
    }
}
