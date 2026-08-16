package com.gymapp.presentation.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.domain.PaymentState
import com.gymapp.domain.Decimals
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Membership
import com.gymapp.domain.Money
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.SessionQuota
import com.gymapp.domain.labelTr
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    memberId: String,
    onNavigateBack: () -> Unit,
    viewModel: MemberViewModel = koinViewModel()
) {
    val member by viewModel.getMemberById(memberId).collectAsState(initial = null)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var siliniyor by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val tabs = listOf("Genel", "Sağlık", "Ölçümler", "Paketler")

    // Ekrandan çıkmak SONUCA bağlı.
    //
    // Önceden diyalog kapanıyor ve `onNavigateBack()` sonuç beklenmeden
    // çağrılıyordu: silme başarısız olsa bile kullanıcı listeye dönüyor, üyeyi
    // orada görüyor ve neden hâlâ durduğunu anlayamıyordu. Daha kötüsü, silme
    // fırlattığında uygulama kapanıyordu.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MemberEvent.Deleted -> onNavigateBack()
                is MemberEvent.Saved -> snackbarHostState.showSnackbar(event.message)
                is MemberEvent.Failed -> {
                    siliniyor = false
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Üyeyi sil") },
            text = {
                Text("${member?.fullName ?: "Bu üye"} listeden kaldırılacak. Devam edilsin mi?")
            },
            confirmButton = {
                TextButton(
                    enabled = !siliniyor, // çift dokunma iki silme denemesi başlatmasın
                    onClick = {
                        siliniyor = true
                        showDeleteConfirm = false
                        viewModel.deleteMember(memberId)
                    },
                ) { Text("Sil", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Vazgeç") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(member?.fullName ?: "Üye Detayı") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // Silme geri alınamaz bir işlem; tek dokunuşla tetiklenmemeli.
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
                    }
                }
            )
        }
    ) { padding ->
        member?.let { m ->
            Column(modifier = Modifier.padding(padding)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> GeneralInfoTab(m, viewModel)
                    1 -> HealthProfileTab(m, viewModel)
                    2 -> MeasurementsTab(m, viewModel)
                    3 -> PackagesTab(m, viewModel)
                }
            }
        } ?: LoadingState()
    }
}

@Composable
fun GeneralInfoTab(member: MemberEntity, viewModel: MemberViewModel = koinViewModel()) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(label = "Telefon", value = PhoneNumber.formatForDisplay(member.phone))
                member.email?.let { DetailRow(label = "E-posta", value = it) }
                DetailRow(
                    label = "Durum",
                    value = Membership.stateOf(
                        member.status,
                        member.endDateMs,
                        System.currentTimeMillis()
                    ).labelTr()
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                DetailRow(label = "Ödeme Durumu", value = member.paymentStatus.labelTr())

                // Kalan borç artık ekranda. Önceden hiçbir yerde
                // gösterilmiyordu; oysa "Ödemeyi Onayla" tam olarak bu tutarı
                // tahsil ediyordu.
                var kalanBorc by remember(member.id) { mutableStateOf<Money?>(null) }
                LaunchedEffect(member.id, member.paymentStatus) {
                    kalanBorc = viewModel.outstandingBalance(member.id)
                }
                kalanBorc?.let { borc ->
                    if (borc.isPositive) DetailRow(label = "Kalan Borç", value = "₺$borc")
                }

                var tahsilatAcik by remember { mutableStateOf(false) }
                if (tahsilatAcik) {
                    PaymentDialog(
                        memberName = member.fullName,
                        outstanding = kalanBorc ?: Money.ZERO,
                        onConfirm = { tutar ->
                            tahsilatAcik = false
                            viewModel.markAsPaid(member.id, tutar)
                        },
                        onDismiss = { tahsilatAcik = false },
                    )
                }

                if (member.paymentStatus == PaymentState.PENDING) {
                    Button(
                        // Borç okunana kadar pasif: tutarı bilmeden tahsilat
                        // diyaloğu açmak, düzeltilen hatanın aynısı olurdu.
                        enabled = kalanBorc?.isPositive == true,
                        onClick = { tahsilatAcik = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ödemeyi Onayla")
                    }
                }
            }
        }
        
        Text("Üyelik Bilgileri", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(label = "Başlangıç", value = member.startDateMs?.let { dateFormat.format(Date(it)) } ?: "-")
                DetailRow(label = "Bitiş", value = member.endDateMs?.let { dateFormat.format(Date(it)) } ?: "-")
                DetailRow(label = "Kalan Seans", value = member.remainingSessions?.toString() ?: "Sınırsız")
            }
        }
    }
}

@Composable
fun HealthProfileTab(member: MemberEntity, viewModel: MemberViewModel = koinViewModel()) {
    var isEditing by remember { mutableStateOf(false) }
    var healthRisks by remember { mutableStateOf(member.healthRisks ?: "") }
    var healthNotes by remember { mutableStateOf(member.healthNotes ?: "") }
    var riskLevel by remember { mutableStateOf(member.riskLevel) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sağlık Profili", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    if (isEditing) {
                        viewModel.updateMember(
                            member.copy(
                                healthRisks = healthRisks,
                                healthNotes = healthNotes,
                                riskLevel = riskLevel
                            )
                        )
                    }
                    isEditing = !isEditing
                },
                colors = if (isEditing) ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) else ButtonDefaults.buttonColors()
            ) {
                Icon(if (isEditing) Icons.Default.Check else Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isEditing) "Kaydet" else "Düzenle")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when(riskLevel) {
                    "HIGH" -> MaterialTheme.colorScheme.errorContainer
                    "MEDIUM" -> Color(0xFFFFEB3B).copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Risk Seviyesi: $riskLevel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (isEditing) {
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LOW", "MEDIUM", "HIGH").forEach { level ->
                            FilterChip(
                                selected = riskLevel == level,
                                onClick = { riskLevel = level },
                                label = { Text(level) }
                            )
                        }
                    }
                }
            }
        }
        
        if (isEditing) {
            OutlinedTextField(
                value = healthRisks,
                onValueChange = { healthRisks = it },
                label = { Text("Kronik Rahatsızlıklar / Riskler") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = healthNotes,
                onValueChange = { healthNotes = it },
                label = { Text("Sağlık Notları") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        } else {
            Text("Kronik Rahatsızlıklar", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(text = member.healthRisks ?: "Belirtilmemiş", style = MaterialTheme.typography.bodyLarge)
            
            Spacer(Modifier.height(8.dp))
            
            Text("Sağlık Notları", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(text = member.healthNotes ?: "Not yok", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun MeasurementsTab(member: MemberEntity, viewModel: MemberViewModel = koinViewModel()) {
    val measurements by viewModel.getMeasurements(member.id).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Son Ölçümler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddDialog = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ölçüm Ekle", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (measurements.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Henüz ölçüm kaydı yok.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(measurements) { measurement ->
                    MeasurementHistoryItem(measurement)
                }
            }
        }
    }

    if (showAddDialog) {
        AddMeasurementDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { height, weight, shoulder, chest, waist, hips, leg, arm, notes ->
                viewModel.addMeasurement(
                    memberId = member.id,
                    height = height,
                    weight = weight,
                    shoulder = shoulder,
                    chest = chest,
                    waist = waist,
                    hips = hips,
                    leg = leg,
                    arm = arm,
                    notes = notes,
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MeasurementHistoryItem(measurement: com.gymapp.data.local.entity.MeasurementEntity) {
    val sdf = remember { SimpleDateFormat("dd MMMM yyyy", Locale("tr")) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sdf.format(Date(measurement.dateMs)), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("${measurement.weight} kg / ${measurement.height} cm", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))
            
            // Grid layout for measurements
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MeasurementLabelValue("Omuz", "${measurement.shoulder} cm", Modifier.weight(1f))
                    MeasurementLabelValue("Göğüs", "${measurement.chest} cm", Modifier.weight(1f))
                    MeasurementLabelValue("Karın", "${measurement.waist} cm", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    MeasurementLabelValue("Kalça", "${measurement.hips} cm", Modifier.weight(1f))
                    MeasurementLabelValue("Bacak", "${measurement.leg} cm", Modifier.weight(1f))
                    MeasurementLabelValue("Kol", "${measurement.arm} cm", Modifier.weight(1f))
                }
            }
            
            if (measurement.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Not: ${measurement.notes}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun MeasurementLabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementDialog(
    onDismiss: () -> Unit,
    /** (boy, kilo, omuz, göğüs, karın, kalça, bacak, kol, not) */
    onConfirm: (Double, Double, Double, Double, Double, Double, Double, Double, String) -> Unit
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
        onDismissRequest = onDismiss,
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
                onConfirm(
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun PackagesTab(member: MemberEntity, viewModel: MemberViewModel = koinViewModel()) {
    val packages by viewModel.packages.collectAsState()
    val activePackage = packages.find { it.id == member.activePackageId }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (activePackage != null) {
            Text("Aktif Paket", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(activePackage.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    // Sınırsızlık artık `null` seans kotasından anlaşılıyor; önceden
                    // `-1` sentinel'i ekranda "-1 Seans" olarak sızabiliyordu.
                    val unlimited = SessionQuota.isUnlimited(activePackage.sessionCount)
                    DetailRow(label = "Paket Türü", value = if (unlimited) "Abonman" else "Ders Paketi")
                    DetailRow(label = "Bitiş Tarihi", value = member.endDateMs?.let { dateFormat.format(Date(it)) } ?: "-")
                    DetailRow(
                        label = "Kalan Hak",
                        value = member.remainingSessions?.let { "$it Seans" } ?: "Sınırsız"
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text("Aktif paket bulunamadı.", color = Color.Gray)
                }
            }
        }
        
        HorizontalDivider()

        // İşlem geçmişi artık gerçek: önceden burada sabit bir
        // "Henüz geçmiş işlem bulunmuyor." metni vardı ve üyenin onlarca
        // tahsilatı olsa bile aynı şeyi yazıyordu. Veri katmanı (defter)
        // baştan beri hazırdı, yalnızca çağıran ekran yoktu.
        Text("İşlem Geçmişi", style = MaterialTheme.typography.titleSmall, color = Color.Gray)

        val hareketler by viewModel.getLedgerForMember(member.id)
            .collectAsState(initial = emptyList())

        if (hareketler.isEmpty()) {
            Text(
                "Henüz işlem kaydı yok.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        } else {
            hareketler.forEach { hareket ->
                LedgerRow(entry = hareket, dateFormat = dateFormat)
            }
        }
    }
}

/**
 * Defter satırı: tahsilat yeşil ve `+`, tahakkuk kırmızı ve `−`.
 *
 * İşaret ve renk birlikte veriliyor; yalnızca renge dayanmak, renk körlüğünde
 * borçla tahsilatı ayırt edilemez hâle getirirdi.
 */
@Composable
private fun LedgerRow(
    entry: com.gymapp.data.local.entity.LedgerEntryEntity,
    dateFormat: SimpleDateFormat,
) {
    val tahsilat = entry.type == LedgerType.PAYMENT
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                dateFormat.format(Date(entry.occurredAtMs)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
        Text(
            text = "${if (tahsilat) "+" else "−"}₺${Money(entry.amountMinor)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (tahsilat) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun MeasurementCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
