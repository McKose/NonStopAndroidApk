package com.gymapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.sync.SyncCoordinator
import com.gymapp.presentation.common.GlobalErrorHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import android.widget.Toast
import org.koin.androidx.compose.koinViewModel
import com.gymapp.presentation.calendar.CalendarScreen
import com.gymapp.presentation.dashboard.DashboardScreen
import com.gymapp.presentation.login.LoginScreen
import com.gymapp.presentation.packages.AddPackageScreen
import com.gymapp.presentation.packages.PackageListScreen
import com.gymapp.presentation.members.MemberDetailScreen
import com.gymapp.presentation.members.MemberListScreen
import com.gymapp.presentation.members.RegisterMemberScreen
import com.gymapp.presentation.finance.FinanceScreen
import com.gymapp.presentation.market.MarketScreen
import com.gymapp.presentation.market.OrderHistoryScreen
import com.gymapp.presentation.settings.SettingsScreen
import com.gymapp.presentation.settings.PersonnelScreen

import com.gymapp.ui.theme.GymAppTheme

class MainActivity : ComponentActivity() {
    private val errorHandler: GlobalErrorHandler by inject()
    private val sync: SyncCoordinator by inject()
    private val sessions: SessionManager by inject()
    private val arkaPlan: ArkaPlanSenkronizasyonu by inject()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        baslatSenkronizasyonDongusu()
        setContent {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                errorHandler.errors.collect { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
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
                    val navController = rememberNavController()

                    // Oturum artık TEPKİLİ okunuyor.
                    //
                    // Önceden yalnızca `.value` ile bir kez okunuyordu. Yenileme
                    // jetonu reddedildiğinde `SessionManager` oturumu düşürüyor
                    // (bkz. `currentAccessToken`), ama arayüz bunu hiç görmüyordu:
                    // kullanıcı giriş yapmış görünen bir panelde kalıyor, açtığı
                    // ilk veri ekranı `requireTenantId()` yüzünden çöküyordu.
                    val oturum by sessions.session.collectAsState()

                    // Başlangıç hedefi ilk karede sabitleniyor; sonraki
                    // değişiklikleri NavHost zaten dikkate almaz, `remember`
                    // bunu açık hâle getiriyor.
                    val baslangicHedefi = remember { if (oturum != null) "dashboard" else "login" }

                    LaunchedEffect(oturum) {
                        if (oturum == null &&
                            navController.currentDestination?.route != "login"
                        ) {
                            navController.navigate("login") {
                                // Geri yığınında veri ekranı bırakmıyoruz: geri
                                // tuşu oturumsuz bir ekrana dönmemeli.
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    Surface(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            // Oturum geri yüklendiyse doğrudan panoya giriliyor.
                            startDestination = baslangicHedefi,
                        ) {
                            composable("login") {
                                LoginScreen(
                                    onLoginSuccess = {
                                        navController.navigate("dashboard") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("dashboard") {
                                DashboardScreen(
                                    onNavigateToMembers = { navController.navigate("member_list") },
                                    onNavigateToFinance = { navController.navigate("finance") },
                                    onNavigateToMarket = { navController.navigate("market") },
                                    onNavigateToCalendar = { navController.navigate("calendar") },
                                    onNavigateToPackages = { navController.navigate("package_list") },
                                    onNavigateToSettings = { navController.navigate("settings") }
                                )
                            }
                            composable("calendar") {
                                CalendarScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("member_list") {
                                MemberListScreen(
                                    onNavigateToRegister = {
                                        navController.navigate("register_member")
                                    },
                                    onNavigateToDetail = { memberId ->
                                        navController.navigate("member_detail/$memberId")
                                    },
                                    onNavigateToPackages = {
                                        navController.navigate("package_list")
                                    },
                                    onNavigateToFinance = {
                                        navController.navigate("finance")
                                    },
                                    onNavigateToMarket = {
                                        navController.navigate("market")
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate("settings")
                                    },
                                    onNavigateToRenew = { memberId ->
                                        navController.navigate("renew_package/$memberId")
                                    }
                                )
                            }
                            composable("finance") {
                                FinanceScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("market") {
                                MarketScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToOrders = { navController.navigate("order_history") }
                                )
                            }
                            composable("order_history") {
                                OrderHistoryScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToPersonnel = { navController.navigate("personnel") },
                                    onLogout = {
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("personnel") {
                                PersonnelScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("register_member") {
                                RegisterMemberScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "renew_package/{memberId}",
                                arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val memberId = backStackEntry.arguments?.getString("memberId").orEmpty()
                                RegisterMemberScreen(
                                    isRenewal = true,
                                    memberId = memberId,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "member_detail/{memberId}",
                                arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val memberId = backStackEntry.arguments?.getString("memberId").orEmpty()
                                MemberDetailScreen(
                                    memberId = memberId,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("package_list") {
                                PackageListScreen(
                                    onNavigateToAdd = { packageId ->
                                        if (packageId == null) {
                                            navController.navigate("add_package")
                                        } else {
                                            navController.navigate("edit_package/$packageId")
                                        }
                                    },
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("add_package") {
                                AddPackageScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable(
                                route = "edit_package/{packageId}",
                                arguments = listOf(navArgument("packageId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                AddPackageScreen(
                                    packageId = backStackEntry.arguments?.getString("packageId"),
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
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
