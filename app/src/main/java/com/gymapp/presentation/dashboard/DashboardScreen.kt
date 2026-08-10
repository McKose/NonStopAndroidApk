package com.gymapp.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gymapp.domain.labelTr
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToMembers: () -> Unit,
    onNavigateToFinance: () -> Unit,
    onNavigateToMarket: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToPackages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Non Stop", fontWeight = FontWeight.Bold) },
                actions = {
                    if (!uiState.isTrainer) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                        }
                        IconButton(onClick = onNavigateToPackages) {
                            Icon(Icons.Default.Inventory, contentDescription = "Paketler")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // ─── İstatistik Kartları (Pill Design) ──────────────────────
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        StatCard(
                            label = "Aktif Üye",
                            value = uiState.activeMembers.toString(),
                            icon = Icons.Default.People,
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    item {
                        StatCard(
                            label = "Günlük Ders",
                            value = uiState.dailyAppointments.size.toString(),
                            icon = Icons.Default.Event,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                }
            }

            // ─── Hızlı Erişim Menüsü ────────────────────────────────────────
            item {
                Text("Hızlı Erişim", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard("Takvim", Icons.Default.CalendarMonth, Modifier.weight(1f), onNavigateToCalendar)
                    QuickActionCard("Üyeler", Icons.Default.Groups, Modifier.weight(1f), onNavigateToMembers)
                }
                if (!uiState.isTrainer) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard("Paketler", Icons.Default.Inventory, Modifier.weight(1f), onNavigateToPackages)
                        QuickActionCard("Market", Icons.Default.ShoppingCart, Modifier.weight(1f), onNavigateToMarket)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard("Finans", Icons.Default.Payments, Modifier.weight(1f), onNavigateToFinance)
                        QuickActionCard("Ayarlar", Icons.Default.Settings, Modifier.weight(1f), onNavigateToSettings)
                    }
                }
            }

            // ─── Kritik Uyarılar ────────────────────────────────────────────
            item {
                Text("Bugünkü Randevular", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.dailyAppointments.isEmpty()) {
                    Text("Bugün için randevu bulunmuyor.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items(uiState.dailyAppointments) { appt ->
                val member = uiState.members.find { it.id == appt.memberId }
                val staff = uiState.staffList.find { it.id == appt.staffId }
                AppointmentDashboardItem(appt, member, staff)
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Kritik Uyarılar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.criticalAlerts.isEmpty()) {
                    Text("Her şey yolunda!", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items(uiState.criticalAlerts) { alert ->
                AlertItem(alert)
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, containerColor: Color) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.width(160.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun QuickActionCard(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AppointmentDashboardItem(
    appt: AppointmentEntity,
    member: MemberEntity?,
    staff: StaffEntity?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hour = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(appt.startTimeMs))
            Text(
                text = hour,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(member?.fullName ?: "Bilinmeyen Üye", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("${appt.trainingType.labelTr()} - ${staff?.fullName ?: "Eğitmen atanmadı"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AlertItem(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
