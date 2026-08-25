package com.gymapp.arayuz.paketler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymapp.domain.Decimals
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType
import com.gymapp.domain.labelTr

/**
 * Paket ekleme/düzenleme formu — `app`'teki `AddPackageScreen`'in taşınmış hâli.
 *
 * ### Form durumu neden İÇERİDE
 * Diğer taşınan ekranlarda durum tamamen dışarıdan geliyor; burada yazılan
 * metinler ekranın kendi içinde tutuluyor. Ayrım bilinçli: bu değerler
 * kullanıcının o anki YAZMA durumu, uygulamanın verisi değil. Dışarı
 * çıkarılsaydı her tuş vuruşu çağırana kadar gidip geri gelirdi ve iOS
 * kabuğunun da aynı ara durumu ayrıca tutması gerekirdi.
 *
 * Dışarıdan gelen şey başlangıç değeri ([baslangic]) ve kaydetme eylemi.
 *
 * @param baslangic düzenlemede mevcut değerler, yeni pakette `null`
 * @param yukleniyor düzenlenecek paket sunucudan/veritabanından okunurken
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaketFormuEkrani(
    baslangic: PaketFormu?,
    yukleniyor: Boolean,
    onKaydet: (PaketFormu) -> Unit,
    onGeri: () -> Unit,
    snackbarDurumu: SnackbarHostState = remember { SnackbarHostState() },
) {
    val duzenleme = baslangic != null

    // `remember(baslangic)`: düzenlenecek paket geç geldiğinde (yükleme bitince)
    // form onun değerleriyle yeniden kurulmalı. Anahtarsız `remember` ilk
    // karedeki boş hâli sonsuza kadar tutar ve kullanıcı boş bir düzenleme
    // formu görürdü.
    var form by remember(baslangic) { mutableStateOf(baslangic ?: PaketFormu()) }

    val seansEtiketi = if (form.sinirsiz) "Sınırsız" else form.seansSayisi.ifBlank { "0" }
    val uretilenAd = "$seansEtiketi - ${form.tur.labelTr()} - ${form.kategori.labelTr()}"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarDurumu) },
        topBar = {
            TopAppBar(
                title = { Text(if (duzenleme) "Paketi Düzenle" else "Yeni Paket Ekle") },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { bosluk ->
        if (yukleniyor) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.padding(bosluk).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Paket Adı (Otomatik)", style = MaterialTheme.typography.labelSmall)
                        Text(
                            uretilenAd,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sınırsız (Abonman)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = form.sinirsiz,
                        onCheckedChange = { form = form.copy(sinirsiz = it) },
                    )
                }

                // Sınırsız (abonman) paket seans sayısı taşımaz; `-1` sentinel'i
                // yerine ayrı bir anahtar kullanılıyor, böylece geçersiz değer
                // girilemiyor.
                if (!form.sinirsiz) {
                    OutlinedTextField(
                        value = form.seansSayisi,
                        onValueChange = { form = form.copy(seansSayisi = it) },
                        label = { Text("Seans Sayısı") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                Text("Paket Türü")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrainingType.entries.forEach { t ->
                        FilterChip(
                            selected = form.tur == t,
                            onClick = { form = form.copy(tur = t) },
                            label = { Text(t.labelTr()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text("Kategori")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PackageCategory.entries.forEach { k ->
                        FilterChip(
                            selected = form.kategori == k,
                            onClick = { form = form.copy(kategori = k) },
                            label = { Text(k.labelTr()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                OutlinedTextField(
                    value = form.fiyat,
                    onValueChange = { form = form.copy(fiyat = it) },
                    label = { Text("Baz Fiyat (TL)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

                OutlinedTextField(
                    value = form.gun,
                    onValueChange = { form = form.copy(gun = it) },
                    label = { Text("Geçerlilik Süresi (Gün)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                // Kaydetme yalnızca üç alan da geçerliyken açık: geçersiz
                // değerle kaydedip sunucudan hata almak yerine düğme kapalı
                // kalıyor.
                val kaydedilebilir = Decimals.parseOrNull(form.fiyat) != null &&
                    (form.gun.toIntOrNull() ?: 0) > 0 &&
                    (form.sinirsiz || (form.seansSayisi.toIntOrNull() ?: 0) > 0)

                Button(
                    onClick = { onKaydet(form) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = kaydedilebilir,
                ) {
                    Text(if (duzenleme) "Güncelle" else "Kaydet")
                }
            }
        }
    }
}
