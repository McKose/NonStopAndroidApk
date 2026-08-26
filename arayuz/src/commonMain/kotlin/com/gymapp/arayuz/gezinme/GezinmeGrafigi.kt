// Bağlama composable'ları deneysel Material3 API'lerine dokunuyor (sheet ve
// açılır menü durumları). Eskiden bu izin `MainActivity.onCreate` üzerindeydi
// ve NavHost'un tamamını kapsıyordu; grafik taşınınca dosya düzeyine geçti.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gymapp.arayuz.gezinme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.Decimals
import com.gymapp.domain.Money
import com.gymapp.domain.Now
import com.gymapp.domain.labelTr
import com.gymapp.arayuz.ayarlar.AyarlarEkrani
import com.gymapp.arayuz.ayarlar.SettingsViewModel
import com.gymapp.arayuz.finans.FinansEkrani
import com.gymapp.arayuz.finans.FinanceEvent
import com.gymapp.arayuz.finans.FinanceViewModel
import com.gymapp.arayuz.giris.GirisEkrani
import com.gymapp.arayuz.giris.LoginViewModel
import com.gymapp.arayuz.market.MarketEkrani
import com.gymapp.arayuz.market.MarketEvent
import com.gymapp.arayuz.market.MarketViewModel
import com.gymapp.arayuz.market.OrderHistoryViewModel
import com.gymapp.arayuz.market.SiparisGecmisiEkrani
import com.gymapp.arayuz.paketler.PaketFormu
import com.gymapp.arayuz.paketler.PaketFormuEkrani
import com.gymapp.arayuz.paketler.PaketListesiEkrani
import com.gymapp.arayuz.paketler.PackageEvent
import com.gymapp.arayuz.paketler.PackageViewModel
import com.gymapp.arayuz.pano.PanoEkrani
import com.gymapp.arayuz.pano.DashboardViewModel
import com.gymapp.arayuz.personel.PersonelEkrani
import com.gymapp.arayuz.personel.PersonelFormHedefi
import com.gymapp.arayuz.personel.PersonnelEvent
import com.gymapp.arayuz.personel.PersonnelViewModel
import com.gymapp.arayuz.takvim.TakvimEkrani
import com.gymapp.arayuz.takvim.CalendarEvent
import com.gymapp.arayuz.takvim.CalendarViewModel
import com.gymapp.arayuz.uyeler.MemberEvent
import com.gymapp.arayuz.uyeler.MemberViewModel
import com.gymapp.arayuz.uyeler.UyeDetayEkrani
import com.gymapp.arayuz.uyeler.UyeKayitEkrani
import com.gymapp.arayuz.uyeler.UyeListesiEkrani
import org.koin.compose.viewmodel.koinViewModel

/**
 * Rota argümanını okur.
 *
 * `backStackEntry.arguments?.getString(...)` YAZILAMIYOR ve sebebi ilk bakışta
 * görünmüyor: `arguments`ın tipi `SavedState` ve bu tip Android'de
 * `android.os.Bundle`a takma ad. Yani `getString` Android'de vardı, ortak kodda
 * yok. Grafik `MainActivity` içindeyken bu satırlar sorunsuz derleniyordu; hata
 * ancak modül taşınıp `compileKotlinJvm` çalıştığında çıktı (Android derlemesi
 * aynı koşuda hâlâ geçiyordu).
 *
 * Gövde, navigation'ın kendi `NavType.StringType.get` gerçeklemesinin birebir
 * aynısı: önce anahtar var mı ve null mu diye bakılıyor, çünkü `getString`
 * olmayan anahtarda fırlatıyor.
 */
private fun NavBackStackEntry.rotaArgumani(anahtar: String): String? =
    arguments?.read {
        if (!contains(anahtar) || isNull(anahtar)) null else getString(anahtar)
    }

/**
 * Uygulamanın gezinme grafiği — **ortak**, yani Android ve iOS aynı grafiği
 * kullanıyor.
 *
 * Bu dosya i3e-2'de `app/MainActivity.kt`ten taşındı. Taşınabilmesinin sebebi
 * Compose Multiplatform'un navigation çatallamasının AndroidX ile **aynı paket
 * adlarını** kullanması: `androidx.navigation.compose.NavHost` iki tarafta da
 * aynı isim, dolayısıyla grafiğin gövdesinde tek bir import değişmedi.
 *
 * Taşımanın asıl kazancı hedef listesinin tekliği. On beş hedef ve on bağlama
 * composable'ı iki kabukta ayrı ayrı yazılsaydı, yeni bir ekran eklendiğinde
 * biri güncellenip diğeri unutulurdu ve eksik olan platformda hata ancak
 * kullanıcı o düğmeye bastığında görünürdü.
 *
 * ### Kabukta ne kaldı
 * Oturumun diskten geri yüklenmesi, arka plan senkronizasyonu ve tema kabukta.
 * Bu üçü platforma özgü: Android'de WorkManager, iOS'ta BGTaskScheduler.
 * Grafik oturumun VARLIĞINI [oturumVar] ile parametre olarak alıyor —
 * `SessionManager`ı kendisi okumuyor ki kabuk yükleme ekranını kendi
 * kararıyla gösterebilsin.
 *
 * @param oturumVar Geçerli bir oturum var mı. Yalnızca iki şeyi belirliyor:
 *        açılış hedefi, ve oturum düştüğünde girişe dönüş.
 */
@Composable
fun GymGezinme(oturumVar: Boolean) {
    val navController = rememberNavController()

    // Başlangıç hedefi ilk karede sabitleniyor; sonraki değişiklikleri NavHost
    // zaten dikkate almaz, `remember` bunu açık hâle getiriyor.
    val baslangicHedefi = remember { if (oturumVar) "dashboard" else "login" }

    // Oturum TEPKİLİ okunuyor.
    //
    // Önceden kabukta yalnızca `.value` ile bir kez okunuyordu. Yenileme jetonu
    // reddedildiğinde `SessionManager` oturumu düşürüyor (bkz.
    // `currentAccessToken`), ama arayüz bunu hiç görmüyordu: kullanıcı giriş
    // yapmış görünen bir panelde kalıyor, açtığı ilk veri ekranı
    // `requireTenantId()` yüzünden çöküyordu.
    LaunchedEffect(oturumVar) {
        if (!oturumVar && navController.currentDestination?.route != "login") {
            navController.navigate("login") {
                // Geri yığınında veri ekranı bırakmıyoruz: geri tuşu oturumsuz
                // bir ekrana dönmemeli.
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
                // Ekran hâlâ ViewModel'i TANIMIYOR: durumu parametre olarak
                // alıyor, tıklamayı geri çağrıyla bildiriyor. Değişen tek şey
                // bağlamanın nerede yapıldığı — eskiden Android kabuğundaydı,
                // artık burada ve iki platform aynı kodu kullanıyor.
                //
                // Ekranın durumu dışarıdan alması bu taşımadan sonra da
                // önemini koruyor: görüntü testleri ekranı Koin olmadan,
                // düz parametrelerle çiziyor.
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
                FinansBagla(navController)
            }
            composable("market") {
                MarketBagla(navController)
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
                PersonelBagla(navController)
            }
            composable("register_member") {
                UyeKayitBagla(navController, yenileme = false, uyeId = "")
            }
            composable(
                route = "renew_package/{memberId}",
                arguments = listOf(navArgument("memberId") { type = NavType.StringType })
            ) { backStackEntry ->
                val memberId = backStackEntry.rotaArgumani("memberId").orEmpty()
                UyeKayitBagla(navController, yenileme = true, uyeId = memberId)
            }
            composable(
                route = "member_detail/{memberId}",
                arguments = listOf(navArgument("memberId") { type = NavType.StringType })
            ) { backStackEntry ->
                val memberId = backStackEntry.rotaArgumani("memberId").orEmpty()
                UyeDetayBagla(navController, memberId)
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
                    paketId = backStackEntry.rotaArgumani("packageId"),
                    navController = navController,
                )
            }
        }
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
    navController: NavHostController,
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
private fun AyarlarBagla(navController: NavHostController) {
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
private fun PanoBagla(navController: NavHostController) {
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
private fun UyeListesiBagla(navController: NavHostController) {
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
 * Gün aritmetiği artık burada da DEĞİL, ViewModel'in içinde: bağlama yalnızca
 * `oncekiGun` / `sonrakiGun` / `bugune` çağırıyor ve seçili günü epoch
 * milisaniye olarak alıyor. Önceden bu üç hesap `java.time.LocalDate` ile tam
 * bu dosyada yapılıyordu; ViewModel ortak modüle taşınırken taşınamayacak tek
 * parça oydu, dolayısıyla hesabın kendisi de içeri alındı.
 *
 * Sheet ve diyaloğun açık/kapalı hâli de burada: kayıt reddedildiğinde
 * (çakışma, seans hakkı yok) sheet açık kalmalı ve bunu yalnızca olayları
 * dinleyen taraf bilebilir.
 */
@Composable
private fun TakvimBagla(navController: NavHostController) {
    val model: CalendarViewModel = koinViewModel()
    val durum by model.uiState.collectAsState()
    val secilenGunMs by model.secilenGunMs.collectAsState()
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

    TakvimEkrani(
        secilenGunMs = secilenGunMs,
        randevular = durum.appointments,
        uyeler = durum.members,
        personeller = durum.staffList,
        randevuEklemeAcik = eklemeAcik,
        secilenRandevu = secilenRandevu,
        onGeri = { navController.popBackStack() },
        onOncekiGun = model::oncekiGun,
        onSonrakiGun = model::sonrakiGun,
        onBugun = model::bugune,
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
    navController: NavHostController,
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

/**
 * Personel yönetiminin Android bağlaması.
 *
 * Diyalogların açık/kapalı hâli burada tutuluyor çünkü kapatma kararı
 * SONUCA bağlı: kayıt başarılıysa diyalog kapanıyor, reddedilirse açık
 * kalıyor ki kullanıcı yazdıklarını kaybetmesin.
 */
@Composable
private fun PersonelBagla(navController: NavHostController) {
    val model: PersonnelViewModel = koinViewModel()
    val personeller by model.staffList.collectAsState(initial = emptyList())
    val yazabilir by model.canWrite.collectAsState()
    val snackbarDurumu = remember { SnackbarHostState() }

    var formHedefi by remember { mutableStateOf<PersonelFormHedefi?>(null) }
    var silinecek by remember { mutableStateOf<StaffEntity?>(null) }

    LaunchedEffect(Unit) {
        model.events.collect { olay ->
            when (olay) {
                is PersonnelEvent.Saved -> formHedefi = null
                is PersonnelEvent.Deleted -> snackbarDurumu.showSnackbar("Personel silindi.")
                is PersonnelEvent.Failed -> snackbarDurumu.showSnackbar(olay.message)
            }
        }
    }

    PersonelEkrani(
        personeller = personeller,
        yazabilir = yazabilir,
        formHedefi = formHedefi,
        silinecek = silinecek,
        onGeri = { navController.popBackStack() },
        onYeniPersonel = { formHedefi = PersonelFormHedefi(null) },
        onPersonelSec = { formHedefi = PersonelFormHedefi(it) },
        onFormKapat = { formHedefi = null },
        onKaydet = { personelId, form ->
            model.saveStaff(
                staffId = personelId,
                name = form.name,
                title = form.title,
                branch = form.branch,
                commissionPercent = form.commissionPercent,
                salary = form.salary,
                phone = form.phone,
                nickname = form.nickname,
                role = form.role,
                authUserId = form.authUserId,
            )
        },
        onSilIste = { silinecek = it },
        onSilOnayla = { id ->
            model.deleteStaff(id)
            silinecek = null
        },
        onSilVazgec = { silinecek = null },
        snackbarDurumu = snackbarDurumu,
    )
}

/** Finans ekranının Android bağlaması. */
@Composable
private fun FinansBagla(navController: NavHostController) {
    val model: FinanceViewModel = koinViewModel()
    val durum by model.uiState.collectAsState()
    val gorebilir by model.gorebilir.collectAsState()
    val snackbarDurumu = remember { SnackbarHostState() }
    var eklemeAcik by remember { mutableStateOf(false) }

    // Kayıt sonucu her durumda kullanıcıya bildiriliyor.
    LaunchedEffect(Unit) {
        model.events.collect { olay ->
            when (olay) {
                is FinanceEvent.Saved -> snackbarDurumu.showSnackbar("Kayıt eklendi.")
                is FinanceEvent.Failed -> snackbarDurumu.showSnackbar(olay.message)
            }
        }
    }

    FinansEkrani(
        gorebilir = gorebilir,
        ay = durum.selectedMonth,
        yil = durum.selectedYear,
        aylikCiro = durum.monthlyRevenue,
        ucAylikCiro = durum.quarterlyRevenue,
        altiAylikCiro = durum.halfYearlyRevenue,
        yillikCiro = durum.yearlyRevenue,
        gelir = durum.totalIncome,
        gider = durum.totalExpense,
        netKar = durum.totalProfit,
        turSuzgeci = durum.selectedFilter,
        yontemSuzgeci = durum.selectedPaymentMethod,
        kayitlar = durum.entries,
        eklemeAcik = eklemeAcik,
        onGeri = { navController.popBackStack() },
        onDonemDegisti = { ay, yil -> model.setPeriod(ay, yil) },
        onTurSuzgeci = { model.setFilter(it) },
        onYontemSuzgeci = { model.setMethodFilter(it) },
        onEklemeAc = { eklemeAcik = true },
        onEklemeKapat = { eklemeAcik = false },
        onKayitEkle = { tutar, kategori, aciklama, yontem, gelirMi ->
            // Yeni yazımların tamamı deftere gider.
            model.addEntry(
                amountText = tutar,
                categoryName = kategori,
                description = aciklama,
                paymentMethodName = yontem,
                isIncome = gelirMi,
            )
            eklemeAcik = false
        },
        snackbarDurumu = snackbarDurumu,
    )
}

/** Market / POS ekranının Android bağlaması. */
@Composable
private fun MarketBagla(navController: NavHostController) {
    val model: MarketViewModel = koinViewModel()
    val durum by model.uiState.collectAsState()
    val urunYonetebilir by model.canManageProducts.collectAsState()
    val snackbarDurumu = remember { SnackbarHostState() }

    var urunEklemeAcik by remember { mutableStateOf(false) }
    var duzenlenenUrun by remember { mutableStateOf<ProductEntity?>(null) }
    var odemeAcik by remember { mutableStateOf(false) }

    // Sipariş sonucu her durumda bildiriliyor. Ödeme sayfası yalnızca
    // BAŞARIDA kapanıyor: başarısız sipariş sessizce "başarılı" görünmesin.
    LaunchedEffect(Unit) {
        model.events.collect { olay ->
            when (olay) {
                is MarketEvent.OrderCompleted -> {
                    odemeAcik = false
                    snackbarDurumu.showSnackbar("Sipariş tamamlandı (#${olay.orderId.take(8)})")
                }
                is MarketEvent.ProductSaved -> snackbarDurumu.showSnackbar("Ürün kaydedildi.")
                is MarketEvent.ProductDeleted -> snackbarDurumu.showSnackbar("Ürün silindi.")
                is MarketEvent.Failed -> snackbarDurumu.showSnackbar(olay.message)
            }
        }
    }

    MarketEkrani(
        durum = durum,
        urunYonetebilir = urunYonetebilir,
        urunEklemeAcik = urunEklemeAcik,
        duzenlenenUrun = duzenlenenUrun,
        odemeAcik = odemeAcik,
        onGeri = { navController.popBackStack() },
        onSiparisGecmisi = { navController.navigate("order_history") },
        onUrunEklemeAc = { urunEklemeAcik = true },
        onUrunEklemeKapat = { urunEklemeAcik = false },
        onUrunDuzenle = { duzenlenenUrun = it },
        onSepeteEkle = { model.addToCart(it) },
        onSepettenCikar = { model.removeFromCart(it) },
        onUrunSil = { model.deleteProduct(it) },
        onUrunKaydet = { urunId, ad, kategori, fiyat, stok ->
            model.saveProduct(
                productId = urunId,
                name = ad,
                category = kategori,
                price = fiyat,
                stock = stok,
            )
            urunEklemeAcik = false
            duzenlenenUrun = null
        },
        onOdemeAc = { odemeAcik = true },
        onOdemeKapat = { odemeAcik = false },
        onUyeSec = { model.selectMember(it) },
        onOdemeTuru = { model.setPaymentType(it) },
        onOdemeDurumu = { model.setPaymentStatus(it) },
        onTeslimDurumu = { model.setDeliveryStatus(it) },
        onIskonto = { model.setDiscount(Decimals.parseOrDefault(it)) },
        onNotlar = { model.setNotes(it) },
        onOdemeOnayla = { model.checkout() },
        snackbarDurumu = snackbarDurumu,
    )
}

/**
 * Üye detayının Android bağlaması.
 *
 * Ekranın dört sekmesi eskiden her biri kendi `koinViewModel()`ini
 * çekiyordu; artık verilerin hepsi burada toplanıp parametre olarak
 * geçiyor.
 *
 * Silme SONUCA bağlı: geri gezinme yalnızca `Deleted` olayı gelince
 * yapılıyor. Önceden sonuç beklenmeden yapılıyordu ve silme başarısız
 * olduğunda kullanıcı listeye dönüp üyeyi orada görüyordu.
 */
@Composable
private fun UyeDetayBagla(
    navController: NavHostController,
    uyeId: String,
) {
    val model: MemberViewModel = koinViewModel()
    val uye by model.getMemberById(uyeId).collectAsState(initial = null)
    val olcumler by model.getMeasurements(uyeId).collectAsState(initial = emptyList())
    val hareketler by model.getLedgerForMember(uyeId).collectAsState(initial = emptyList())
    val paketler by model.packages.collectAsState()
    val snackbarDurumu = remember { SnackbarHostState() }

    var secilenSekme by remember { mutableStateOf(0) }
    var silmeOnayiAcik by remember { mutableStateOf(false) }
    var siliniyor by remember { mutableStateOf(false) }
    var kalanBorc by remember { mutableStateOf<Money?>(null) }

    LaunchedEffect(uyeId, uye?.paymentStatus) {
        kalanBorc = model.outstandingBalance(uyeId)
    }

    LaunchedEffect(Unit) {
        model.events.collect { olay ->
            when (olay) {
                is MemberEvent.Deleted -> navController.popBackStack()
                is MemberEvent.Saved -> snackbarDurumu.showSnackbar(olay.message)
                is MemberEvent.Failed -> {
                    siliniyor = false
                    snackbarDurumu.showSnackbar(olay.message)
                }
            }
        }
    }

    UyeDetayEkrani(
        uye = uye,
        secilenSekme = secilenSekme,
        silmeOnayiAcik = silmeOnayiAcik,
        siliniyor = siliniyor,
        simdiMs = Now.epochMillis(),
        kalanBorc = kalanBorc,
        olcumler = olcumler,
        aktifPaket = paketler.find { it.id == uye?.activePackageId },
        hareketler = hareketler,
        onGeri = { navController.popBackStack() },
        onSekmeSec = { secilenSekme = it },
        onSilIste = { silmeOnayiAcik = true },
        onSilOnayla = {
            siliniyor = true
            silmeOnayiAcik = false
            model.deleteMember(uyeId)
        },
        onSilVazgec = { silmeOnayiAcik = false },
        onTahsilat = { tutar -> model.markAsPaid(uyeId, tutar) },
        onSaglikKaydet = { model.updateMember(it) },
        onOlcumEkle = { boy, kilo, omuz, gogus, karin, kalca, bacak, kol, not ->
            model.addMeasurement(
                memberId = uyeId,
                height = boy,
                weight = kilo,
                shoulder = omuz,
                chest = gogus,
                waist = karin,
                hips = kalca,
                leg = bacak,
                arm = kol,
                notes = not,
            )
        },
        onOlcumSil = { model.deleteMeasurement(it) },
        snackbarDurumu = snackbarDurumu,
    )
}
