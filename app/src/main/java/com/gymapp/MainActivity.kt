package com.gymapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gymapp.arayuz.gezinme.GymGezinme
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.sync.SyncCoordinator
import com.gymapp.ui.theme.GymAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val sync: SyncCoordinator by inject()
    private val sessions: SessionManager by inject()
    private val arkaPlan: ArkaPlanSenkronizasyonu by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        baslatSenkronizasyonDongusu()
        setContent {
            // KALDIRILDI: `GlobalErrorHandler`. Hiçbir yerden beslenmiyordu —
            // `handleError` ve `showMessage` projede sıfır kez çağrılıyor — yani
            // burada hiç yayın yapmayan bir akış dinleniyordu. Varlığı yanıltıcıydı:
            // "genel hata yakalama var" izlenimi veriyor, gerçekte hiçbir hata
            // buraya ulaşmıyordu. Hatalar ekran bazında Snackbar ile bildiriliyor.

            // Saklanan oturum okunana kadar hiçbir ekran gösterilmiyor.
            //
            // Beklemeden başlansaydı giriş ekranı bir an görünür, oturum geri
            // yüklenince ekran altından değişirdi. Daha kötüsü: kullanıcı o anda
            // e-postasını yazmaya başlamış olabilirdi. Okuma yerel ve şifre
            // çözme dışında iş yapmıyor, yani bekleme gözle görülür değil.
            var oturumYuklendi by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                sessions.restore()
                oturumYuklendi = true
                // Oturum varsa arka plan işi de kurulsun. Girişte zaten
                // kuruluyor, ama uygulama güncellendiğinde ya da kullanıcı
                // uzun süredir giriş yapmış durumdayken buradan tazeleniyor:
                // iş tanımı (aralık, kısıtlar) değişmişse eskisi yaşamaya
                // devam etmesin.
                if (sessions.session.value != null) arkaPlan.baslat()
                // Geri yüklenen oturumla, uygulama kapalıyken biriken
                // değişiklikler hemen gönderilmeye başlansın; 60 saniyelik
                // döngüyü beklemenin karşılığı yok.
                sync.requestSync()
            }

            GymAppTheme {
                if (!oturumYuklendi) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                } else {
                    // Oturum TEPKİLİ okunuyor.
                    //
                    // Önceden yalnızca `.value` ile bir kez okunuyordu. Yenileme
                    // jetonu reddedildiğinde `SessionManager` oturumu düşürüyor
                    // (bkz. `currentAccessToken`), ama arayüz bunu hiç görmüyordu:
                    // kullanıcı giriş yapmış görünen bir panelde kalıyor, açtığı
                    // ilk veri ekranı `requireTenantId()` yüzünden çöküyordu.
                    //
                    // Okuma burada, kabukta kalıyor; girişe yönlendirme kararı
                    // grafiğin içinde. Kabuğa düşen tek şey oturumun VAR olup
                    // olmadığı — `Session` tipinin kendisi grafiğe geçmiyor.
                    val oturum by sessions.session.collectAsState()

                    GymGezinme(oturumVar = oturum != null)
                }
            }
        }
    }

    /**
     * Uygulama önplandayken düzenli aralıklarla gönderim tetikler.
     *
     * Yazma anında tetiklemek doğru olmazdı: kuyruğa alma, satırı değiştiren
     * yazmayla aynı transaction içinde yapılıyor ve o an başlayan bir tur henüz
     * işlenmemiş kaydı göremez. Düzenli tetikleme bu yarışı tamamen ortadan
     * kaldırıyor.
     *
     * Döngü yaşam döngüsüne bağlı: uygulama arkaplandayken tetikleme durur.
     * Sürekli koşan bir zamanlayıcı, kullanıcı uygulamaya bakmazken pil harcardı.
     * Turun kendisi arkaplanda da tamamlanır — koordinatörün kapsamı uygulama
     * ömrü boyunca yaşıyor.
     */
    private fun baslatSenkronizasyonDongusu() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    sync.requestSync()
                    delay(SENKRONIZASYON_ARALIGI_MS)
                }
            }
        }
    }

    private companion object {
        /**
         * Önplandayken tetikleme aralığı.
         *
         * Kuyruk zaten boşsa tur neredeyse bedava (tek sorgu); doluysa zaten
         * gönderilmesi gereken veri var. Daha sık tetiklemenin karşılığı yok,
         * daha seyrek olması panelde verinin geç görünmesi demek.
         */
        const val SENKRONIZASYON_ARALIGI_MS = 60_000L
    }
}
