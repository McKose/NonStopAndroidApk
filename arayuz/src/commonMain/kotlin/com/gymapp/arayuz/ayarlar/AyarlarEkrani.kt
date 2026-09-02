package com.gymapp.arayuz.ayarlar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gymapp.data.sync.SyncState
import com.gymapp.domain.SifreKurali

/**
 * Ayarlar — `app`'teki `SettingsScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Neden kendi dilimini hak etti
 * Taşınan diğer ekranlar yalnızca veri gösteriyordu; bu ekran ViewModel'in
 * DURUM MAKİNESİNE bağlı: çıkış tek bir geri çağrı değil, üç adımlı bir akış
 * (iste → onayla/vazgeç) ve arada gösterilen uyarı, gönderilmemiş kayıt
 * sayısına bağlı. Bu akışı olduğu gibi taşımak, "ekran ViewModel tanımaz"
 * kuralını bozmadan tek tek parçalamayı gerektiriyordu.
 *
 * ### Çıkış akışı neden dışarıda
 * [cikistaBekleyen] `null` değilse uyarı diyaloğu çiziliyor. Kararı ekran
 * vermiyor: kaç kaydın gönderilmediğini yalnızca veri katmanı bilir ve
 * "çıkabilir miyim" sorusunun cevabı ekranın çizim döngüsünde hesaplanamaz.
 * Ekranın işi sayıyı göstermek ve üç düğmeden hangisine basıldığını bildirmek.
 *
 * ### Diyalogların AÇIK/KAPALI hâli neden içeride
 * "Salon Bilgileri" diyaloğunun görünürlüğü ve içindeki metin kutusu ekranın
 * kendi durumu — [PaketFormuEkrani][com.gymapp.arayuz.paketler.PaketFormuEkrani]
 * ile aynı ayrım: yazma durumu uygulamanın verisi değil. Dışarı çıkarılsaydı
 * her tuş vuruşu ViewModel'e gidip geri gelirdi.
 *
 * ### Şifre diyaloğu neden dışarıda — salon diyaloğu içerideyken
 * Aradaki fark sınanabilirlik: bu diyaloğun DÖRT hâli var (boşta, gönderiliyor,
 * hata, başarılı) ve dördü de ekranda farklı bir şey çiziyor. Açık/kapalı bilgisi
 * içeride kalsaydı görüntü testleri diyaloğu hiç açamaz, dolayısıyla hiçbirini
 * çizemezdi — ve "başarılı" hâlinin görünmesi bu özelliğin bütün amacı. Salon
 * diyaloğunun tek bir hâli var, orada aynı gerekçe yok.
 *
 * @param cikistaBekleyen çıkış onayı bekliyorsa gönderilmemiş kayıt sayısı,
 *   beklemiyorsa `null`
 * @param sifreDurumu şifre değiştirme akışının o anki hâli; diyalog açıkken
 *   çiziliyor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyarlarEkrani(
    salonAdi: String,
    senkDurumu: SyncState,
    bekleyen: Int,
    cikistaBekleyen: Int?,
    sifreDurumu: SifreDurumu,
    sifreDiyaloguAcik: Boolean,
    onGeri: () -> Unit,
    onPersonel: () -> Unit,
    onSimdiEsitle: () -> Unit,
    onCikisIste: () -> Unit,
    onCikisiOnayla: () -> Unit,
    onCikistanVazgec: () -> Unit,
    onSalonAdiKaydet: (String) -> Unit,
    onSifreDegistir: (mevcut: String, yeni: String, tekrar: String) -> Unit,
    onSifreDiyaloguAc: () -> Unit,
    onSifreDiyaloguKapat: () -> Unit,
) {
    var salonDiyalogu by remember { mutableStateOf(false) }

    // Çıkışta cihazdaki veri siliniyor; gönderilmemiş kayıt varsa önce soruluyor.
    cikistaBekleyen?.let { adet ->
        AlertDialog(
            onDismissRequest = onCikistanVazgec,
            title = { Text("Gönderilmemiş değişiklik var") },
            text = {
                Text(
                    "$adet değişiklik henüz sunucuya gönderilmedi. Çıkış yapıldığında " +
                        "cihazdaki veriler silinir ve bu değişiklikler kaybolur.\n\n" +
                        "İnternet bağlantınız varsa önce \"Şimdi Eşitle\" deyip bekleyin.",
                )
            },
            confirmButton = {
                TextButton(onClick = onCikisiOnayla) {
                    Text("Yine de çık", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = onCikistanVazgec) { Text("Vazgeç") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { bosluk ->
        Column(
            modifier = Modifier
                .padding(bosluk)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Yönetim",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            AyarSatiri(
                baslik = "Personel Yönetimi",
                altBaslik = "Eğitmen ve çalışanları yönet",
                ikon = Icons.Default.Group,
                onTikla = onPersonel,
            )

            // KALDIRILDI: "Hakediş Oranları". Girilen iki oran yalnızca bu
            // ekranın kendisi tarafından yazılıp okunuyordu; gerçek hakediş
            // hesabı `staff.commissionBasisPoints` üzerinden yapılıyor
            // (`AppointmentRepository`). Yani salon sahibi buradan oranı
            // değiştirdiğinde hiçbir şey değişmiyor, ama değiştirdiğini
            // sanıyordu. Çalışıyormuş gibi görünen ama hiçbir etkisi olmayan
            // bir ayar, olmamasından daha kötü — oran artık personel kartından
            // giriliyor.

            HorizontalDivider()
            Text(
                "Sistem",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            AyarSatiri(
                baslik = "Salon Bilgileri",
                altBaslik = salonAdi,
                ikon = Icons.Default.Store,
                onTikla = { salonDiyalogu = true },
            )

            // Bir zamanlar burada hiçbir yerde okunmayan bir tercihi yazan
            // "Giriş Şifresi" alanı vardı; kaldırılmıştı. Bu satır onun yerine
            // GERÇEĞİNİ koyuyor: Supabase Auth üzerindeki şifreyi değiştiriyor.
            //
            // Gerekçesi `personel-davet`: yeni personele geçici bir şifre
            // üretiliyor ve kişinin onu değiştireceği bir yer olmadığı sürece
            // geçici şifre kalıcı hâle geliyordu.
            AyarSatiri(
                baslik = "Şifre Değiştir",
                altBaslik = "Giriş şifrenizi güncelleyin",
                ikon = Icons.Default.Lock,
                onTikla = onSifreDiyaloguAc,
            )

            AyarSatiri(
                baslik = "Sunucuya Eşitle",
                altBaslik = senkronizasyonOzeti(senkDurumu, bekleyen),
                ikon = Icons.Default.Sync,
                onTikla = onSimdiEsitle,
            )

            AyarSatiri(
                baslik = "Çıkış Yap",
                altBaslik = "Oturumu sonlandır",
                ikon = Icons.Default.Logout,
                onTikla = onCikisIste,
            )
        }

        if (salonDiyalogu) {
            AyarDiyalogu(
                baslik = "Salon Bilgileri",
                onKapat = { salonDiyalogu = false },
            ) {
                // `remember(salonAdi)`: ad dışarıdan geç geldiğinde (ilk
                // eşitlemeden sonra) kutu onunla dolmalı. Anahtarsız `remember`
                // ilk karedeki boş adı diyalog kapanana kadar tutardı.
                var yeniAd by remember(salonAdi) { mutableStateOf(salonAdi) }
                OutlinedTextField(
                    value = yeniAd,
                    onValueChange = { yeniAd = it },
                    label = { Text("Salon Adı") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSalonAdiKaydet(yeniAd)
                        salonDiyalogu = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    // Boş ad kaydedilirse ekranda "Salon Bilgileri" satırının
                    // alt yazısı boş kalır ve salon adını geri yazmanın yolu
                    // kalmaz gibi görünür.
                    enabled = yeniAd.isNotBlank(),
                ) {
                    Text("Kaydet")
                }
            }
        }

        if (sifreDiyaloguAcik) {
            SifreDiyalogu(
                durum = sifreDurumu,
                onKapat = onSifreDiyaloguKapat,
                onKaydet = onSifreDegistir,
            )
        }
    }
}

/**
 * Şifre değiştirme diyaloğu.
 *
 * ### Mevcut şifre neden soruluyor
 * Sunucu istemiyor — geçerli bir jeton yetiyor. Sormamak, salonun tezgâhında
 * açık unutulmuş bir telefonu eline alan birinin şifreyi değiştirip hesap
 * sahibini kilitlemesine izin vermek olurdu.
 *
 * ### Başarı neden diyaloğu kapatmıyor
 * Kapansaydı kullanıcı şifresinin gerçekten değişip değişmediğini bilemezdi ve
 * bu akışta bilememek pahalı: bir dahaki girişte hangi şifreyi yazacağını
 * bilmiyor demek. Diyalog "değişti" diyor, kapatmayı kullanıcı yapıyor.
 *
 * Kutular da o anda temizleniyor: açık kalan bir "yeni şifre" alanı, ekranı
 * bırakıp giden kullanıcının şifresini bir sonraki kişiye gösterirdi.
 */
@Composable
private fun SifreDiyalogu(
    durum: SifreDurumu,
    onKapat: () -> Unit,
    onKaydet: (mevcut: String, yeni: String, tekrar: String) -> Unit,
) {
    var mevcut by remember { mutableStateOf("") }
    var yeni by remember { mutableStateOf("") }
    var tekrar by remember { mutableStateOf("") }

    val basarili = durum is SifreDurumu.Basarili
    val gonderiliyor = durum is SifreDurumu.Gonderiliyor

    // Başarıdan sonra alanlar boşaltılıyor; şifre ekranda asılı kalmasın.
    LaunchedEffect(basarili) {
        if (basarili) {
            mevcut = ""
            yeni = ""
            tekrar = ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!gonderiliyor) onKapat() },
        title = { Text("Şifre Değiştir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (basarili) {
                    Text(
                        "Şifreniz değiştirildi. Bundan sonra yeni şifrenizle giriş yapın.",
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    SifreAlani("Mevcut şifre", mevcut, gonderiliyor) { mevcut = it }
                    SifreAlani("Yeni şifre", yeni, gonderiliyor) { yeni = it }
                    SifreAlani("Yeni şifre (tekrar)", tekrar, gonderiliyor) { tekrar = it }

                    Text(
                        "En az ${SifreKurali.EN_AZ_UZUNLUK} karakter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )

                    if (durum is SifreDurumu.Hata) {
                        Text(durum.mesaj, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (basarili) {
                TextButton(onClick = onKapat) { Text("Tamam") }
            } else {
                TextButton(
                    // Gönderim sırasında pasif: çift dokunma iki isteğe
                    // dönüşürdü ve ikincisi artık geçersiz olan eski şifreyle
                    // gider, "mevcut şifreniz yanlış" derdi.
                    enabled = !gonderiliyor,
                    onClick = { onKaydet(mevcut, yeni, tekrar) },
                ) { Text(if (gonderiliyor) "Gönderiliyor…" else "Değiştir") }
            }
        },
        dismissButton = {
            if (!basarili) {
                TextButton(enabled = !gonderiliyor, onClick = onKapat) { Text("Vazgeç") }
            }
        },
    )
}

@Composable
private fun SifreAlani(
    etiket: String,
    deger: String,
    pasif: Boolean,
    onDegisti: (String) -> Unit,
) {
    OutlinedTextField(
        value = deger,
        onValueChange = onDegisti,
        label = { Text(etiket) },
        singleLine = true,
        enabled = !pasif,
        // Şifre gizleniyor ve klavye öneri/otomatik tamamlama yapmıyor.
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AyarDiyalogu(
    baslik: String,
    onKapat: () -> Unit,
    icerik: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text(baslik) },
        text = { Column(content = icerik) },
        confirmButton = {},
    )
}

@Composable
private fun AyarSatiri(
    baslik: String,
    altBaslik: String,
    ikon: ImageVector,
    onTikla: () -> Unit,
) {
    OutlinedCard(
        onClick = onTikla,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                ikon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    baslik,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    altBaslik,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

/**
 * Senkronizasyon durumunun tek satırlık özeti.
 *
 * "Bekleyen: 0" ile "Bağlantı sorunu" arasındaki fark kullanıcı için önemli:
 * ilki her şeyin gittiğini, ikincisi verinin cihazda beklediğini söylüyor.
 * Tek bir "eşitleniyor" metni ikisini de gizlerdi.
 */
internal fun senkronizasyonOzeti(durum: SyncState, bekleyen: Int): String = when (durum) {
    is SyncState.Running -> "Eşitleniyor…"
    is SyncState.NoSession -> "Oturum yok"
    is SyncState.Problem -> "${durum.reason} Bekleyen: $bekleyen"
    is SyncState.Done -> buildString {
        append(if (bekleyen == 0) "Güncel" else "Bekleyen: $bekleyen")
        // İnen kayıt sayısı ayrıca yazılıyor: "hiçbir şey göndermedim ama on
        // satır indirdim" ile "hiçbir şey olmadı" kullanıcı için farklı.
        if (durum.pulled > 0) append(" · ${durum.pulled} kayıt indirildi")
    }
    SyncState.Idle ->
        if (bekleyen == 0) "Bekleyen değişiklik yok" else "Bekleyen: $bekleyen"
}
