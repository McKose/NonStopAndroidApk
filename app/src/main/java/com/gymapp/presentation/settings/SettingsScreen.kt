package com.gymapp.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.InstallmentCommissionEntity
import com.gymapp.data.local.entity.MultiSportRateEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersonnel: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var showInstallmentDialog by remember { mutableStateOf(false) }
    var showMultiSportDialog by remember { mutableStateOf(false) }
    var showSalonInfoDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val installments by viewModel.installmentRates.collectAsState()
    val msHistory by viewModel.multiSportHistory.collectAsState()
    val currentMsRate = msHistory.firstOrNull { it.supersededByMs == null }

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
                subtitle = "Eğitmen ve çalışanları yönet, hakediş oranlarını kişiye özel ayarla",
                icon = Icons.Default.Group,
                onClick = onNavigateToPersonnel
            )

            HorizontalDivider()
            Text("Finansal Ayarlar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            SettingsItem(
                title = "Taksit Komisyonları",
                subtitle = "Kredi kartı 1-12 taksit oranları (müşteriye yansır)",
                icon = Icons.Default.CreditCard,
                onClick = { showInstallmentDialog = true }
            )

            SettingsItem(
                title = "MultiSport Seans Ücreti",
                subtitle = currentMsRate?.let {
                    "Güncel: %.2f TL/seans".format(it.amount)
                } ?: "Henüz belirlenmedi",
                icon = Icons.Default.FitnessCenter,
                onClick = { showMultiSportDialog = true }
            )

            HorizontalDivider()
            Text("Sistem", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            SettingsItem(
                title = "Salon Bilgileri",
                subtitle = viewModel.salonName,
                icon = Icons.Default.Store,
                onClick = { showSalonInfoDialog = true }
            )

            SettingsItem(
                title = "Giriş Şifresi",
                subtitle = "Yönetici şifresini güncelle",
                icon = Icons.Default.Key,
                onClick = { showPasswordDialog = true }
            )

            SettingsItem(
                title = "Çıkış Yap",
                subtitle = "Oturumu sonlandır",
                icon = Icons.Default.Logout,
                onClick = { viewModel.logout(onLogout) }
            )
        }

        if (showInstallmentDialog) {
            InstallmentRatesDialog(
                rates = installments,
                onSave = { count, pct -> viewModel.updateInstallmentRate(count, pct) },
                onDismiss = { showInstallmentDialog = false }
            )
        }

        if (showMultiSportDialog) {
            MultiSportRateDialog(
                history = msHistory,
                onApplyNewRate = { amount, note -> viewModel.setMultiSportRate(amount, note) },
                onDismiss = { showMultiSportDialog = false }
            )
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

        if (showPasswordDialog) {
            ConfigDialog(
                title = "Şifre Değiştir",
                onDismiss = { showPasswordDialog = false }
            ) {
                var passText by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                OutlinedTextField(
                    value = passText,
                    onValueChange = { passText = it; error = null },
                    label = { Text("Yeni Şifre") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (passText.isBlank()) {
                            error = "Şifre boş olamaz"
                            return@Button
                        }
                        viewModel.updateSalonPassword(passText)
                        showPasswordDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Güncelle")
                }
            }
        }
    }
}

// ─── Taksit komisyon editörü ─────────────────────────────────────────────────

@Composable
private fun InstallmentRatesDialog(
    rates: List<InstallmentCommissionEntity>,
    onSave: (count: Int, ratePercent: Double) -> Unit,
    onDismiss: () -> Unit
) {
    // 1..12 satırlarını her zaman göster; DB'den gelen değerle doldur
    val rowValues = remember(rates) {
        val map = rates.associateBy { it.installmentCount }
        (1..12).map { n -> n to (map[n]?.ratePercent ?: 0.0) }
    }
    val editable = remember(rowValues) {
        mutableStateMapOf<Int, String>().apply {
            rowValues.forEach { (n, v) -> put(n, "%.2f".format(v)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Taksit Komisyonları (%)") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Her taksit sayısı için müşteriden alınacak komisyon yüzdesi. 1 taksit = peşin, genelde 0%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (n in 1..12) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${n} Taksit",
                                modifier = Modifier.width(90.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedTextField(
                                value = editable[n] ?: "0",
                                onValueChange = { editable[n] = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                                singleLine = true,
                                suffix = { Text("%") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                for (n in 1..12) {
                    val raw = editable[n]?.replace(',', '.') ?: "0"
                    val pct = raw.toDoubleOrNull() ?: 0.0
                    onSave(n, pct)
                }
                onDismiss()
            }) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

// ─── MultiSport ücret editörü ────────────────────────────────────────────────

@Composable
private fun MultiSportRateDialog(
    history: List<MultiSportRateEntity>,
    onApplyNewRate: (amount: Double, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var newAmount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale("tr")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MultiSport Seans Ücreti") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Yeni ücret uygulandığı andan itibaren geçerli olur; geçmiş randevular eski ücret üzerinden işlenmeye devam eder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = newAmount,
                    onValueChange = { newAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Yeni Ücret (TL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not (opsiyonel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Text("Geçmiş", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    }
                    Column(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        history.forEach { r ->
                            val from = dateFmt.format(Date(r.effectiveFromMs))
                            val to = r.supersededByMs?.let { dateFmt.format(Date(it)) } ?: "günümüz"
                            val label = "%.2f TL".format(r.amount)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (r.supersededByMs == null) MaterialTheme.colorScheme.primaryContainer
                                            else Color.LightGray.copy(alpha = 0.3f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "$from → $to",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = newAmount.replace(',', '.').toDoubleOrNull()
                if (amt != null && amt >= 0) {
                    onApplyNewRate(amt, note.ifBlank { null })
                    onDismiss()
                }
            }) { Text("Uygula") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

// ─── Jenerik dialog container ───────────────────────────────────────────────

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
            verticalAlignment = Alignment.CenterVertically
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
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
