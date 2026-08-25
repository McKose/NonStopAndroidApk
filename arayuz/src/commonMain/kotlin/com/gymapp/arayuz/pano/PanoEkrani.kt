package com.gymapp.arayuz.pano

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymapp.arayuz.ortak.SaltOkunurSerit
import com.gymapp.data.access.AppDestination
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.StaffRole
import com.gymapp.domain.TarihBicimi
import com.gymapp.domain.labelTr

/**
 * Pano — `app`'teki `DashboardScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Neden `DashboardUiState` yerine tek tek parametreler
 * Ekran ViewModel'in durum sınıfını almıyor. O sınıf `app`'te yaşıyor ve
 * ekranla birlikte taşınsaydı, taşıma bu dilimde ViewModel'i de içine
 * çekerdi — oysa ViewModel'ler ayrı bir dilim (i3e). Parametreler ekranın
 * gerçek sözleşmesi zaten: durum sınıfının on alanının yalnızca yedisi
 * çiziliyor.
 *
 * ### Yetki
 * `gorebilir()` yardımcısı yerine [rol] geçiliyor ve görünürlük
 * `AppDestination.isVisibleTo` ile hesaplanıyor — kararın zaten tek kaynağı
 * o. Yardımcı yalnızca aynı çağrıyı ileten bir sarmalayıcıydı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanoEkrani(
    rol: StaffRole,
    aktifUye: Int,
    gunlukRandevular: List<AppointmentEntity>,
    uyeler: List<MemberEntity>,
    personeller: List<StaffEntity>,
    kritikUyarilar: List<String>,
    personelBaglantisiYok: Boolean,
    onUyeler: () -> Unit,
    onFinans: () -> Unit,
    onMarket: () -> Unit,
    onTakvim: () -> Unit,
    onPaketler: () -> Unit,
    onAyarlar: () -> Unit,
) {
    fun gorebilir(hedef: AppDestination) = hedef.isVisibleTo(rol)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Non Stop", fontWeight = FontWeight.Bold) },
                actions = {
                    // Ayarlar herkeste duruyor: "Çıkış Yap" burada. Eskiden
                    // eğitmene gizleniyordu ve uygulamadan çıkmanın tek yolu
                    // üye listesindeki çekmeceden dolaşmaktı.
                    if (gorebilir(AppDestination.SETTINGS)) {
                        IconButton(onClick = onAyarlar) {
                            Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                        }
                    }
                    if (gorebilir(AppDestination.PACKAGES)) {
                        IconButton(onClick = onPaketler) {
                            Icon(Icons.Default.Inventory, contentDescription = "Paketler")
                        }
                    }
                },
            )
        },
    ) { bosluk ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bosluk)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Eğitmenin hesabı bir personel kaydına bağlı değilse pano boş
            // görünür. Sebebini yazmadan boş bırakmak, kullanıcıya "uygulama
            // verimi kaybetti" dedirtiyordu; asıl gereken iş salon sahibinde.
            if (personelBaglantisiYok) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SaltOkunurSerit(
                        "Hesabınız bir personel kaydına bağlı olmadığı için kendi " +
                            "dersleriniz ve üyeleriniz listelenemiyor. Salon sahibinin " +
                            "Ayarlar → Personel Yönetimi'nden kartınıza Supabase " +
                            "kullanıcı kimliğinizi girmesi gerekiyor.",
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        SayiKarti(
                            etiket = "Aktif Üye",
                            deger = aktifUye.toString(),
                            ikon = Icons.Default.People,
                            zemin = MaterialTheme.colorScheme.primaryContainer,
                        )
                    }
                    item {
                        SayiKarti(
                            etiket = "Günlük Ders",
                            deger = gunlukRandevular.size.toString(),
                            ikon = Icons.Default.Event,
                            zemin = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                }
            }

            item {
                Text(
                    "Hızlı Erişim",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Kısayollar rolün görebildiği hedeflerden üretiliyor; hangi
                // rolün neyi göreceği tek yerde (`AppDestination`). Eskiden
                // burada elle yazılmış bir "eğitmen değilse" koşulu vardı ve
                // üye listesindeki çekmece aynı kararı bağımsız veriyordu:
                // pano dördünü gizlerken çekmece dördünü de açıyordu.
                val kisayollar = listOf(
                    Kisayol(AppDestination.CALENDAR, "Takvim", Icons.Default.CalendarMonth, onTakvim),
                    Kisayol(AppDestination.MEMBERS, "Üyeler", Icons.Default.Groups, onUyeler),
                    Kisayol(AppDestination.PACKAGES, "Paketler", Icons.Default.Inventory, onPaketler),
                    Kisayol(AppDestination.MARKET, "Market", Icons.Default.ShoppingCart, onMarket),
                    Kisayol(AppDestination.FINANCE, "Finans", Icons.Default.Payments, onFinans),
                    Kisayol(AppDestination.SETTINGS, "Ayarlar", Icons.Default.Settings, onAyarlar),
                ).filter { gorebilir(it.hedef) }

                kisayollar.chunked(2).forEachIndexed { sira, satir ->
                    if (sira > 0) Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        satir.forEach { kisayol ->
                            KisayolKarti(
                                etiket = kisayol.etiket,
                                ikon = kisayol.ikon,
                                modifier = Modifier.weight(1f),
                                onTikla = kisayol.onTikla,
                            )
                        }
                        // Tek kalan kartın satırın tamamına yayılmaması için
                        // boş bir ağırlık bırakılıyor.
                        if (satir.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    "Bugünkü Randevular",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (gunlukRandevular.isEmpty()) {
                    Text(
                        "Bugün için randevu bulunmuyor.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(gunlukRandevular) { randevu ->
                RandevuSatiri(
                    randevu = randevu,
                    uye = uyeler.find { it.id == randevu.memberId },
                    personel = personeller.find { it.id == randevu.staffId },
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "Kritik Uyarılar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (kritikUyarilar.isEmpty()) {
                    Text(
                        "Her şey yolunda!",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(kritikUyarilar) { uyari -> UyariSatiri(uyari) }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

/** Panodaki bir hızlı erişim kartı ve hangi hedefe ait olduğu. */
private data class Kisayol(
    val hedef: AppDestination,
    val etiket: String,
    val ikon: ImageVector,
    val onTikla: () -> Unit,
)

@Composable
private fun SayiKarti(etiket: String, deger: String, ikon: ImageVector, zemin: Color) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = zemin),
        modifier = Modifier.width(160.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(ikon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(deger, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(etiket, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun KisayolKarti(
    etiket: String,
    ikon: ImageVector,
    modifier: Modifier,
    onTikla: () -> Unit,
) {
    OutlinedCard(onClick = onTikla, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(ikon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(etiket, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RandevuSatiri(
    randevu: AppointmentEntity,
    uye: MemberEntity?,
    personel: StaffEntity?,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = TarihBicimi.saat(randevu.startTimeMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    uye?.fullName ?: "Bilinmeyen Üye",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${randevu.trainingType.labelTr()} - ${personel?.fullName ?: "Eğitmen atanmadı"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun UyariSatiri(mesaj: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Text(mesaj, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
