package com.gymapp.presentation.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.local.entity.TransactionCategory
import com.gymapp.data.local.entity.TransactionEntity
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceCommissionScreen(
    onNavigateBack: () -> Unit,
    viewModel: FinanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedStaffForPayment by remember { mutableStateOf<StaffEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personel Hakedişleri", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            MonthYearPicker(
                selectedMonth = uiState.selectedMonth,
                selectedYear = uiState.selectedYear,
                onMonthChanged = { month, year -> viewModel.setPeriod(month, year) }
            )

            if (uiState.allStaff.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Personel bulunamadı.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.allStaff) { staff ->
                        StaffCommissionItem(
                            staff = staff,
                            transactions = uiState.transactions,
                            onPayClick = { selectedStaffForPayment = staff }
                        )
                    }
                }
            }
        }
    }

    if (selectedStaffForPayment != null) {
        AddPaymentDialog(
            staffName = selectedStaffForPayment!!.fullName,
            onDismiss = { selectedStaffForPayment = null },
            onConfirm = { amount, desc ->
                viewModel.addExpense(
                    amount = amount,
                    category = TransactionCategory.STAFF_PAYMENT,
                    description = desc,
                    paymentMethod = "CASH",
                    note = "Personel: ${selectedStaffForPayment!!.fullName}",
                    type = "EXPENSE"
                )
                selectedStaffForPayment = null
            }
        )
    }
}

@Composable
fun StaffCommissionItem(
    staff: StaffEntity,
    transactions: List<TransactionEntity>,
    onPayClick: () -> Unit
) {
    // Toplam Hakediş (TRAINER_COMMISSION)
    val totalCommission = transactions
        .filter { it.staffId == staff.id && it.category == TransactionCategory.TRAINER_COMMISSION }
        .sumOf { it.amount }

    // Yapılan Ödemeler (STAFF_PAYMENT)
    val totalPaid = transactions
        .filter { it.category == TransactionCategory.STAFF_PAYMENT && it.note?.contains("Personel: ${staff.fullName}") == true }
        .sumOf { it.amount }

    val remaining = totalCommission - totalPaid

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(staff.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(staff.title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Button(
                    onClick = onPayClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ödeme Yap", style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CommissionMetric("Toplam Hakediş", totalCommission, Color.Black)
                CommissionMetric("Ödenen", totalPaid, Color(0xFF4CAF50))
                CommissionMetric("Kalan", remaining, if (remaining > 0) Color(0xFFF44336) else Color.Gray)
            }
        }
    }
}

@Composable
fun CommissionMetric(label: String, amount: Double, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            "₺${String.format(Locale.getDefault(), "%,.0f", amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentDialog(
    staffName: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("$staffName Hakediş Ödemesi") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ödeme Yap: $staffName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Ödeme Tutarı (₺)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = amount.toDoubleOrNull() ?: 0.0
                    if (a > 0) onConfirm(a, description)
                }
            ) {
                Text("Ödemeyi Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
