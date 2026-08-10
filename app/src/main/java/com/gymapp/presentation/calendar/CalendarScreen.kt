package com.gymapp.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.TrainingType
import com.gymapp.domain.labelTr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateBack: () -> Unit,
    viewModel: CalendarViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("tr"))
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Çakışma / seans hakkı gibi reddedilme sebepleri artık kullanıcıya gösteriliyor.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CalendarEvent.AppointmentSaved -> {
                    showAddSheet = false
                    snackbarHostState.showSnackbar("Randevu oluşturuldu.")
                }
                is CalendarEvent.StatusUpdated -> {
                    selectedAppointment = null
                    snackbarHostState.showSnackbar("Randevu güncellendi.")
                }
                is CalendarEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Randevu Takvimi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setDate(LocalDate.now()) }) {
                        Text("Bugün", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Randevu Ekle") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // ─── Tarih Seçici ───────────────────────────────────────────
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.setDate(selectedDate.minusDays(1)) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                    }

                    Text(
                        text = selectedDate.format(dateFormatter),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = { viewModel.setDate(selectedDate.plusDays(1)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            // ─── Saat Slotları ──────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items((9..21).toList()) { hour ->
                    val appointmentsInHour = uiState.appointments.filter {
                        Instant.ofEpochMilli(it.startTimeMs)
                            .atZone(ZoneId.systemDefault()).hour == hour
                    }
                    TimeSlotRow(
                        hour = hour,
                        appointments = appointmentsInHour,
                        members = uiState.members,
                        staffList = uiState.staffList,
                        onClick = { selectedAppointment = it }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        if (showAddSheet) {
            AddAppointmentSheet(
                members = uiState.members,
                staffList = uiState.staffList,
                onDismiss = { showAddSheet = false },
                // Sheet burada kapatılmaz: kayıt reddedilirse (çakışma, seans hakkı yok)
                // kullanıcı formunu kaybetmesin diye açık kalır.
                onConfirm = viewModel::addAppointment,
                sheetState = sheetState
            )
        }

        if (selectedAppointment != null) {
            AppointmentStatusDialog(
                appointment = selectedAppointment!!,
                member = uiState.members.find { it.id == selectedAppointment!!.memberId },
                onDismiss = { selectedAppointment = null },
                // Diyalog StatusUpdated olayında kapanır; hata olursa açık kalıp mesaj gösterir.
                onConfirm = { status, notes ->
                    viewModel.updateAppointmentStatus(selectedAppointment!!.id, status, notes)
                }
            )
        }
    }
}

@Composable
fun TimeSlotRow(
    hour: Int,
    appointments: List<AppointmentEntity>,
    members: List<MemberEntity>,
    staffList: List<StaffEntity>,
    onClick: (AppointmentEntity) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%02d:00", hour),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(60.dp).padding(top = 12.dp),
            color = Color.Gray
        )
        
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (appointments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Müsait", modifier = Modifier.padding(start = 12.dp), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                appointments.forEach { appt ->
                    val memberName = members.find { it.id == appt.memberId }?.fullName ?: "Bilinmeyen Üye"
                    val staffName = staffList.find { it.id == appt.staffId }?.fullName ?: "Bilinmeyen Eğitmen"
                    
                    val statusColor = when (appt.state) {
                        AppointmentState.COMPLETED -> Color(0xFF4CAF50)
                        AppointmentState.CANCELLED -> Color(0xFFF44336)
                        AppointmentState.POSTPONED -> Color(0xFFFF9800)
                        AppointmentState.NO_SHOW -> Color(0xFF9E9E9E)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Card(
                        onClick = { onClick(appt) },
                        colors = CardDefaults.cardColors(
                            containerColor = statusColor.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(memberName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${appt.trainingType.labelTr()} - $staffName", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            
                            if (appt.state != AppointmentState.SCHEDULED) {
                                Text(
                                    text = when (appt.state) {
                                        AppointmentState.COMPLETED -> "Tamamlandı"
                                        AppointmentState.CANCELLED -> "İptal"
                                        AppointmentState.POSTPONED -> "Ertelendi"
                                        AppointmentState.NO_SHOW -> "Gelmedi"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentStatusDialog(
    appointment: AppointmentEntity,
    member: MemberEntity?,
    onDismiss: () -> Unit,
    onConfirm: (AppointmentState, String) -> Unit
) {
    var notes by remember { mutableStateOf(appointment.notes ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(member?.fullName ?: "Randevu Durumu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Antrenman Notları") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                Text("Durum Güncelle:", style = MaterialTheme.typography.labelLarge)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onConfirm(AppointmentState.COMPLETED, notes) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tamamlandı", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { onConfirm(AppointmentState.POSTPONED, notes) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ertelendi", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = { onConfirm(AppointmentState.CANCELLED, notes) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("İptal Edildi")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppointmentSheet(
    members: List<MemberEntity>,
    staffList: List<StaffEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, TrainingType) -> Unit,
    sheetState: SheetState
) {
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    var selectedStaffId by remember { mutableStateOf<String?>(null) }
    var selectedHour by remember { mutableIntStateOf(9) }
    var selectedType by remember { mutableStateOf(TrainingType.FITNESS) }
    val trainingTypes = TrainingType.entries

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
            Text("Yeni Randevu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            
            Text("Ders Türü", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                trainingTypes.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.labelTr()) }
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Üye Seçin", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.height(120.dp)) {
                items(members) { member ->
                    ListItem(
                        headlineContent = { Text(member.fullName) },
                        leadingContent = {
                            RadioButton(selected = selectedMemberId == member.id, onClick = { selectedMemberId = member.id })
                        },
                        modifier = Modifier.clickable { selectedMemberId = member.id }
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Eğitmen Seçin", style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.height(120.dp)) {
                items(staffList) { staff ->
                    ListItem(
                        headlineContent = { Text(staff.fullName) },
                        supportingContent = { Text(staff.branch) },
                        leadingContent = {
                            RadioButton(selected = selectedStaffId == staff.id, onClick = { selectedStaffId = staff.id })
                        },
                        modifier = Modifier.clickable { selectedStaffId = staff.id }
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Saat Seçin", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (9..21).forEach { h ->
                    FilterChip(
                        selected = selectedHour == h,
                        onClick = { selectedHour = h },
                        label = { Text(String.format(Locale.getDefault(), "%02d:00", h)) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { 
                    if (selectedMemberId != null && selectedStaffId != null) {
                        onConfirm(selectedMemberId!!, selectedStaffId!!, selectedHour, selectedType)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMemberId != null && selectedStaffId != null
            ) {
                Text("Randevuyu Kaydet")
            }
        }
    }
}
