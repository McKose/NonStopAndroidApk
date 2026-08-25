package com.gymapp.arayuz.uyeler

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gymapp.arayuz.ortak.SaltOkunurSerit
import com.gymapp.data.access.AppDestination
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.domain.MemberScope
import com.gymapp.domain.Membership
import com.gymapp.domain.MembershipState
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentState
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.StaffRole
import com.gymapp.domain.labelTr
import kotlinx.coroutines.launch

/**
 * Üye listesi — `app`'teki `MemberListScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Saat neden parametre
 * Ekran `System.currentTimeMillis()` çağırıyordu. İki sorun:
 *
 *  1. JVM'e özgü; Kotlin/Native'de yok, yani ekran taşınamazdı.
 *  2. Ekranın saati kendi okuması hoisting'e aykırı. Üyelik durumu
 *     ("Aktif" / "Süresi doldu") bu değerden türetiliyor, yani ekranın
 *     çıktısı dışarıdan görünmeyen bir girdiye bağlıydı — görüntü testi de
 *     her koşuda farklı sonuç verirdi.
 *
 * [simdiMs] dışarıdan geliyor. Değer `Now.epochMillis()` ile üretiliyor;
 * `Now` zaten ortak modülde ve platformdan bağımsız.
 *
 * ### Tahsilat diyaloğu neden dışarıdan sürülüyor
 * Diyalog açıldığında üyenin kalan borcu **veritabanından** okunuyor; bu
 * askıya alınabilir bir çağrı ve ekranın işi değil. Ekran yalnızca
 * [tahsilatUyesi] ve [tahsilatBorcu] doluysa diyaloğu çiziyor, "tahsilat
 * istendi" olayını bildiriyor. Borcun getirilmesi çağıranın sorumluluğu.
 *
 * @param tahsilatUyesi diyalog açık olacak üye, kapalıysa `null`
 * @param tahsilatBorcu o üyenin kalan borcu; henüz okunmadıysa `null`
 *   (diyalog tutarsız bir tutar göstermemek için beklemede kalır)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UyeListesiEkrani(
    uyeler: List<MemberEntity>,
    yukleniyor: Boolean,
    arama: String,
    rol: StaffRole,
    kapsam: MemberScope,
    kapsamSecilebilir: Boolean,
    personelBaglantisiYok: Boolean,
    simdiMs: Long,
    tahsilatUyesi: MemberEntity?,
    tahsilatBorcu: Money?,
    onAramaDegisti: (String) -> Unit,
    onKapsamDegisti: (MemberScope) -> Unit,
    onUyeAc: (String) -> Unit,
    onYeniUye: () -> Unit,
    onYenile: (String) -> Unit,
    onTahsilatIste: (MemberEntity) -> Unit,
    onTahsilatOnayla: (Money) -> Unit,
    onTahsilatVazgec: () -> Unit,
    onPaketler: () -> Unit,
    onFinans: () -> Unit,
    onMarket: () -> Unit,
    onAyarlar: () -> Unit,
    snackbarDurumu: SnackbarHostState = remember { SnackbarHostState() },
) {
    val cekmece = rememberDrawerState(initialValue = DrawerValue.Closed)
    val korutinAlani = rememberCoroutineScope()

    if (tahsilatUyesi != null && tahsilatBorcu != null) {
        TahsilatDiyalogu(
            uyeAdi = tahsilatUyesi.fullName,
            kalanBorc = tahsilatBorcu,
            onOnayla = onTahsilatOnayla,
            onVazgec = onTahsilatVazgec,
        )
    }

    ModalNavigationDrawer(
        drawerState = cekmece,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                NavigationDrawerItem(
                    label = { Text("Üyeler") },
                    selected = true,
                    onClick = { korutinAlani.launch { cekmece.close() } },
                    icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                )

                // Çekmece rolün göremediği hedefi çizmiyor.
                //
                // Önceden bu liste koşulsuzdu: pano eğitmene Finans'ı
                // gizliyordu ama aynı ekran buradan iki dokunuşla açılıyordu.
                // Yani kısıt gerçek bir kısıt değil, yalnızca bir ekranda
                // görünmeyen bir düğmeydi. Görünürlük kararı `AppDestination`
                // içinde, tek yerde.
                val hedefler = listOf(
                    CekmeceHedefi(AppDestination.FINANCE, "Finans", Icons.Default.AccountBalanceWallet, onFinans),
                    CekmeceHedefi(AppDestination.PACKAGES, "Ders Paketleri", Icons.Default.Inventory, onPaketler),
                    CekmeceHedefi(AppDestination.MARKET, "Market", Icons.Default.ShoppingCart, onMarket),
                    CekmeceHedefi(AppDestination.SETTINGS, "Ayarlar", Icons.Default.Settings, onAyarlar),
                ).filter { it.hedef.isVisibleTo(rol) }

                hedefler.forEach { girdi ->
                    NavigationDrawerItem(
                        label = { Text(girdi.etiket) },
                        selected = false,
                        onClick = {
                            korutinAlani.launch { cekmece.close() }
                            girdi.onTikla()
                        },
                        icon = { Icon(girdi.ikon, contentDescription = null) },
                    )
                }
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarDurumu) },
            topBar = {
                TopAppBar(
                    title = { Text("Üye Listesi") },
                    navigationIcon = {
                        IconButton(onClick = { korutinAlani.launch { cekmece.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onYeniUye) {
                    Icon(Icons.Default.Add, contentDescription = "Üye Ekle")
                }
            },
        ) { bosluk ->
            Column(modifier = Modifier.padding(bosluk).fillMaxSize()) {
                OutlinedTextField(
                    value = arama,
                    onValueChange = onAramaDegisti,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("İsim veya telefon ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )

                // Kapsam görünür bir seçim: hangi listeye bakıldığı ekranda
                // yazıyor ve tek dokunuşla değişiyor. Sessiz süzme, eğitmenin
                // yeni kaydettiği (henüz randevusu olmayan) üyeyi bulamaması
                // demek olurdu.
                if (kapsamSecilebilir) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = kapsam == MemberScope.MINE,
                            onClick = { onKapsamDegisti(MemberScope.MINE) },
                            label = { Text("Üyelerim") },
                        )
                        FilterChip(
                            selected = kapsam == MemberScope.ALL,
                            onClick = { onKapsamDegisti(MemberScope.ALL) },
                            label = { Text("Tüm üyeler") },
                        )
                    }
                }

                // Bağlantısı olmayan eğitmende "üyelerim" tanımsız; liste
                // süzülmüyor ve sebebi yazıyor.
                if (personelBaglantisiYok) {
                    SaltOkunurSerit(
                        text = "Hesabınız bir personel kaydına bağlı olmadığı için " +
                            "\"üyelerim\" ayrımı yapılamıyor; salonun tamamı listeleniyor.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (yukleniyor) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uyeler.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Boş listenin sebebi kapsam olabilir; "üye bulunamadı"
                        // demek kullanıcıyı salonda hiç üye yok sanmaya iterdi.
                        Text(
                            if (kapsam == MemberScope.MINE) {
                                "Size atanmış randevusu olan üye yok. " +
                                    "Salonun tamamı için \"Tüm üyeler\" seçin."
                            } else {
                                "Üye bulunamadı."
                            },
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uyeler) { uye ->
                            UyeSatiri(
                                uye = uye,
                                simdiMs = simdiMs,
                                onTikla = { onUyeAc(uye.id) },
                                onYenile = { onYenile(uye.id) },
                                onTahsilatIste = { onTahsilatIste(uye) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Çekmecedeki bir girdi ve hangi hedefe ait olduğu. */
private data class CekmeceHedefi(
    val hedef: AppDestination,
    val etiket: String,
    val ikon: ImageVector,
    val onTikla: () -> Unit,
)

@Composable
private fun UyeSatiri(
    uye: MemberEntity,
    simdiMs: Long,
    onTikla: () -> Unit,
    onYenile: () -> Unit,
    onTahsilatIste: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onTikla)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = uye.fullName, style = MaterialTheme.typography.titleMedium)
                    if (uye.paymentStatus == PaymentState.PENDING) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape,
                            modifier = Modifier.padding(start = 8.dp).clickable { onTahsilatIste() },
                        ) {
                            Text(
                                "Ödeme Bekliyor",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                Text(
                    text = PhoneNumber.formatForDisplay(uye.phone),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Durum kayıtlı kolondan değil bitiş tarihinden türetiliyor;
                // süresi dolmuş üye artık "Aktif" görünmüyor. Paket kimliği
                // (UUID) kullanıcıya hiçbir şey ifade etmediği için
                // gösterilmiyor.
                val durum = Membership.stateOf(uye.status, uye.endDateMs, simdiMs)
                Text(
                    text = "Durum: ${durum.labelTr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (durum == MembershipState.ACTIVE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Button(
                onClick = onYenile,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text("Yenile", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
