package com.gymapp.masaustu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gymapp.arayuz.ekranModelleriModulu
import com.gymapp.arayuz.gezinme.GymGezinme
import com.gymapp.arayuz.tema.GymTema
import com.gymapp.data.auth.InMemorySessionStore
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.local.db.createGymDatabase
import com.gymapp.data.local.preferences.JvmTercihler
import com.gymapp.data.sync.ArkaPlanYok
import com.gymapp.di.cekirdekModul
import com.gymapp.di.supabaseModule
import com.gymapp.di.veritabaniModulu
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

/**
 * Masaüstü kabuğu — artık iskelet değil, uygulamanın kendisi.
 *
 * i1'de burada yalnızca giriş ekranı vardı ve düğmesi hiçbir yere bağlı
 * değildi. i4a'da gerçek grafiğe bağlandı: aynı `GymGezinme`, aynı ekran
 * modelleri, aynı repository'ler.
 *
 * ### Bunun asıl değeri iOS değil
 * Uygulamanın tamamı artık **Linux'ta** çalıştırılabiliyor. Apple donanımı
 * gerekmiyor, macOS CI dakikası harcanmıyor, simülatör kurulumu gerekmiyor.
 * iOS'ta ayrı olan tek şey kabuk; ekranlar, gezinme ve iş kuralı bu pencerede
 * görülenle birebir aynı kod.
 *
 * ### Oturum saklama neden bellekte
 * Android'de Keystore, iOS'ta Keychain kullanılıyor. Masaüstünde şifreli bir
 * saklama yok ve [InMemorySessionStore] bunu **gizlemiyor**: uygulama
 * kapanınca oturum düşüyor. Alternatif — jetonu düz metin bir dosyaya yazmak —
 * geliştirme kolaylığı için gerçek bir kimlik bilgisini korumasız bırakmak
 * olurdu. Masaüstü kabuğu bir geliştirme/test aracı; kalıcı oturumun karşılığı
 * o risk değil.
 *
 * ### Sunucu ayarları
 * Ortam değişkenlerinden okunuyor, depoya işlenmiyor. Boş olmaları uygulamayı
 * düşürmüyor: `supabaseModule` o durumda ne yapılması gerektiğini söyleyen
 * karşılıkları bağlıyor ve giriş ekranı sebebi yazıyor.
 *
 * Pencere 420x880: iPhone 14 Pro'nun mantıksal ekranına yakın bir oran.
 * Ekranlar önce bu darlıkta doğru görünmeli — masaüstü genişliğinde güzel
 * duran bir düzen telefonda taşabilir, tersi taşmaz.
 */
fun main() {
    startKoin {
        modules(
            veritabaniModulu(createGymDatabase()),
            supabaseModule(
                url = System.getenv("SUPABASE_URL").orEmpty(),
                anonKey = System.getenv("SUPABASE_ANON_KEY").orEmpty(),
                sessionStore = InMemorySessionStore(),
            ),
            cekirdekModul(
                tercihler = JvmTercihler(),
                // Masaüstünde uygulama kapalıyken senkronizasyon yok; pencere
                // açıkken zaten düzenli tetikleniyor (aşağıdaki döngü).
                arkaPlan = ArkaPlanYok,
            ),
            ekranModelleriModulu,
        )
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Non Stop GYM",
            state = rememberWindowState(width = 420.dp, height = 880.dp),
        ) {
            val oturumlar = remember { KoinPlatform.getKoin().get<SessionManager>() }

            // Saklanan oturum okunana kadar hiçbir ekran gösterilmiyor —
            // Android kabuğundaki kararın aynısı: beklemeden başlansaydı giriş
            // ekranı bir an görünür, oturum geri yüklenince altından değişirdi.
            var oturumYuklendi by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                oturumlar.restore()
                oturumYuklendi = true
            }

            GymTema(koyu = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (oturumYuklendi) {
                        val oturum by oturumlar.session.collectAsState()
                        GymGezinme(oturumVar = oturum != null)
                    }
                }
            }
        }
    }
}
