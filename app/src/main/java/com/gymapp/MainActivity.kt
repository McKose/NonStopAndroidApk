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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gymapp.domain.Decimals
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.domain.Money
import com.gymapp.domain.Now
import com.gymapp.domain.labelTr
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.sync.SyncCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import com.gymapp.arayuz.giris.GirisEkrani
import com.gymapp.arayuz.takvim.TakvimEkrani
import com.gymapp.presentation.calendar.CalendarEvent
import com.gymapp.presentation.calendar.CalendarViewModel
import com.gymapp.arayuz.pano.PanoEkrani
import com.gymapp.presentation.dashboard.DashboardViewModel
import com.gymapp.presentation.login.LoginViewModel
import com.gymapp.arayuz.paketler.PaketFormu
import com.gymapp.arayuz.paketler.PaketFormuEkrani
import com.gymapp.arayuz.paketler.PaketListesiEkrani
import com.gymapp.presentation.packages.PackageEvent
import com.gymapp.presentation.packages.PackageViewModel
import com.gymapp.presentation.members.MemberDetailScreen
import com.gymapp.arayuz.uyeler.UyeListesiEkrani
import com.gymapp.presentation.members.MemberEvent
import com.gymapp.presentation.members.MemberViewModel
import com.gymapp.arayuz.uyeler.UyeKayitEkrani
import com.gymapp.presentation.finance.FinanceScreen
import com.gymapp.presentation.market.MarketScreen
import com.gymapp.arayuz.market.SiparisGecmisiEkrani
import com.gymapp.presentation.market.OrderHistoryViewModel
import com.gymapp.arayuz.ayarlar.AyarlarEkrani
import com.gymapp.presentation.settings.SettingsViewModel
import com.gymapp.presentation.settings.PersonnelScreen

import com.gymapp.ui.theme.GymAppTheme

class MainActivity : ComponentActivity() {
    private val sync: SyncCoordinator by inject()
    private val sessions: SessionManager by inject()
    private val arkaPlan: ArkaPlanSenkronizasyonu by inject()

    @OptIn(ExperimentalMaterial3Api::class)
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
                                // Ekranın kendisi ORTAK modülde (`:arayuz`) ve
                                // ViewModel'i tanımıyor: durumu parametre
                                // olarak alıyor, tıklamayı geri çağrıyla
                                // bildiriyor. Bağlama işi burada, Android
                                // kabuğunda yapılıyor — iOS kabuğu aynı ekranı
                                // kendi tarafında aynı şekilde bağlayacak.
                                val girisModeli: LoginViewModel = koinViewModel()
                                val hata by girisModeli.error.collectAsState()
                                val gonderiliyor by girisModeli.isSubmitting.collectAsState()

                                GirisEkrani(
                                    gonderiliyor = gonderiliyor,
                                    hata = hata,
                                    onGiris = { eposta, sifre ->
                                        girisModeli.login(eposta, sifre) {
                                            navController.navigate("dashboard") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    },
                                )
                            }
                            composable("dashboard") {
                                PanoBagla(navController)
                            }
                            composable("calendar") {
                                TakvimBagla(navController)
                            }
                            composable("member_list") {
                                UyeListesiBagla(navController)
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
                                val siparisModeli: OrderHistoryViewModel = koinViewModel()
                                val siparisler by siparisModeli.orders.collectAsState()
                                val uyeAdlari by siparisModeli.memberNames.collectAsState()

                                SiparisGecmisiEkrani(
                                    siparisler = siparisler,
                                    uyeAdlari = uyeAdlari,
                                    onGeri = { navController.popBackStack() },
                                )
                            }
                            composable("settings") {
                                AyarlarBagla(navController)
                            }
                            composable("personnel") {
                                PersonnelScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("register_member") {
                                UyeKayitBagla(navController, yenileme = false, uyeId = "")
                            }
                            composable(
                                route = "renew_package/{memberId}",
                                arguments = listOf(navArgument("memberId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val memberId = backStackEntry.arguments?.getString("memberId").orEmpty()
                                UyeKayitBagla(navController, yenileme = true, uyeId = memberId)
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
                                // Ekran ORTAK modülde; ViewModel bağlaması ve
                                // olay akışının bildirime çevrilmesi burada,
                                // Android kabuğunda.
                                val paketModeli: PackageViewModel = koinViewModel()
                                val paketler by paketModeli.packages.collectAsState()
                                val yazabilir by paketModeli.canWrite.collectAsState()
                                val snackbarDurumu = remember { SnackbarHostState() }

                                // Silme sonucu her durumda bildiriliyor. Önceden
                                // `deletePackage` fırlatıyor ve sonuç hiçbir yerde
                                // okunmuyordu: başarısız silme başarılıdan ayırt
                                // edilemiyor, üstelik uygulama kapanıyordu.
                                LaunchedEffect(Unit) {
                                    paketModeli.events.collect { olay ->
                                        when (olay) {
                                            is PackageEvent.Saved -> Unit // kaydetme başka ekranda
                                            is PackageEvent.Deleted ->
                                                snackbarDurumu.showSnackbar("Paket silindi.")
                                            is PackageEvent.Failed ->
                                                snackbarDurumu.showSnackbar(olay.message)
                                        }
                                    }
                                }

                                PaketListesiEkrani(
                                    paketler = paketler,
                                    yazabilir = yazabilir,
                                    onEkle = { navController.navigate("add_package") },
                                    onDuzenle = { id -> navController.navigate("edit_package/$id") },
                                    onSil = { id -> paketModeli.deletePackage(id) },
                                    onGeri = { navController.popBackStack() },
                                    snackbarDurumu = snackbarDurumu,
                                )
                            }
                            composable("add_package") {
                                PaketFormuBagla(
                                    paketId = null,
                                    navController = navController,
                                )
                            }
                            composable(
                                route = "edit_package/{packageId}",
                                arguments = listOf(navArgument("packageId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                PaketFormuBagla(
                                    paketId = backStackEntry.arguments?.getString("packageId"),
                                    navController = navController,
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

/**
 * Paket formunu ViewModel'e bağlar.
 *
 * Ayrı bir işlev çünkü iki gezinme yolu (yeni paket / düzenleme) aynı bağlamayı
 * kullanıyor; `NavHost` içine iki kez yazılsaydı biri düzeltilip diğeri
 * unutulabilirdi.
 *
 * Ekranın kendisi ORTAK modülde ve ViewModel tanımıyor: düzenlenecek paketi
 * okumak, kaydetmek ve olayları bildirime çevirmek burada — Android kabuğunda.
 */
@Composable
private fun PaketFormuBagla(
    paketId: String?,
    navController: androidx.navigation.NavHostController,
) {
    val model: PackageViewModel = koinViewModel()
    val snackbarDurumu = remember { SnackbarHostState() }

    // Düzenlemede paket okunana kadar form boş kalmamalı; `null` başlangıç
    // "henüz yüklenmedi" demek, `PaketFormu()` ise "yeni paket".
    var baslangic by remember(paketId) { mutableStateOf(if (paketId == null) PaketFormu() else null) }
    var yukleniyor by remember(paketId) { mutableStateOf(paketId != null) }

    LaunchedEffect(paketId) {
        if (paketId != null) {
            model.getPackageById(paketId)?.let { paket ->
                baslangic = PaketFormu(
                    sinirsiz = paket.sessionCount == null,
                    seansSayisi = paket.sessionCount?.toString() ?: "10",
                    tur = paket.type,
                    kategori = paket.category,
                    fiyat = Money(paket.basePriceMinor).asDouble.toString(),
                    gun = paket.validityDays.toString(),
                )
            }
            yukleniyor = false
        }
    }

    // Kaydetme sonucu **her zaman** bildirilir; önceden hata sessizce yutuluyor
    // ve ekran başarılıymış gibi kapanıyordu.
    LaunchedEffect(Unit) {
        model.events.collect { olay ->
            when (olay) {
                is PackageEvent.Saved -> navController.popBackStack()
                // Silme bu ekrandan yapılamıyor; liste ekranı bildiriyor.
                is PackageEvent.Deleted -> Unit
                is PackageEvent.Failed -> snackbarDurumu.showSnackbar(olay.message)
            }
        }
    }

    PaketFormuEkrani(
        baslangic = baslangic,
        yukleniyor = yukleniyor,
        onKaydet = { form ->
            model.savePackage(
                packageId = paketId,
                name = "${if (form.sinirsiz) "Sınırsız" else form.seansSayisi.ifBlank { "0" }} - " +
                    "${form.tur.labelTr()} - ${form.kategori.labelTr()}",
                type = form.tur,
                category = form.kategori,
                basePrice = Decimals.parseOrDefault(form.fiyat),
                validityDays = form.gun.toIntOrNull() ?: 30,
                sessionCount = if (form.sinirsiz) null else form.seansSayisi.toIntOrNull(),
            )
        },
        onGeri = { navController.popBackStack() },
        snackbarDurumu = snackbarDurumu,
    )
}

/**
 * Ayarlar ekranının Android bağlaması.
 *
 * Ekran `arayuz` modülünde ve ViewModel tanımıyor; çıkış akışının üç adımı
 * (iste / onayla / vazgeç) burada `SettingsViewModel`'e bağlanıyor.
 *
 * Çıkış sonrası gezinme `popUpTo(0)` ile tüm yığını siliyor: çıkmış bir
 * kullanıcının geri tuşuyla panele dönebilmesi, çıkışın yaptığı her şeyi
 * (veritabanı temizliği dahil) anlamsız kılardı.
 */
@Composable
private fun AyarlarBagla(navController: androidx.navigation.NavHostController) {
    val model: SettingsViewModel = koinViewModel()
    val senkDurumu by model.syncState.collectAsState()
    val bekleyen by model.pendingCount.collectAsState()
    val cikistaBekleyen by model.cikistaBekleyen.collectAsState()

    val cikisaGit: () -> Unit = {
        navController.navigate("login") {
            popUpTo(0) { inclusive = true }
        }
    }

    AyarlarEkrani(
        salonAdi = model.salonName,
        senkDurumu = senkDurumu,
        bekleyen = bekleyen,
        cikistaBekleyen = cikistaBekleyen,
        onGeri = { navController.popBackStack() },
        onPersonel = { navController.navigate("personnel") },
        onSimdiEsitle = { model.syncNow() },
        onCikisIste = { model.requestLogout(cikisaGit) },
        onCikisiOnayla = { model.confirmLogout(cikisaGit) },
        onCikistanVazgec = { model.cancelLogout() },
        onSalonAdiKaydet = { model.updateSalonName(it) },
    )
}

/**
 * Panonun Android bağlaması.
 *
 * Ekran `arayuz` modülünde ve ViewModel tanımıyor; durum sınıfının alanları
 * burada tek tek parametrelere açılıyor.
 */
@Composable
private fun PanoBagla(navController: androidx.navigation.NavHostController) {
    val model: DashboardViewModel = koinViewModel()
    val durum by model.uiState.collectAsState()

    PanoEkrani(
        rol = durum.userRole,
        aktifUye = durum.activeMembers,
        gunlukRandevular = durum.dailyAppointments,
        uyeler = durum.members,
        personeller = durum.staffList,
        kritikUyarilar = durum.criticalAlerts,
        personelBaglantisiYok = durum.personelBaglantisiYok,
        onUyeler = { navController.navigate("member_list") },
        onFinans = { navController.navigate("finance") },
        onMarket = { navController.navigate("market") },
        onTakvim = { navController.navigate("calendar") },
        onPaketler = { navController.navigate("package_list") },
        onAyarlar = { navController.navigate("settings") },
    )
}

/**
 * Üye listesinin Android bağlaması.
 *
 * Ekran `arayuz` modülünde. Burada üç şey yapılıyor:
 *
 *  1. Saat okunuyor. Ekran `System.currentTimeMillis()` çağırıyordu; artık
 *     değer ortak `Now`'dan gelip parametre olarak geçiyor.
 *  2. Tahsilat diyaloğunun kalan borcu **askıya alınabilir** bir çağrıyla
 *     getiriliyor. Ekran bunu yapamaz; hangi üye için diyalog istendiğini
 *     bildiriyor, borcu buradaki etki getiriyor.
 *  3. Olaylar Snackbar'a bağlanıyor. Tahsilat sonucu önceden yutuluyordu,
 *     yani reddedilen bir tahsilat başarılı olandan ayırt edilemiyordu.
 */
@Composable
private fun UyeListesiBagla(navController: androidx.navigation.NavHostController) {
    val model: MemberViewModel = koinViewModel()
    val durum by model.listUiState.collectAsState()
    val snackbarDurumu = remember { SnackbarHostState() }

    var tahsilatUyesi by remember { mutableStateOf<MemberEntity?>(null) }
    var tahsilatBorcu by remember { mutableStateOf<Money?>(null) }

    // Borç, diyalog istendiğinde okunuyor. `null` kaldığı sürece ekran
    // diyaloğu çizmiyor: yanlış bir tutar göstermektense beklemek doğru.
    LaunchedEffect(tahsilatUyesi?.id) {
        val uye = tahsilatUyesi
        tahsilatBorcu = if (uye == null) null else model.outstandingBalance(uye.id)
    }

    LaunchedEffect(Unit) {
        model.events.collect { olay ->
            when (olay) {
                is MemberEvent.Saved -> snackbarDurumu.showSnackbar(olay.message)
                is MemberEvent.Failed -> snackbarDurumu.showSnackbar(olay.message)
                // Silme detay ekranından yapılıyor.
                is MemberEvent.Deleted -> Unit
            }
        }
    }

    fun tahsilatiKapat() {
        tahsilatUyesi = null
        tahsilatBorcu = null
    }

    UyeListesiEkrani(
        uyeler = durum.members,
        yukleniyor = durum.isLoading,
        arama = durum.searchQuery,
        rol = durum.role,
        kapsam = durum.kapsam,
        kapsamSecilebilir = durum.kapsamSecilebilir,
        personelBaglantisiYok = durum.personelBaglantisiYok,
        simdiMs = Now.epochMillis(),
        tahsilatUyesi = tahsilatUyesi,
        tahsilatBorcu = tahsilatBorcu,
        onAramaDegisti = { model.onSearchQueryChange(it) },
        onKapsamDegisti = { model.onKapsamChange(it) },
        onUyeAc = { navController.navigate("member_detail/$it") },
        onYeniUye = { navController.navigate("register_member") },
        onYenile = { navController.navigate("renew_package/$it") },
        onTahsilatIste = { tahsilatUyesi = it },
        onTahsilatOnayla = { tutar ->
            val uye = tahsilatUyesi
            tahsilatiKapat()
            if (uye != null) model.markAsPaid(uye.id, tutar)
        },
        onTahsilatVazgec = { tahsilatiKapat() },
        onPaketler = { navController.navigate("package_list") },
        onFinans = { navController.navigate("finance") },
        onMarket = { navController.navigate("market") },
        onAyarlar = { navController.navigate("settings") },
        snackbarDurumu = snackbarDurumu,
    )
}

/**
 * Takvimin Android bağlaması.
 *
 * Ekran artık gün aritmetiği yapmıyor; ileri/geri/bugün burada
 * `java.time.LocalDate` ile hesaplanıyor (ViewModel de o tiple konuşuyor) ve
 * ekrana yalnızca seçili günün epoch milisaniyesi geçiyor.
 *
 * Sheet ve diyaloğun açık/kapalı hâli de burada: kayıt reddedildiğinde
 * (çakışma, seans hakkı yok) sheet açık kalmalı ve bunu yalnızca olayları
 * dinleyen taraf bilebilir.
 */
@Composable
private fun TakvimBagla(navController: androidx.navigation.NavHostController) {
    val model: CalendarViewModel = koinViewModel()
    val durum by model.uiState.collectAsState()
    val secilenGun by model.selectedDate.collectAsState()
    val snackbarDurumu = remember { SnackbarHostState() }

    var eklemeAcik by remember { mutableStateOf(false) }
    var secilenRandevu by remember { mutableStateOf<AppointmentEntity?>(null) }

    // Çakışma / seans hakkı gibi reddedilme sebepleri kullanıcıya gösteriliyor.
    LaunchedEffect(Unit) {
        model.events.collect { olay ->
            when (olay) {
                is CalendarEvent.AppointmentSaved -> {
                    eklemeAcik = false
                    snackbarDurumu.showSnackbar("Randevu oluşturuldu.")
                }
                is CalendarEvent.StatusUpdated -> {
                    secilenRandevu = null
                    snackbarDurumu.showSnackbar("Randevu güncellendi.")
                }
                is CalendarEvent.Failed -> snackbarDurumu.showSnackbar(olay.message)
            }
        }
    }

    val gunMs = secilenGun.atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant().toEpochMilli()

    TakvimEkrani(
        secilenGunMs = gunMs,
        randevular = durum.appointments,
        uyeler = durum.members,
        personeller = durum.staffList,
        randevuEklemeAcik = eklemeAcik,
        secilenRandevu = secilenRandevu,
        onGeri = { navController.popBackStack() },
        onOncekiGun = { model.setDate(secilenGun.minusDays(1)) },
        onSonrakiGun = { model.setDate(secilenGun.plusDays(1)) },
        onBugun = { model.setDate(java.time.LocalDate.now()) },
        onRandevuEklemeAc = { eklemeAcik = true },
        onRandevuEklemeKapat = { eklemeAcik = false },
        onRandevuSec = { secilenRandevu = it },
        onRandevuEkle = { uyeId, personelId, saat, tur ->
            model.addAppointment(uyeId, personelId, saat, tur)
        },
        onDurumGuncelle = { randevuId, yeniDurum, not ->
            model.updateAppointmentStatus(randevuId, yeniDurum, not)
        },
        snackbarDurumu = snackbarDurumu,
    )
}

/**
 * Üye kaydı / paket yenilemenin Android bağlaması.
 *
 * Tek bağlama iki rotayı da karşılıyor; aralarındaki fark yalnızca
 * [yenileme] ve [uyeId].
 *
 * Ekran ViewModel tanımıyor: form durumunu bütün hâlde alıyor,
 * değişiklikleri alan alan bildiriyor. Doğrulama ve fiyat hesabı burada
 * değil ViewModel'de kalıyor — ekranın işi "kullanıcı bu alana bunu yazdı"
 * demek.
 */
@Composable
private fun UyeKayitBagla(
    navController: androidx.navigation.NavHostController,
    yenileme: Boolean,
    uyeId: String,
) {
    val model: MemberViewModel = koinViewModel()
    val form by model.formState.collectAsState()
    val paketler by model.packages.collectAsState()

    LaunchedEffect(yenileme, uyeId) {
        if (yenileme && uyeId.isNotBlank()) model.loadMemberForRenewal(uyeId)
        else if (!yenileme) model.resetForm()
    }

    // Kayıt başarılıysa ekran kapanıyor; başarısızsa form (ve yazılanlar)
    // duruyor ve hata kartı görünüyor.
    LaunchedEffect(form.submitSuccess) {
        if (form.submitSuccess) navController.popBackStack()
    }

    UyeKayitEkrani(
        form = form,
        paketler = paketler,
        taksitSecenekleri = model.installmentOptions,
        yenileme = yenileme,
        onGeri = { navController.popBackStack() },
        onAdSoyad = model::onFullNameChange,
        onTelefon = model::onPhoneChange,
        onEposta = model::onEmailChange,
        onSaglikRiskleri = model::onHealthRisksChange,
        onSaglikNotlari = model::onHealthNotesChange,
        onPaketSecildi = { model.onPackageSelected(it) },
        onDevir = model::onCarryOverChange,
        onIskonto = model::onDiscountChange,
        onOdemeTuru = model::onPaymentTypeChange,
        onOdemeDurumu = model::onPaymentStatusChange,
        onTaksit = model::onInstallmentChange,
        onNotlar = model::onNotesChange,
        onKaydet = model::submitRegistration,
    )
}
