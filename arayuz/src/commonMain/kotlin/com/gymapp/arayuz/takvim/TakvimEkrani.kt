package com.gymapp.arayuz.takvim

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.TarihBicimi
import com.gymapp.domain.TrainingType
import com.gymapp.domain.labelTr

/** Salonun çalışma saatleri; takvim yalnızca bu aralığı çiziyor. */
private val CALISMA_SAATLERI = 9..21

/**
 * Randevu takvimi — `app`'teki `CalendarScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Taşınırken değişmek zorunda olan üç şey
 * Bu ekran `app` içindeki en Android'e bağlı ekrandı:
 *
 *  1. `DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("tr"))` — JVM'e
 *     özgü. Yerini `TarihBicimi.gunAyAdiYil` aldı; ay adları zaten orada
 *     açıkça yazılı olduğu için çıktı cihaz diline göre değişmiyor.
 *  2. `Instant.ofEpochMilli(...).atZone(ZoneId.systemDefault()).hour` —
 *     JVM'e özgü. Yerini `TarihBicimi.saatSayisi` aldı.
 *  3. `java.time.LocalDate` — ekranın gün aritmetiği yaptığı yer. Artık
 *     yapmıyor: seçili gün [secilenGunMs] olarak geliyor, ileri/geri/bugün
 *     üç ayrı geri çağrı. Gün eklemek göründüğü kadar basit değil (yaz
 *     saati geçişlerinde bir gün 24 saat değil) ve o hesap ekranın işi
 *     değil.
 *
 * ### Sheet ve diyalog neden dışarıdan sürülüyor
 * Randevu ekleme sheet'i kayıt **başarılı olduğunda** kapanıyor, reddedildiğinde
 * (çakışma, seans hakkı yok) açık kalıyor — kullanıcı formunu kaybetmesin
 * diye. Bu kararı ekran veremez: sonucu yalnızca çağıran biliyor. Aynısı
 * durum diyaloğu için de geçerli.
 *
 * Sheet'in İÇİNDEKİ form durumu (seçili üye, eğitmen, saat, tür) ekranda
 * kalıyor — o yazma durumu, uygulamanın verisi değil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakvimEkrani(
    secilenGunMs: Long,
    randevular: List<AppointmentEntity>,
    uyeler: List<MemberEntity>,
    personeller: List<StaffEntity>,
    randevuEklemeAcik: Boolean,
    secilenRandevu: AppointmentEntity?,
    onGeri: () -> Unit,
    onOncekiGun: () -> Unit,
    onSonrakiGun: () -> Unit,
    onBugun: () -> Unit,
    onRandevuEklemeAc: () -> Unit,
    onRandevuEklemeKapat: () -> Unit,
    onRandevuSec: (AppointmentEntity?) -> Unit,
    onRandevuEkle: (uyeId: String, personelId: String, saat: Int, tur: TrainingType) -> Unit,
    onDurumGuncelle: (randevuId: String, durum: AppointmentState, not: String) -> Unit,
    snackbarDurumu: SnackbarHostState = remember { SnackbarHostState() },
) {
    val sheetDurumu = rememberModalBottomSheetState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarDurumu) },
        topBar = {
            TopAppBar(
                title = { Text("Randevu Takvimi") },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = onBugun) {
                        Text(
                            "Bugün",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRandevuEklemeAc,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Randevu Ekle") },
            )
        },
    ) { bosluk ->
        Column(modifier = Modifier.padding(bosluk)) {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOncekiGun) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki gün")
                    }
                    Text(
                        text = TarihBicimi.gunAyAdiYil(secilenGunMs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onSonrakiGun) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki gün")
                    }
                }
            }

            // Randevular saat satırlarına bir kez dağıtılıyor. Önceden her
            // saat için tüm liste yeniden süzülüyordu (13 saat x N randevu).
            val saatlik = randevular.groupBy { TarihBicimi.saatSayisi(it.startTimeMs) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(CALISMA_SAATLERI.toList()) { saat ->
                    SaatSatiri(
                        saat = saat,
                        randevular = saatlik[saat].orEmpty(),
                        uyeler = uyeler,
                        personeller = personeller,
                        onTikla = onRandevuSec,
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        if (randevuEklemeAcik) {
            RandevuEklemeSheet(
                uyeler = uyeler,
                personeller = personeller,
                sheetDurumu = sheetDurumu,
                onKapat = onRandevuEklemeKapat,
                // Sheet burada kapatılmıyor: kayıt reddedilirse (çakışma,
                // seans hakkı yok) kullanıcı formunu kaybetmesin diye açık
                // kalıyor. Kapatma kararı çağırana ait.
                onKaydet = onRandevuEkle,
            )
        }

        secilenRandevu?.let { randevu ->
            RandevuDurumuDiyalogu(
                randevu = randevu,
                uye = uyeler.find { it.id == randevu.memberId },
                onKapat = { onRandevuSec(null) },
                onOnayla = { durum, not -> onDurumGuncelle(randevu.id, durum, not) },
            )
        }
    }
}

@Composable
private fun SaatSatiri(
    saat: Int,
    randevular: List<AppointmentEntity>,
    uyeler: List<MemberEntity>,
    personeller: List<StaffEntity>,
    onTikla: (AppointmentEntity) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "${ikiHane(saat)}:00",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(60.dp).padding(top = 12.dp),
            color = Color.Gray,
        )

        Column(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (randevular.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "Müsait",
                        modifier = Modifier.padding(start = 12.dp),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                randevular.forEach { randevu ->
                    RandevuKarti(
                        randevu = randevu,
                        uyeAdi = uyeler.find { it.id == randevu.memberId }?.fullName
                            ?: "Bilinmeyen Üye",
                        personelAdi = personeller.find { it.id == randevu.staffId }?.fullName
                            ?: "Bilinmeyen Eğitmen",
                        onTikla = { onTikla(randevu) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RandevuKarti(
    randevu: AppointmentEntity,
    uyeAdi: String,
    personelAdi: String,
    onTikla: () -> Unit,
) {
    val renk = durumRengi(randevu.state)

    Card(
        onClick = onTikla,
        colors = CardDefaults.cardColors(containerColor = renk.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(uyeAdi, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${randevu.trainingType.labelTr()} - $personelAdi",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                )
            }

            if (randevu.state != AppointmentState.SCHEDULED) {
                Text(
                    text = durumEtiketi(randevu.state),
                    style = MaterialTheme.typography.labelSmall,
                    color = renk,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RandevuDurumuDiyalogu(
    randevu: AppointmentEntity,
    uye: MemberEntity?,
    onKapat: () -> Unit,
    onOnayla: (AppointmentState, String) -> Unit,
) {
    var not by remember(randevu.id) { mutableStateOf(randevu.notes ?: "") }

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text(uye?.fullName ?: "Randevu Durumu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = not,
                    onValueChange = { not = it },
                    label = { Text("Antrenman Notları") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )

                Text("Durum Güncelle:", style = MaterialTheme.typography.labelLarge)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onOnayla(AppointmentState.COMPLETED, not) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = durumRengi(AppointmentState.COMPLETED),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Tamamlandı", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { onOnayla(AppointmentState.POSTPONED, not) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = durumRengi(AppointmentState.POSTPONED),
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Ertelendi", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = { onOnayla(AppointmentState.CANCELLED, not) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = durumRengi(AppointmentState.CANCELLED),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("İptal Edildi")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onKapat) { Text("Kapat") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RandevuEklemeSheet(
    uyeler: List<MemberEntity>,
    personeller: List<StaffEntity>,
    sheetDurumu: SheetState,
    onKapat: () -> Unit,
    onKaydet: (uyeId: String, personelId: String, saat: Int, tur: TrainingType) -> Unit,
) {
    var uyeId by remember { mutableStateOf<String?>(null) }
    var personelId by remember { mutableStateOf<String?>(null) }
    var saat by remember { mutableIntStateOf(CALISMA_SAATLERI.first) }
    var tur by remember { mutableStateOf(TrainingType.FITNESS) }

    ModalBottomSheet(onDismissRequest = onKapat, sheetState = sheetDurumu) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
            Text(
                "Yeni Randevu",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            Text("Ders Türü", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainingType.entries.forEach { secenek ->
                    FilterChip(
                        selected = tur == secenek,
                        onClick = { tur = secenek },
                        label = { Text(secenek.labelTr()) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Üye Seçin", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.height(120.dp)) {
                items(uyeler) { uye ->
                    ListItem(
                        headlineContent = { Text(uye.fullName) },
                        leadingContent = {
                            RadioButton(selected = uyeId == uye.id, onClick = { uyeId = uye.id })
                        },
                        modifier = Modifier.clickable { uyeId = uye.id },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Eğitmen Seçin", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.height(120.dp)) {
                items(personeller) { personel ->
                    ListItem(
                        headlineContent = { Text(personel.fullName) },
                        supportingContent = { Text(personel.branch) },
                        leadingContent = {
                            RadioButton(
                                selected = personelId == personel.id,
                                onClick = { personelId = personel.id },
                            )
                        },
                        modifier = Modifier.clickable { personelId = personel.id },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Saat Seçin", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CALISMA_SAATLERI.forEach { secenek ->
                    FilterChip(
                        selected = saat == secenek,
                        onClick = { saat = secenek },
                        label = { Text("${ikiHane(secenek)}:00") },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            val secildi = uyeId != null && personelId != null
            Button(
                onClick = {
                    val u = uyeId
                    val p = personelId
                    if (u != null && p != null) onKaydet(u, p, saat, tur)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = secildi,
            ) {
                Text("Randevuyu Kaydet")
            }
        }
    }
}

/**
 * Randevu durumunun rengi.
 *
 * Renkler iki yerde (kart zemini ve durum yazısı) ve diyalogdaki düğmelerde
 * kullanılıyordu; üç kopya vardı ve biri değişirse diğerleri sessizce
 * ayrışırdı.
 */
@Composable
private fun durumRengi(durum: AppointmentState): Color = when (durum) {
    AppointmentState.COMPLETED -> Color(0xFF4CAF50)
    AppointmentState.CANCELLED -> Color(0xFFF44336)
    AppointmentState.POSTPONED -> Color(0xFFFF9800)
    AppointmentState.NO_SHOW -> Color(0xFF9E9E9E)
    // Planlanmış randevu tema rengini kullanıyor; diğer dört durum anlamı
    // renkte taşıdığı için sabit. `@Composable` olmasının tek sebebi bu dal.
    AppointmentState.SCHEDULED -> MaterialTheme.colorScheme.primary
}

private fun durumEtiketi(durum: AppointmentState): String = when (durum) {
    AppointmentState.COMPLETED -> "Tamamlandı"
    AppointmentState.CANCELLED -> "İptal"
    AppointmentState.POSTPONED -> "Ertelendi"
    AppointmentState.NO_SHOW -> "Gelmedi"
    AppointmentState.SCHEDULED -> ""
}

private fun ikiHane(deger: Int): String = if (deger < 10) "0$deger" else deger.toString()
