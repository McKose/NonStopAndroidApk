package com.gymapp.arayuz.paketler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gymapp.arayuz.ortak.SaltOkunurSerit
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.domain.ParaBicimi
import com.gymapp.domain.Money
import com.gymapp.domain.labelTr

/**
 * Paket listesi — `app`'teki `PackageListScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Durum dışarıdan
 * Özgün ekran ViewModel'ini Koin'den kendisi çekiyordu. Burada almıyor:
 * paketler ve yetki parametre, silme/gezinme geri çağrı. Aynı ekran böylece
 * Android'de ViewModel'e, testte sabit listeye, iOS kabuğunda kendi bağlamasına
 * takılabiliyor.
 *
 * ### `snackbarDurumu` neden dışarıdan
 * Silme sonucunun bildirimi olay akışından geliyor ve o akış ViewModel'e ait.
 * Ekran kendi `SnackbarHostState`'ini kursaydı, çağıranın oraya mesaj basmasının
 * yolu olmazdı. Varsayılan değeri var ki test ve önizleme tek satırla çağırsın.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaketListesiEkrani(
    paketler: List<PackageEntity>,
    yazabilir: Boolean,
    onEkle: () -> Unit,
    onDuzenle: (String) -> Unit,
    onSil: (String) -> Unit,
    onGeri: () -> Unit,
    snackbarDurumu: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarDurumu) },
        topBar = {
            TopAppBar(
                title = { Text("Paket Yönetimi") },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
        floatingActionButton = {
            // Yetkisi olmayanda düğme hiç çizilmiyor. Pasif (disabled) bırakmak
            // da bir seçenekti; gizlemek tercih edildi çünkü ekranın altındaki
            // şerit sebebi zaten söylüyor ve pasif bir düğme "belki açılır"
            // izlenimi verirdi.
            if (yazabilir) {
                FloatingActionButton(onClick = onEkle) {
                    Icon(Icons.Default.Add, contentDescription = "Paket Ekle")
                }
            }
        },
    ) { bosluk ->
        if (paketler.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bosluk),
                contentAlignment = Alignment.Center,
            ) {
                Text("Henüz paket tanımlanmamış.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(bosluk),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!yazabilir) {
                    item {
                        SaltOkunurSerit(
                            "Paketleri görüntüleyebilirsiniz. Değiştirmek salon " +
                                "sahibi ve yönetici yetkisi gerektiriyor.",
                        )
                    }
                }
                items(paketler) { paket ->
                    PaketSatiri(
                        paket = paket,
                        // Yetki yoksa satır düzenleme ekranını açmıyor ve silme
                        // simgesi çizilmiyor.
                        onSil = if (yazabilir) ({ onSil(paket.id) }) else null,
                        onTikla = { if (yazabilir) onDuzenle(paket.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaketSatiri(paket: PackageEntity, onSil: (() -> Unit)?, onTikla: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onTikla) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = paket.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    // Eskiden `"₺${Money(...)}"` yazıyordu: `Money.toString()`
                    // binlik ayırıcı üretmiyor, yani dört haneli fiyatlar
                    // `₺12000,00` diye okunuyordu. `ParaBicimi` ayırıcıyı da
                    // koyuyor ve uygulamanın geri kalanıyla aynı biçimi veriyor.
                    text = "${ParaBicimi.tl(Money(paket.basePriceMinor))} • ${paket.validityDays} Gün",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    // Sınırsızlık `null` seans kotasından anlaşılır; `-1` sentinel'i kalktı.
                    text = paket.sessionCount?.let { "$it Seans" } ?: "Sınırsız Seans",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Text(
                    text = "${paket.type.labelTr()} • ${paket.category.labelTr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            if (onSil != null) {
                IconButton(onClick = onSil) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
                }
            }
        }
    }
}
