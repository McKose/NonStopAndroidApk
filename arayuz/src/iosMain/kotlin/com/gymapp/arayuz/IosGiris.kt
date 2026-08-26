package com.gymapp.arayuz

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.gymapp.arayuz.gezinme.GymGezinme
import com.gymapp.arayuz.tema.GymTema
import com.gymapp.data.auth.InMemorySessionStore
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.local.db.createGymDatabase
import com.gymapp.data.local.preferences.IosTercihler
import com.gymapp.data.sync.ArkaPlanYok
import com.gymapp.data.sync.SyncCoordinator
import com.gymapp.di.cekirdekModul
import com.gymapp.di.supabaseModule
import com.gymapp.di.veritabaniModulu
import kotlinx.coroutines.delay
import org.koin.core.Koin
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * iOS kabuğunun tek giriş noktası.
 *
 * Swift tarafı **yalnızca bu fonksiyonu** görüyor. Koin, `SessionManager`,
 * `NavHostController`, ekran modelleri — hiçbiri Swift'e sızmıyor. Bu bilinçli:
 * Kotlin/Native köprüsünden geçen her tip Swift tarafında kullanımı zorlaşan
 * bir karşılığa dönüşüyor (jenerikler siliniyor, `sealed` hiyerarşiler düz
 * sınıfa iniyor, suspend fonksiyonlar completion handler oluyor). Yüzey tek
 * fonksiyonda kalınca bu bedelin tamamı ortadan kalkıyor ve `export(...)` da
 * gerekmiyor.
 *
 * ### Ayarlar neden parametre
 * Android'de `BuildConfig`ten geliyorlar; iOS'ta karşılığı `Info.plist`.
 * Kotlin'in `Info.plist` okuması mümkün ama yanlış yer olurdu: yapılandırma
 * kabuğun işi ve Xcode zaten derleme sırasında değer enjekte etmeyi biliyor.
 * Boş geçilmeleri uygulamayı düşürmüyor — `supabaseModule` o durumda ne
 * yapılması gerektiğini söyleyen karşılıkları bağlıyor.
 *
 * ### Oturum saklama — EKSİK, ve gizlenmiyor
 * Android'de Keystore ile şifreli saklanıyor, iOS'ta Keychain olmalı. Keychain
 * gerçeklemesi HENÜZ YOK, bu yüzden şimdilik [InMemorySessionStore]
 * kullanılıyor: uygulama kapanınca oturum düşüyor ve kullanıcı tekrar giriş
 * yapmak zorunda kalıyor. Bu bir hata değil, kapsanmamış bir iş — jetonu
 * `NSUserDefaults`a düz metin yazmak alternatif DEĞİL, çünkü orası şifresiz ve
 * yedeklemeye dahil. Keychain'li `SessionStore` ayrı bir dilim.
 */
fun GymUygulamasiViewController(
    supabaseUrl: String,
    supabaseAnonKey: String,
): UIViewController {
    val koin = koinuKur(supabaseUrl, supabaseAnonKey)

    return ComposeUIViewController {
        val oturumlar = remember { koin.get<SessionManager>() }
        val senkronizasyon = remember { koin.get<SyncCoordinator>() }

        // Saklanan oturum okunana kadar hiçbir ekran gösterilmiyor — üç
        // kabukta da aynı karar: beklemeden başlansaydı giriş ekranı bir an
        // görünür, oturum geri yüklenince altından değişirdi.
        var oturumYuklendi by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            oturumlar.restore()
            oturumYuklendi = true
            senkronizasyon.requestSync()
        }

        // Uygulama önplandayken düzenli gönderim. Android'de bu döngü
        // Activity'nin, masaüstünde pencerenin yaşam döngüsüne bağlı; burada
        // controller'ın. Uygulama KAPALIYKEN gönderim yok — Android'de
        // WorkManager yapıyor, iOS karşılığı BGTaskScheduler ve o ayrı bir iş
        // (bkz. `ArkaPlanYok` KDoc'u).
        LaunchedEffect(Unit) {
            while (true) {
                delay(SENKRONIZASYON_ARALIGI_MS)
                senkronizasyon.requestSync()
            }
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

/**
 * Koin'i bir kez kurar.
 *
 * Tekrar çağrılmaya karşı korumalı olması ŞART: `GymUygulamasiViewController`
 * Swift tarafında `UIViewControllerRepresentable` içinden çağrılıyor ve SwiftUI
 * `makeUIViewController`u yeniden çalıştırabiliyor. Koruma olmasaydı ikinci
 * çağrı `KoinAppAlreadyStartedException` ile uygulamayı düşürürdü.
 */
private var kurulanKoin: Koin? = null

private fun koinuKur(supabaseUrl: String, supabaseAnonKey: String): Koin =
    kurulanKoin ?: startKoin {
        modules(
            veritabaniModulu(createGymDatabase()),
            supabaseModule(
                url = supabaseUrl,
                anonKey = supabaseAnonKey,
                sessionStore = InMemorySessionStore(),
            ),
            cekirdekModul(
                tercihler = IosTercihler(),
                arkaPlan = ArkaPlanYok,
            ),
            ekranModelleriModulu,
        )
    }.koin.also { kurulanKoin = it }

/**
 * Önplandayken gönderim tetikleme aralığı — üç kabukta da aynı.
 *
 * Kuyruk zaten boşsa tur neredeyse bedava (tek sorgu); doluysa zaten
 * gönderilmesi gereken veri var.
 */
private const val SENKRONIZASYON_ARALIGI_MS = 60_000L
