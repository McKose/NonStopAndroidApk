package com.gymapp.presentation.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gymapp.presentation.common.ReadOnlyNotice
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import java.text.SimpleDateFormat
import java.util.*

/**
 * Finans ekranına yetkisiz gelindiğinde gösterilen ekran.
 *
 * Boş bir liste ya da sıfır dolu bir özet göstermek daha kötü olurdu: eğitmen
 * salonun hiç geliri olmadığını sanırdı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YetkiYokEkrani(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finansal Durum", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            ReadOnlyNotice(
                "Finans ekranı salon sahibi ve yöneticiye açık. Kendi hakediş " +
                    "kayıtlarınızı üye ve randevu ekranlarından görebilirsiniz."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: FinanceViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gorebilir by viewModel.gorebilir.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Yetkisi olmayana defter hiç çizilmiyor. Giriş noktalarının gizlenmesine
    // ek bir kat: gizlenmiş düğme kural değil, yalnızca görüntü.
    if (!gorebilir) {
        YetkiYokEkrani(onNavigateBack)
        return
    }

    // Kayıt sonucu her durumda kullanıcıya bildirilir.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FinanceEvent.Saved -> snackbarHostState.showSnackbar("Kayıt eklendi.")
                is FinanceEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Finansal Durum", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "İşlem Ekle", tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ─── Dönem Seçici ──────────────────────────────────────────
            MonthYearPicker(
                selectedMonth = uiState.selectedMonth,
                selectedYear = uiState.selectedYear,
                onMonthChanged = { month, year ->
                    viewModel.setPeriod(month, year)
                }
            )

            // ─── Ciro Kartları (Aylık, 3 Aylık, 6 Aylık, Yıllık) ───────────────────
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item { RevenueCard("Aylık Ciro", uiState.monthlyRevenue) }
                item { RevenueCard("3 Aylık Ciro", uiState.quarterlyRevenue) }
                item { RevenueCard("6 Aylık Ciro", uiState.halfYearlyRevenue) }
                item { RevenueCard("Yıllık Ciro", uiState.yearlyRevenue) }
            }

            // ─── Finansal Özet Kartları ─────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinanceSummaryCard(
                    label = "Aylık Gelir",
                    amount = uiState.totalIncome,
                    icon = Icons.Default.TrendingUp,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                FinanceSummaryCard(
                    label = "Aylık Gider",
                    amount = uiState.totalExpense,
                    icon = Icons.Default.TrendingDown,
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
                FinanceSummaryCard(
                    label = "Net Kâr",
                    amount = uiState.totalProfit,
                    icon = Icons.Default.AccountBalanceWallet,
                    color = if (!uiState.totalProfit.isNegative) Color(0xFF2196F3) else Color(0xFFFF9800),
                    modifier = Modifier.weight(1f)
                )
            }

            // ─── Filtre Çipleri ──────────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedFilter == "ALL",
                    onClick = { viewModel.setFilter("ALL") },
                    label = { Text("Tümü") }
                )
                FilterChip(
                    selected = uiState.selectedFilter == "INCOME",
                    onClick = { viewModel.setFilter("INCOME") },
                    label = { Text("Gelir") }
                )
                FilterChip(
                    selected = uiState.selectedFilter == "EXPENSE",
                    onClick = { viewModel.setFilter("EXPENSE") },
                    label = { Text("Gider") }
                )
                FilterChip(
                    selected = uiState.selectedFilter == "PENDING",
                    onClick = { viewModel.setFilter("PENDING") },
                    label = { Text("Bekleyen") }
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "Tümü", "CASH" to "Nakit", "CARD" to "Kart", "MULTISPORT" to "Multi").forEach { (id, label) ->
                    FilterChip(
                        selected = uiState.selectedPaymentMethod == id,
                        onClick = { viewModel.setMethodFilter(id) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── İşlem Listesi ──────────────────────────────────────────
            if (uiState.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu dönemde işlem bulunamadı.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.entries) { transaction ->
                        TransactionListItem(transaction)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amountText, category, desc, method, isIncome ->
                // Yeni yazımların tamamı deftere gider.
                viewModel.addEntry(
                    amountText = amountText,
                    categoryName = category,
                    description = desc,
                    paymentMethodName = method,
                    isIncome = isIncome,
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MonthYearPicker(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthChanged: (Int, Int) -> Unit
) {
    val months = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedMonth == 0) onMonthChanged(11, selectedYear - 1)
                else onMonthChanged(selectedMonth - 1, selectedYear)
            }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Önceki Ay")
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${months[selectedMonth]} $selectedYear",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Dönem Seçimi", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            
            IconButton(onClick = {
                if (selectedMonth == 11) onMonthChanged(0, selectedYear + 1)
                else onMonthChanged(selectedMonth + 1, selectedYear)
            }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Sonraki Ay")
            }
        }
    }
}

@Composable
fun FinanceSummaryCard(label: String, amount: Money, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                "₺${String.format(Locale.getDefault(), "%,.2f", amount.asDouble)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun RevenueCard(label: String, amount: Money) {
    Card(
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                "₺${String.format(Locale.getDefault(), "%,.0f", amount.asDouble)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TransactionListItem(transaction: FinanceEntry) {
    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("tr")) }
    val isIncome = transaction.isIncome
    
    val containerColor = if (transaction.isPending) {
        Color(0xFFFF9800).copy(alpha = 0.05f)
    } else {
        Color.Transparent
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isIncome) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFFF44336).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isIncome) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(transaction.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (transaction.isPending) {
                        Surface(
                            color = Color(0xFFFF9800),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                "BEKLEYEN",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = transaction.categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = when (transaction.paymentMethod) {
                            PaymentMethod.CARD -> "💳 Kart"
                            PaymentMethod.MULTISPORT -> "🏢 Multi"
                            PaymentMethod.CASH -> "💵 Nakit"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(sdf.format(Date(transaction.occurredAtMs)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                transaction.note?.let {
                    if (it.isNotBlank()) {
                        Text(
                            text = "Not: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            Text(
                "${if (isIncome) "+" else "-"}₺${String.format(Locale.getDefault(), "%,.2f", transaction.amount.asDouble)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    /** (tutar metni, kategori, açıklama, ödeme yöntemi, gelir mi?) */
    onConfirm: (String, String, String, String, Boolean) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("OTHER") }
    var selectedMethod by remember { mutableStateOf("CASH") }
    var isIncome by remember { mutableStateOf(false) }

    // (LedgerCategory adı, görünen etiket) — seçim enum adını taşır, ekranda Türkçesi görünür.
    val categories = listOf(
        "SALARY" to "Maaş",
        "RENT" to "Kira",
        "BILL" to "Fatura",
        "PURCHASE" to "Alım",
        "COMMISSION" to "Eğitmen Hakedişi",
        "MEMBERSHIP" to "Üyelik",
        "MARKET" to "Market",
        "OTHER" to "Diğer",
    )
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isIncome) "Gelir Ekle" else "Gider Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isIncome,
                        onClick = { isIncome = false },
                        label = { Text("Gider") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isIncome,
                        onClick = { isIncome = true },
                        label = { Text("Gelir") },
                        modifier = Modifier.weight(1f)
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.first == selectedCategory }?.second
                            ?: selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { (categoryName, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedCategory = categoryName
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("CASH" to "Nakit", "CARD" to "Kart", "MULTISPORT" to "Multi").forEach { (id, label) ->
                        FilterChip(
                            selected = selectedMethod == id,
                            onClick = { selectedMethod = id },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Tutar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    modifier = Modifier.fillMaxWidth()
                )
                // KALDIRILDI: "Ödeme Bekliyor" kutusu ve serbest not alanı.
                // Defter modelinde bekleyen tutar ayrı bir bayrak değil, tahakkuk
                // (CHARGE) kaydıdır ve üyelik satışından otomatik doğar; elle
                // girilen kayıtlarda karşılığı yoktu ve kutu hiçbir şey yapmıyordu.
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Doğrulama artık ViewModel'de; hata mesajı Snackbar ile gösteriliyor.
                    onConfirm(amount, selectedCategory, description, selectedMethod, isIncome)
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
