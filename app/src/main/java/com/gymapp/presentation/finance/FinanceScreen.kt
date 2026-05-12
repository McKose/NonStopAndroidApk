package com.gymapp.presentation.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.TransactionEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRevenue: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToTaxes: () -> Unit,
    viewModel: FinanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finansal Panel", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MonthYearPicker(
                selectedMonth = uiState.selectedMonth,
                selectedYear = uiState.selectedYear,
                onMonthChanged = { month, year -> viewModel.setPeriod(month, year) }
            )

            // Finansal Özet Kartları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinanceSummaryCard(
                    label = "Gelir",
                    amount = uiState.totalIncome,
                    icon = Icons.Default.TrendingUp,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                FinanceSummaryCard(
                    label = "Gider",
                    amount = uiState.totalExpense,
                    icon = Icons.Default.TrendingDown,
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
                FinanceSummaryCard(
                    label = "Net Kâr",
                    amount = uiState.totalProfit,
                    icon = Icons.Default.AccountBalanceWallet,
                    color = if (uiState.totalProfit >= 0) Color(0xFF2196F3) else Color(0xFFFF9800),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Detaylar ve Hareketler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            FinanceNavigationButton(
                label = "Ciro Detayları",
                description = "Aylık, 3/6 aylık ve yıllık ciro raporları",
                icon = Icons.Default.BarChart,
                color = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToRevenue
            )

            FinanceNavigationButton(
                label = "Gider Detayları",
                description = "Manuel giderler ve otomatik vergi kayıtları",
                icon = Icons.Default.Payments,
                color = Color(0xFFF44336),
                onClick = onNavigateToExpenses
            )

            FinanceNavigationButton(
                label = "Vergi Detayları",
                description = "KDV ve Gelir Vergisi çeyreklik matrah raporu",
                icon = Icons.Default.Gavel,
                color = Color(0xFF7C4DFF),
                onClick = onNavigateToTaxes
            )
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, category, desc, method, isPending, type ->
                viewModel.addExpense(
                    amount = amount,
                    category = category,
                    description = desc,
                    paymentMethod = method,
                    isPending = isPending,
                    type = type
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
fun FinanceNavigationButton(
    label: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
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
        modifier = Modifier.fillMaxWidth(),
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
fun FinanceSummaryCard(label: String, amount: Double, icon: ImageVector, color: Color, modifier: Modifier) {
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
                "₺${String.format(Locale.getDefault(), "%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun RevenueCard(label: String, amount: Double) {
    Card(
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                "₺${String.format(Locale.getDefault(), "%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TransactionListItem(
    transaction: TransactionEntity,
    onMarkPaid: (() -> Unit)? = null
) {
    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("tr")) }
    val isIncome = transaction.type == "INCOME"
    
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
                        text = categoryLabel(transaction.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (transaction.category == "TAX_VAT" || transaction.category == "TAX_INCOME")
                                    Color(0xFFD32F2F)
                                else MaterialTheme.colorScheme.primary,
                        fontWeight = if (transaction.category == "TAX_VAT" || transaction.category == "TAX_INCOME")
                                        FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = when(transaction.paymentMethod) {
                            "CARD" -> "💳 Kart"
                            "MULTISPORT" -> "🏢 Multi"
                            else -> "💵 Nakit"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(sdf.format(Date(transaction.date)), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                if (transaction.isPending && onMarkPaid != null) {
                    OutlinedButton(
                        onClick = onMarkPaid,
                        modifier = Modifier.padding(top = 6.dp).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("Ödendi olarak işaretle", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Text(
                "${if (isIncome) "+" else "-"}₺${String.format(Locale.getDefault(), "%,.0f", transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

private fun categoryLabel(category: String): String = when (category) {
    "MEMBERSHIP" -> "Üyelik"
    "MULTISPORT_SESSION" -> "MultiSport Seans"
    "TRAINER_COMMISSION" -> "Antrenör Hakedişi"
    "SALARY" -> "Maaş"
    "RENT" -> "Kira"
    "UTILITY" -> "Fatura"
    "MARKET_SALE" -> "Market Satışı"
    "TAX_VAT" -> "KDV (Otomatik)"
    "TAX_INCOME" -> "Gelir Vergisi (Otomatik)"
    "OTHER" -> "Diğer"
    else -> category
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String, String, Boolean, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Gider") }
    var selectedMethod by remember { mutableStateOf("CASH") }
    var isPending by remember { mutableStateOf(false) }
    var isIncome by remember { mutableStateOf(false) }

    val categories = listOf("Maaş", "Kira", "Fatura", "Alışveriş", "Bakım", "Eğitmen Hakediş", "Üyelik Geliri", "Market Satışı", "Diğer")
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
                        value = selectedCategory,
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
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
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
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not (İsteğe Bağlı)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPending, onCheckedChange = { isPending = it })
                    Text("Ödeme Bekliyor (Yaklaşan)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = amount.toDoubleOrNull() ?: 0.0
                    if (description.isNotBlank() && a > 0) {
                        onConfirm(a, selectedCategory, description, selectedMethod, isPending, if (isIncome) "INCOME" else "EXPENSE")
                    }
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
