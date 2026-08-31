package com.gymapp.arayuz.uyeler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.local.entity.MeasurementEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.local.entity.aktifKayitlar
import com.gymapp.domain.Decimals
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Membership
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentState
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.SessionQuota
import com.gymapp.domain.TarihBicimi
import com.gymapp.domain.labelTr

/** Sekme başlıkları; sıralama [UyeDetayEkrani]'ndaki `when` ile eşleşiyor. */
private val SEKMELER = listOf("Genel", "Sağlık", "Ölçümler", "Paketler")

/**
 * Üye detayı — `app`'teki `MemberDetailScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Neden diğerlerinden farklı bir taşımaydı
 * Dört sekmenin her biri `koinViewModel()` ile KENDİ ViewModel'ini
 * çekiyordu; yani ekranın içinde dört ayrı gizli bağımlılık vardı.
 * `koinViewModel()` Android'e özgü olduğu için bu hâliyle taşınamazdı.
 * Sekmeler artık verilerini parametre olarak alıyor ve ViewModel'i hiç
 * tanımıyor.
 *
 * ### Silme neden SONUCA bağlı
 * Önceden diyalog kapanıyor ve geri gezinme sonuç beklenmeden yapılıyordu:
 * silme başarısız olsa bile kullanıcı listeye dönüyor, üyeyi orada görüyor
 * ve neden hâlâ durduğunu anlayamıyordu. [siliniyor] açıkken onay düğmesi
 * pasif — çift dokunma iki silme denemesi başlatmasın.
 *
 * @param uye `null` ise üye henüz okunmadı; yükleme göstergesi çiziliyor
 * @param simdiMs üyelik durumunu türetmek için kullanılan an; ekran saati
 *   kendi okumuyor (bkz. [UyeListesiEkrani])
 * @param kalanBorc veritabanından okunuyor, `null` ise henüz gelmedi
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UyeDetayEkrani(
    uye: MemberEntity?,
    secilenSekme: Int,
    silmeOnayiAcik: Boolean,
    siliniyor: Boolean,
    simdiMs: Long,
    kalanBorc: Money?,
    olcumler: List<MeasurementEntity>,
    aktifPaket: PackageEntity?,
    hareketler: List<LedgerEntryEntity>,
    onGeri: () -> Unit,
    onSekmeSec: (Int) -> Unit,
    onSilIste: () -> Unit,
    onSilOnayla: (iptalEdilecekKayitlar: List<String>) -> Unit,
    onSilVazgec: () -> Unit,
    onTahsilat: (Money) -> Unit,
    onSaglikKaydet: (MemberEntity) -> Unit,
    onOlcumEkle: (
        boy: Double, kilo: Double, omuz: Double, gogus: Double,
        karin: Double, kalca: Double, bacak: Double, kol: Double, not: String,
    ) -> Unit,
    onOlcumSil: (String) -> Unit,
    snackbarDurumu: SnackbarHostState = remember { SnackbarHostState() },
) {
    if (silmeOnayiAcik) {
        UyeSilmeDiyalogu(
            ad = uye?.fullName ?: "Bu üye",
            silinebilirKayitlar = hareketler.aktifKayitlar(),
            siliniyor = siliniyor,
            onOnayla = onSilOnayla,
            onVazgec = onSilVazgec,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarDurumu) },
        topBar = {
            TopAppBar(
                title = { Text(uye?.fullName ?: "Üye Detayı") },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // Silme geri alınamaz bir işlem; tek dokunuşla tetiklenmemeli.
                    IconButton(onClick = onSilIste) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
                    }
                },
            )
        },
    ) { bosluk ->
        if (uye == null) {
            YukleniyorDurumu()
            return@Scaffold
        }

        Column(modifier = Modifier.padding(bosluk)) {
            TabRow(
                selectedTabIndex = secilenSekme,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                SEKMELER.forEachIndexed { sira, baslik ->
                    Tab(
                        selected = secilenSekme == sira,
                        onClick = { onSekmeSec(sira) },
                        text = { Text(baslik) },
                    )
                }
            }

            when (secilenSekme) {
                0 -> GenelBilgiSekmesi(uye, simdiMs, kalanBorc, onTahsilat)
                1 -> SaglikSekmesi(uye, onSaglikKaydet)
                2 -> OlcumSekmesi(olcumler, onOlcumEkle, onOlcumSil)
                3 -> PaketSekmesi(uye, aktifPaket, hareketler)
            }
        }
    }
}

@Composable
private fun GenelBilgiSekmesi(
    uye: MemberEntity,
    simdiMs: Long,
    kalanBorc: Money?,
    onTahsilat: (Money) -> Unit,
) {
    var tahsilatAcik by remember(uye.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetaySatiri("Telefon", PhoneNumber.formatForDisplay(uye.phone))
                uye.email?.let { DetaySatiri("E-posta", it) }
                DetaySatiri(
                    "Durum",
                    Membership.stateOf(uye.status, uye.endDateMs, simdiMs).labelTr(),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                DetaySatiri("Ödeme Durumu", uye.paymentStatus.labelTr())

                // Kalan borç ekranda. Önceden hiçbir yerde gösterilmiyordu;
                // oysa "Ödemeyi Onayla" tam olarak bu tutarı tahsil ediyordu.
                kalanBorc?.let { borc ->
                    if (borc.isPositive) DetaySatiri("Kalan Borç", "₺$borc")
                }

                if (tahsilatAcik) {
                    TahsilatDiyalogu(
                        uyeAdi = uye.fullName,
                        kalanBorc = kalanBorc ?: Money.ZERO,
                        onOnayla = { tutar ->
                            tahsilatAcik = false
                            onTahsilat(tutar)
                        },
                        onVazgec = { tahsilatAcik = false },
                    )
                }

                if (uye.paymentStatus == PaymentState.PENDING) {
                    Button(
                        // Borç okunana kadar pasif: tutarı bilmeden tahsilat
                        // diyaloğu açmak, düzeltilen hatanın aynısı olurdu.
                        enabled = kalanBorc?.isPositive == true,
                        onClick = { tahsilatAcik = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ödemeyi Onayla")
                    }
                }
            }
        }

        Text(
            "Üyelik Bilgileri",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetaySatiri("Başlangıç", uye.startDateMs?.let { TarihBicimi.gunAyYil(it) } ?: "-")
                DetaySatiri("Bitiş", uye.endDateMs?.let { TarihBicimi.gunAyYil(it) } ?: "-")
                DetaySatiri("Kalan Seans", uye.remainingSessions?.toString() ?: "Sınırsız")
            }
        }
    }
}

@Composable
private fun SaglikSekmesi(uye: MemberEntity, onKaydet: (MemberEntity) -> Unit) {
    var duzenleme by remember(uye.id) { mutableStateOf(false) }
    var riskler by remember(uye.id) { mutableStateOf(uye.healthRisks ?: "") }
    var notlar by remember(uye.id) { mutableStateOf(uye.healthNotes ?: "") }
    var seviye by remember(uye.id) { mutableStateOf(uye.riskLevel) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sağlık Profili",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = {
                    if (duzenleme) {
                        onKaydet(
                            uye.copy(
                                healthRisks = riskler,
                                healthNotes = notlar,
                                riskLevel = seviye,
                            ),
                        )
                    }
                    duzenleme = !duzenleme
                },
                colors = if (duzenleme) {
                    ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Icon(if (duzenleme) Icons.Default.Check else Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (duzenleme) "Kaydet" else "Düzenle")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (seviye) {
                    "HIGH" -> MaterialTheme.colorScheme.errorContainer
                    "MEDIUM" -> Color(0xFFFFEB3B).copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Risk Seviyesi: $seviye",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (duzenleme) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH").forEach { secenek ->
                            FilterChip(
                                selected = seviye == secenek,
                                onClick = { seviye = secenek },
                                label = { Text(secenek) },
                            )
                        }
                    }
                }
            }
        }

        if (duzenleme) {
            OutlinedTextField(
                value = riskler,
                onValueChange = { riskler = it },
                label = { Text("Kronik Rahatsızlıklar / Riskler") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = notlar,
                onValueChange = { notlar = it },
                label = { Text("Sağlık Notları") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        } else {
            Text("Kronik Rahatsızlıklar", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(uye.healthRisks ?: "Belirtilmemiş", style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(8.dp))

            Text("Sağlık Notları", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(uye.healthNotes ?: "Not yok", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun OlcumSekmesi(
    olcumler: List<MeasurementEntity>,
    onEkle: (
        boy: Double, kilo: Double, omuz: Double, gogus: Double,
        karin: Double, kalca: Double, bacak: Double, kol: Double, not: String,
    ) -> Unit,
    onSil: (String) -> Unit,
) {
    var eklemeAcik by remember { mutableStateOf(false) }
    // Silinecek ölçüm; onay istemeden silmiyoruz çünkü geri alma yolu yok.
    var silinecek by remember { mutableStateOf<MeasurementEntity?>(null) }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Son Ölçümler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = { eklemeAcik = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ölçüm Ekle", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (olcumler.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Henüz ölçüm kaydı yok.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(olcumler) { olcum ->
                    OlcumGecmisiSatiri(olcum = olcum, onSil = { silinecek = olcum })
                }
            }
        }
    }

    if (eklemeAcik) {
        OlcumEklemeDiyalogu(
            onKapat = { eklemeAcik = false },
            onKaydet = { boy, kilo, omuz, gogus, karin, kalca, bacak, kol, not ->
                onEkle(boy, kilo, omuz, gogus, karin, kalca, bacak, kol, not)
                eklemeAcik = false
            },
        )
    }

    silinecek?.let { olcum ->
        AlertDialog(
            onDismissRequest = { silinecek = null },
            title = { Text("Ölçümü sil") },
            text = {
                Text(
                    "${TarihBicimi.gunAyAdiYil(olcum.dateMs)} tarihli ölçüm silinecek. " +
                        "Bu işlem geri alınamaz.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSil(olcum.id)
                    silinecek = null
                }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { silinecek = null }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun OlcumGecmisiSatiri(olcum: MeasurementEntity, onSil: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    TarihBicimi.gunAyAdiYil(olcum.dateMs),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${olcum.weight} kg / ${olcum.height} cm",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onSil, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Ölçümü sil",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OlcumDegeri("Omuz", "${olcum.shoulder} cm", Modifier.weight(1f))
                    OlcumDegeri("Göğüs", "${olcum.chest} cm", Modifier.weight(1f))
                    OlcumDegeri("Karın", "${olcum.waist} cm", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    OlcumDegeri("Kalça", "${olcum.hips} cm", Modifier.weight(1f))
                    OlcumDegeri("Bacak", "${olcum.leg} cm", Modifier.weight(1f))
                    OlcumDegeri("Kol", "${olcum.arm} cm", Modifier.weight(1f))
                }
            }

            if (olcum.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Not: ${olcum.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun OlcumDegeri(etiket: String, deger: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(etiket, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(deger, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PaketSekmesi(
    uye: MemberEntity,
    aktifPaket: PackageEntity?,
    hareketler: List<LedgerEntryEntity>,
) {
    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (aktifPaket != null) {
            Text("Aktif Paket", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            aktifPaket.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Sınırsızlık `null` seans kotasından anlaşılıyor; önceden
                    // `-1` sentinel'i ekranda "-1 Seans" olarak sızabiliyordu.
                    val sinirsiz = SessionQuota.isUnlimited(aktifPaket.sessionCount)
                    DetaySatiri("Paket Türü", if (sinirsiz) "Abonman" else "Ders Paketi")
                    DetaySatiri("Bitiş Tarihi", uye.endDateMs?.let { TarihBicimi.gunAyYil(it) } ?: "-")
                    DetaySatiri("Kalan Hak", uye.remainingSessions?.let { "$it Seans" } ?: "Sınırsız")
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Aktif paket bulunamadı.", color = Color.Gray)
                }
            }
        }

        HorizontalDivider()

        // İşlem geçmişi gerçek: önceden burada sabit bir "Henüz geçmiş işlem
        // bulunmuyor." metni vardı ve üyenin onlarca tahsilatı olsa bile aynı
        // şeyi yazıyordu. Veri katmanı (defter) baştan beri hazırdı, yalnızca
        // çağıran ekran yoktu.
        Text("İşlem Geçmişi", style = MaterialTheme.typography.titleSmall, color = Color.Gray)

        if (hareketler.isEmpty()) {
            Text(
                "Henüz işlem kaydı yok.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        } else {
            hareketler.forEach { hareket ->
                DefterSatiri(hareket, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Üye silme onayı — finans kayıtlarının ne olacağı da burada soruluyor.
 *
 * ### Neden bir soru
 * Silme defterine hiç dokunmuyordu ve bu **görünmez** bir hataydı: yanlışlıkla
 * kaydedilen üye siliniyor, ondan doğan tahsilatlar finansta duruyor, salon o
 * parayı almış görünüyordu. Kayıtları koşulsuz iptal etmek ise ters yönde aynı
 * ağırlıkta olurdu — gerçekten ödeme yapmış bir üyenin kaydı silindiğinde ciro
 * sessizce düşerdi. İki durum da doğru, ayırt edebilecek tek şey kullanıcı.
 *
 * ### Neden hepsi baştan işaretli
 * Bu diyaloğun asıl geldiği yer hatalı kayıt: kullanıcı üyeyi zaten "bu kayıt
 * yanlıştı" diye siliyor ve kayıtların da gitmesini bekliyor. Boş bir liste
 * sunmak, düzeltmeyi yapmayı unutmayı kolaylaştırırdı ve unutulan hâli —
 * hayalet gelir — tam olarak düzeltilen hata. Ters yöndeki risk daha ucuz:
 * fazladan iptal edilen kayıt finansta rozetiyle **görünüyor** ve yeniden
 * girilebiliyor, oysa hiç iptal edilmeyen kayıt hiçbir yerde uyarı üretmiyor.
 *
 * Yine de tek dokunuşla değil: liste tutarlarıyla birlikte önde ve onay
 * düğmesi kaç kaydın iptal edileceğini yazıyor.
 *
 * @param silinebilirKayitlar yalnızca yaşayan kayıtlar; iptal edilmiş olanlar
 *   tekrar iptal edilemez ve listede hiç görünmüyor
 */
@Composable
private fun UyeSilmeDiyalogu(
    ad: String,
    silinebilirKayitlar: List<LedgerEntryEntity>,
    siliniyor: Boolean,
    onOnayla: (iptalEdilecekKayitlar: List<String>) -> Unit,
    onVazgec: () -> Unit,
) {
    // Anahtarsız `remember`: diyalog açıkken senkronizasyon liste değiştirirse
    // kullanıcının işaretlemesi korunuyor. İki uç durum da güvenli tarafa
    // düşüyor — aradan çıkan kimlik onaylarken güncel listeye göre süzülüyor,
    // sonradan gelen kayıt ise işaretsiz geliyor (yani kimsenin seçmediği bir
    // kayıt kendiliğinden iptal edilmiyor).
    var secilenler by remember { mutableStateOf(silinebilirKayitlar.map { it.id }.toSet()) }

    val hepsiSecili = silinebilirKayitlar.isNotEmpty() &&
        silinebilirKayitlar.all { it.id in secilenler }
    val secilenSayisi = silinebilirKayitlar.count { it.id in secilenler }

    AlertDialog(
        onDismissRequest = onVazgec,
        title = { Text("Üyeyi sil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$ad listeden kaldırılacak.")

                if (silinebilirKayitlar.isEmpty()) {
                    Text(
                        "Bu üyenin finans kaydı yok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                } else {
                    HorizontalDivider()
                    Text(
                        "Finans kayıtları duruyor. İptal edilecekleri seçin:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = hepsiSecili,
                            onCheckedChange = {
                                secilenler = if (hepsiSecili) {
                                    emptySet()
                                } else {
                                    silinebilirKayitlar.map { k -> k.id }.toSet()
                                }
                            },
                        )
                        Text(
                            if (hepsiSecili) "Hiçbirini iptal etme" else "Tümünü seç",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // Sınırlı yükseklik + kendi kaydırması: onlarca kaydı olan
                    // üyede liste diyaloğu taşırır ve düğmeler erişilemez
                    // hâle gelirdi.
                    //
                    // `LazyColumn` DEĞİL: diyaloğun metin bölmesi kendi
                    // yüksekliğini içeriğinden alıyor ve tembel liste orada
                    // sınırsız yükseklikle ölçülüp çalışma zamanında düşerdi.
                    // Bir üyenin defter satırı zaten az; tembelliğin kazancı yok.
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        silinebilirKayitlar.forEach { kayit ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = kayit.id in secilenler,
                                    onCheckedChange = {
                                        secilenler = if (kayit.id in secilenler) {
                                            secilenler - kayit.id
                                        } else {
                                            secilenler + kayit.id
                                        }
                                    },
                                )
                                DefterSatiri(kayit, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Text(
                        "İptal edilen kayıt silinmez: aynı tutarda ters kayıt yazılır, " +
                            "ikisi de finansta kalır ve toplamlarda birbirini götürür. " +
                            "Seçilmeyenler olduğu gibi durur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                // Çift dokunma iki silme denemesi başlatmasın.
                enabled = !siliniyor,
                onClick = {
                    // Güncel listeye göre süzülüyor: ekranda artık olmayan bir
                    // kimlik gönderilmiyor.
                    onOnayla(silinebilirKayitlar.map { it.id }.filter { it in secilenler })
                },
            ) {
                Text(
                    if (secilenSayisi == 0) "Sil" else "Sil ve $secilenSayisi kaydı iptal et",
                    color = Color.Red,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onVazgec) { Text("Vazgeç") }
        },
    )
}

/**
 * Defter satırı: tahsilat yeşil ve `+`, tahakkuk kırmızı ve `−`.
 *
 * İşaret ve renk birlikte veriliyor; yalnızca renge dayanmak, renk körlüğünde
 * borçla tahsilatı ayırt edilemez hâle getirirdi.
 */
@Composable
private fun DefterSatiri(kayit: LedgerEntryEntity, modifier: Modifier = Modifier) {
    val tahsilat = kayit.type == LedgerType.PAYMENT
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(kayit.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                TarihBicimi.gunAyYil(kayit.occurredAtMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
        Text(
            text = "${if (tahsilat) "+" else "−"}₺${Money(kayit.amountMinor)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (tahsilat) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun YukleniyorDurumu() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DetaySatiri(etiket: String, deger: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiket, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(deger, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OlcumEklemeDiyalogu(
    onKapat: () -> Unit,
    /** (boy, kilo, omuz, göğüs, karın, kalça, bacak, kol, not) */
    onKaydet: (Double, Double, Double, Double, Double, Double, Double, Double, String) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var shoulder by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hips by remember { mutableStateOf("") }
    var leg by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text("Yeni Ölçüm Kaydı") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Kilo (kg)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Boy (cm)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = shoulder, onValueChange = { shoulder = it }, label = { Text("Omuz") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = chest, onValueChange = { chest = it }, label = { Text("Göğüs") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = waist, onValueChange = { waist = it }, label = { Text("Karın") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = hips, onValueChange = { hips = it }, label = { Text("Kalça") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = leg, onValueChange = { leg = it }, label = { Text("Bacak") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = arm, onValueChange = { arm = it }, label = { Text("Kol") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notlar") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onKaydet(
                    Decimals.parseOrDefault(height),
                    Decimals.parseOrDefault(weight),
                    Decimals.parseOrDefault(shoulder),
                    Decimals.parseOrDefault(chest),
                    Decimals.parseOrDefault(waist),
                    Decimals.parseOrDefault(hips),
                    Decimals.parseOrDefault(leg),
                    Decimals.parseOrDefault(arm),
                    notes,
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onKapat) { Text("İptal") } }
    )
}
