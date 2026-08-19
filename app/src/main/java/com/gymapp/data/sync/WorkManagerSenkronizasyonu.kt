package com.gymapp.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Uygulama kapalıyken senkronizasyonu sürdüren iş.
 *
 * Buna kadar tetikleme yalnızca uygulama açıkken oluyordu: girişte, oturum geri
 * yüklendiğinde ve önplandayken dakikada bir. Yani telefonu cebine koyan bir
 * eğitmenin son dersteki kayıtları, uygulamayı bir daha açana kadar cihazda
 * kalıyordu — kayıp değil ama panelde ve diğer cihazlarda görünmüyordu.
 *
 * ### Neden Koin'e `KoinComponent` ile bağlanıyor
 * `WorkerFactory` kurup Koin'in WorkManager eklentisini eklemek de olurdu; tek
 * işçi için fazladan bir bağımlılık ve fabrika kaydı demek. `KoinComponent`
 * aynı grafikten aynı örneği veriyor — özellikle [SyncCoordinator] tek nesne
 * olduğu için önemli: ikinci bir örnek, önplandaki turla arka plandaki turun
 * birbirinden habersiz koşması demek olurdu.
 */
class SenkronizasyonIsi(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val sync: SyncCoordinator by inject()

    override suspend fun doWork(): Result =
        when (sync.syncNow().backgroundResult()) {
            BackgroundSyncResult.DONE -> Result.success()

            // `Result.retry()`, `Result.failure()` değil: başarısızlık işi
            // tamamen düşürür ve bir sonraki planlı tura kadar hiçbir şey
            // olmaz. `retry` ise üstel geri çekilmeyle tekrar deniyor — ağ
            // geri geldiğinde kuyruğun boşalmasını sağlayan şey bu.
            BackgroundSyncResult.RETRY -> Result.retry()
        }
}

/**
 * [ArkaPlanSenkronizasyonu]'nun Android gerçeklemesi — WorkManager üzerinde.
 *
 * (i2'de yeniden adlandırıldı: `ArkaPlanSenkronizasyonu` artık `shared`'daki
 * ortak ARAYÜZÜN adı; çağıranlar yalnızca onu görüyor. iOS ve masaüstü
 * karşılığı `ArkaPlanYok` — orada neden hiçbir şey planlanmadığının gerekçesi
 * arayüzün dosyasında.)
 *
 * WorkManager ayrıntılarını tek noktada tutuyor: iş adı, aralık ve kısıtlar
 * çağıran ekranlara sızmıyor. Sızsaydı iki farklı yerden farklı kısıtlarla
 * planlanabilir ve hangisinin geçerli olduğu duruma göre değişirdi.
 */
class WorkManagerSenkronizasyonu(private val context: Context) : ArkaPlanSenkronizasyonu {

    /**
     * İşi planlar. Aynı iş zaten planlıysa tanımı güncellenir, zamanı sıfırlanmaz.
     *
     * [ExistingPeriodicWorkPolicy.UPDATE] bilinçli:
     *  - `KEEP` olsaydı aralık ya da kısıt değiştiğinde eski tanım, uygulama
     *    silinip kurulana kadar yaşamaya devam ederdi,
     *  - `CANCEL_AND_REENQUEUE` olsaydı her giriş ve her açılış sayacı
     *    sıfırlar; uygulamayı sık açan birinde iş hiç çalışmayabilirdi.
     *
     * Çağrılması güvenli ve tekrarlanabilir: girişte ve oturum geri
     * yüklendiğinde çağrılıyor.
     */
    override fun baslat() {
        val istek = PeriodicWorkRequestBuilder<SenkronizasyonIsi>(
            ARALIK_DAKIKA, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    // Ağ yokken uyanmanın karşılığı yok: tur zaten ilk istekte
                    // durur ve yalnızca pil harcardı.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // Varsayılan 30 saniyelik geri çekilme, ağı yeni kaybetmiş bir
            // cihazda çok sık deneme demek. On dakika, ağın geri gelmesi için
            // makul ve pil açısından ucuz.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            IS_ADI, ExistingPeriodicWorkPolicy.UPDATE, istek,
        )
    }

    /**
     * İşi iptal eder.
     *
     * Çıkışta çağrılıyor. Bırakılsaydı, çıkış yapmış bir cihaz 15 dakikada bir
     * uyanıp "oturum yok" deyip geri yatardı — hiçbir işe yaramayan, yalnızca
     * pil harcayan bir döngü. İşin kendisi oturumsuz durumu zaten sorunsuz
     * karşılıyor, ama en ucuz iş hiç koşmayan iştir.
     */
    override fun durdur() {
        WorkManager.getInstance(context).cancelUniqueWork(IS_ADI)
    }

    private companion object {
        /**
         * Benzersiz iş adı.
         *
         * Sabit olması şart: her planlamada farklı bir ad üretilseydi işler
         * birikir ve cihaz aynı turu paralel olarak defalarca koşardı.
         */
        const val IS_ADI = "senkronizasyon"

        /**
         * Tetikleme aralığı.
         *
         * 15 dakika WorkManager'ın **alt sınırı**; daha küçük bir değer sessizce
         * 15'e yuvarlanır. İşletim sistemi ayrıca kendi toplu uyandırma
         * penceresine göre erteleyebiliyor, yani bu bir garanti değil "en sık"
         * değeri.
         */
        const val ARALIK_DAKIKA = 15L
    }
}
