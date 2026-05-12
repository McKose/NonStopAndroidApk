package com.gymapp.presentation.members

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.MeasurementEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.MemberPackageEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    memberId: Long,
    onNavigateBack: () -> Unit,
    onAddMeasurement: () -> Unit = {},
    onEditMeasurement: (Long) -> Unit = {},
    onOpenPosture: () -> Unit = {},
    viewModel: MemberViewModel = hiltViewModel()
) {
    val member by viewModel.getMemberById(memberId).collectAsState(initial = null)
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Genel", "Sağlık", "Ölçümler", "Paketler", "Postür")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(member?.fullName ?: "Üye Detayı") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteMember(memberId)
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
                    }
                }
            )
        }
    ) { padding ->
        member?.let { m ->
            Column(modifier = Modifier.padding(padding)) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 8.dp
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
                    2 -> MeasurementsTab(m, viewModel, onAddMeasurement, onEditMeasurement)
                    3 -> PackagesTab(m, viewModel)
                    4 -> PostureTab(m, viewModel, onOpenPosture)
                }
            }
        } ?: LoadingState()
    }
}

// ─── GENEL ───────────────────────────────────────────────────────────────────

@Composable
fun GeneralInfoTab(member: MemberEntity, viewModel: MemberViewModel = hiltViewModel()) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(label = "Telefon", value = member.phone)
                member.email?.let { DetailRow(label = "E-posta", value = it) }
                DetailRow(label = "Durum", value = member.status)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                DetailRow(label = "Ödeme Durumu", value = if (member.paymentStatus == "PAID") "Ödendi" else "Ödeme Bekliyor")
                if (member.paymentStatus == "PENDING") {
                    Button(
                        onClick = { viewModel.markAsPaid(member.id) },
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
                DetailRow(label = "Kalan Seans", value = if (member.remainingSessions == -1) "Sınırsız" else member.remainingSessions.toString())
            }
        }
    }
}

// ─── SAĞLIK ──────────────────────────────────────────────────────────────────

@Composable
fun HealthProfileTab(member: MemberEntity, viewModel: MemberViewModel = hiltViewModel()) {
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
                containerColor = when (riskLevel) {
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

// ─── ÖLÇÜMLER ────────────────────────────────────────────────────────────────

@Composable
fun MeasurementsTab(
    member: MemberEntity,
    viewModel: MemberViewModel,
    onAddMeasurement: () -> Unit,
    onEditMeasurement: (Long) -> Unit
) {
    val measurements by viewModel.getMeasurements(member.id).collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ölçümler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = onAddMeasurement,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ekle", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Kilo trend grafiği — son 30 kayıt
        val weightSeries = remember(measurements) {
            measurements.filter { it.weight > 0 }.sortedBy { it.dateMs }.takeLast(30)
        }
        if (weightSeries.size >= 2) {
            WeightTrendCard(weightSeries)
        }

        if (measurements.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Henüz ölçüm kaydı yok.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(measurements) { measurement ->
                    MeasurementHistoryItem(
                        measurement = measurement,
                        onEdit = { onEditMeasurement(measurement.id) },
                        onDelete = { viewModel.deleteMeasurement(measurement) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightTrendCard(series: List<MeasurementEntity>) {
    val weights = series.map { it.weight }
    val minW = weights.min()
    val maxW = weights.max()
    val range = (maxW - minW).takeIf { it > 0 } ?: 1.0
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kilo Trendi (son ${series.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("%.1f → %.1f kg".format(weights.first(), weights.last()), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val w = size.width
                val h = size.height
                val stepX = if (series.size > 1) w / (series.size - 1).toFloat() else w
                val path = Path()
                series.forEachIndexed { i, m ->
                    val normY = ((m.weight - minW) / range).toFloat()
                    val x = stepX * i
                    val y = h - normY * h * 0.9f - h * 0.05f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = primary, style = Stroke(width = 4f))
                series.forEachIndexed { i, m ->
                    val normY = ((m.weight - minW) / range).toFloat()
                    val x = stepX * i
                    val y = h - normY * h * 0.9f - h * 0.05f
                    drawCircle(color = secondary, radius = 5f, center = Offset(x, y))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("min %.1f".format(minW), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("max %.1f".format(maxW), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun MeasurementHistoryItem(
    measurement: MeasurementEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd MMMM yyyy", Locale("tr")) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(sdf.format(Date(measurement.dateMs)), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", modifier = Modifier.size(18.dp), tint = Color.Red)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("${measurement.weight} kg / ${measurement.height} cm", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

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
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Ölçümü Sil") },
            text = { Text("Bu ölçüm kaydı silinsin mi? Geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text("Sil", color = Color.Red)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("İptal") } }
        )
    }
}

@Composable
fun MeasurementLabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ─── PAKETLER ────────────────────────────────────────────────────────────────

@Composable
fun PackagesTab(member: MemberEntity, viewModel: MemberViewModel = hiltViewModel()) {
    val actives by viewModel.getActivePackages(member.id).collectAsState(initial = emptyList())
    val history by viewModel.getPackageHistory(member.id).collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale("tr")) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Aktif Paketler (${actives.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (actives.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    Text("Aktif paket yok.", color = Color.Gray)
                }
            }
        } else {
            items(actives, key = { it.id }) { pkg ->
                MemberPackageCard(pkg = pkg, dateFormat = dateFormat, isActive = true)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Text("Geçmiş Paketler (${history.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Gray)
        }
        if (history.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Text("Henüz geçmiş işlem yok.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            items(history, key = { it.id }) { pkg ->
                MemberPackageCard(pkg = pkg, dateFormat = dateFormat, isActive = false)
            }
        }
    }
}

@Composable
private fun MemberPackageCard(
    pkg: MemberPackageEntity,
    dateFormat: SimpleDateFormat,
    isActive: Boolean
) {
    val used = if (pkg.totalSessions > 0) (pkg.totalSessions - pkg.remainingSessions).coerceAtLeast(0) else 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(pkg.packageNameSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (isActive) "AKTİF" else "GEÇMİŞ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
            Spacer(Modifier.height(8.dp))
            DetailRow("Tür", pkg.packageType)
            DetailRow("Başlangıç", dateFormat.format(Date(pkg.startDateMs)))
            DetailRow("Bitiş", dateFormat.format(Date(pkg.endDateMs)))
            DetailRow(
                "Seans",
                when {
                    pkg.totalSessions == -1 -> "Sınırsız"
                    pkg.totalSessions > 0 -> "$used / ${pkg.totalSessions} kullanıldı"
                    else -> "-"
                }
            )
            DetailRow("Ödeme", "${pkg.paymentType}${if (pkg.installmentCount > 1) " (${pkg.installmentCount}x)" else ""}")
            DetailRow("Tutar", "₺%.2f".format(pkg.pricePaid))
            if (pkg.installmentSurcharge > 0) {
                DetailRow("Taksit Komisyonu", "+₺%.2f".format(pkg.installmentSurcharge))
            }
        }
    }
}

// ─── POSTÜR ──────────────────────────────────────────────────────────────────

@Composable
fun PostureTab(
    member: MemberEntity,
    viewModel: MemberViewModel,
    onOpenPosture: () -> Unit
) {
    val comments by viewModel.getPostureComments(member.id).collectAsState(initial = emptyList())
    val sdf = remember { SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("tr")) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Postür Gözlemleri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(onClick = onOpenPosture, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Yeni", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (comments.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("Henüz postür gözlemi yok.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(comments, key = { it.id }) { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                sdf.format(Date(c.dateMs)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(c.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

// ─── Ortak ──────────────────────────────────────────────────────────────────

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
