package com.gymapp.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Sync
import com.gymapp.data.sync.SyncState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersonnel: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val syncState by viewModel.syncState.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    var showCommissionDialog by remember { mutableStateOf(false) }
    var showSalonInfoDialog by remember { mutableStateOf(false) }

    // Çıkışta cihazdaki veri siliniyor; gönderilmemiş kayıt varsa önce soruluyor.
    val cikistaBekleyen by viewModel.cikistaBekleyen.collectAsState()
    cikistaBekleyen?.let { bekleyen ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelLogout() },
            title = { Text("Gönderilmemiş değişiklik var") },
            text = {
                Text(
                    "$bekleyen değişiklik henüz sunucuya gönderilmedi. Çıkış yapıldığında " +
                        "cihazdaki veriler silinir ve bu değişiklikler kaybolur.\n\n" +
                        "İnternet bağlantınız varsa önce \"Şimdi Eşitle\" deyip bekleyin."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmLogout(onLogout) }) {
                    Text("Yine de çık", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLogout() }) { Text("Vazgeç") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Yönetim", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            
            SettingsItem(
                title = "Personel Yönetimi",
                subtitle = "Eğitmen ve çalışanları yönet",
                icon = Icons.Default.Group,
                onClick = onNavigateToPersonnel
            )
            
            HorizontalDivider()
            Text("Finansal Ayarlar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            SettingsItem(
                title = "Hakediş Oranları",
                subtitle = "Özel ders ve MultiSport hakediş oranlarını belirle",
                icon = Icons.Default.Percent,
                onClick = { showCommissionDialog = true }
            )

            HorizontalDivider()
            Text("Sistem", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            SettingsItem(
                title = "Salon Bilgileri",
                subtitle = viewModel.salonName,
                icon = Icons.Default.Store,
                onClick = { showSalonInfoDialog = true }
            )
            
            // KALDIRILDI: "Giriş Şifresi". Giriş artık Supabase Auth ile
            // yapılıyor ve şifre panelden/hesap sahibinden değişiyor; buradaki
            // alan hiçbir yerde okunmayan bir tercihi yazıyordu. Çalışıyormuş
            // gibi görünen ama hiçbir etkisi olmayan bir ayar, olmamasından
            // daha kötü.

            SettingsItem(
                title = "Sunucuya Eşitle",
                subtitle = senkronizasyonOzeti(syncState, pendingCount),
                icon = Icons.Default.Sync,
                onClick = { viewModel.syncNow() }
            )

            SettingsItem(
                title = "Çıkış Yap",
                subtitle = "Oturumu sonlandır",
                icon = Icons.Default.Logout,
                onClick = { viewModel.requestLogout(onLogout) }
            )
        }

        if (showCommissionDialog) {
            ConfigDialog(
                title = "Hakediş Ayarları",
                onDismiss = { showCommissionDialog = false }
            ) {
                var rateText by remember { mutableStateOf(viewModel.commissionRate.toString()) }
                var msRateText by remember { mutableStateOf(viewModel.multiSportCommission.toString()) }

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Eğitmen Hakediş (%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = msRateText,
                    onValueChange = { msRateText = it },
                    label = { Text("MultiSport Hakediş (TL/Ders)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.updateCommissionRate(rateText.toFloatOrNull() ?: 0f)
                        viewModel.updateMultiSportCommission(msRateText.toFloatOrNull() ?: 0f)
                        showCommissionDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kaydet")
                }
            }
        }

        if (showSalonInfoDialog) {
            ConfigDialog(
                title = "Salon Bilgileri",
                onDismiss = { showSalonInfoDialog = false }
            ) {
                var nameText by remember { mutableStateOf(viewModel.salonName) }
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Salon Adı") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.updateSalonName(nameText)
                        showSalonInfoDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kaydet")
                }
            }
        }

    }
}

@Composable
fun ConfigDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(content = content) },
        confirmButton = {}
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Gray)
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
private fun senkronizasyonOzeti(durum: SyncState, bekleyen: Int): String = when (durum) {
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
