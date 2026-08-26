package com.gymapp

import android.app.Application
import com.gymapp.arayuz.ekranModelleriModulu
import com.gymapp.data.auth.AndroidSessionStore
import com.gymapp.data.local.db.createGymDatabase
import com.gymapp.data.local.preferences.AndroidTercihler
import com.gymapp.data.sync.WorkManagerSenkronizasyonu
import com.gymapp.di.cekirdekModul
import com.gymapp.di.supabaseModule
import com.gymapp.di.veritabaniModulu
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
 * ### WorkManager
 * Bir dönem tamamen kaldırılmıştı: tek işçi olan `MemberExpirationWorker`
 * gereksizleşince (üyelik durumu artık okuma anında bitiş tarihinden
 * türetiliyor) geriye hiçbir işçisi olmayan bir fabrika kalmıştı.
 *
 * Şimdi gerçek bir işçiyle geri geldi: `SenkronizasyonIsi`, uygulama kapalıyken
 * gönderim kuyruğunu boşaltıyor. Burada ayrıca kurulum yok — işçi Koin'e
 * `KoinComponent` üzerinden bağlanıyor, dolayısıyla `WorkerFactory` gerekmiyor.
 * Planlama `ArkaPlanSenkronizasyonu` içinde ve girişte tetikleniyor.
 */
class GymApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@GymApplication)
            // Sunucu ayarları depoya işlenmiyor; `local.properties`ten okunup
            // BuildConfig üzerinden geliyor. Boş olmaları derlemeyi düşürmüyor —
            // o durumda giriş ekranı ne eklenmesi gerektiğini söylüyor.
            // Modüllerin ÜÇÜ de ortak koddan geliyor; bu blokta Android'e özgü
            // olan yalnızca fabrikalara geçirilen örnekler. `app/di/AppModules.kt`
            // buna karşılık tamamen kalktı: içindeki repository ve DAO listesi
            // Android'e çakılıydı ve iOS kabuğu aynı listeyi yeniden yazmak
            // zorunda kalacaktı.
            modules(
                veritabaniModulu(createGymDatabase(this@GymApplication)),
                supabaseModule(
                    url = BuildConfig.SUPABASE_URL,
                    anonKey = BuildConfig.SUPABASE_ANON_KEY,
                    // Oturum Android Keystore ile şifrelenip saklanıyor;
                    // uygulama kapansa da giriş korunuyor.
                    sessionStore = AndroidSessionStore(this@GymApplication),
                ),
                cekirdekModul(
                    tercihler = AndroidTercihler(this@GymApplication),
                    arkaPlan = WorkManagerSenkronizasyonu(this@GymApplication),
                ),
                ekranModelleriModulu,
            )
        }
    }
}
