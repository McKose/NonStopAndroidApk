package com.gymapp.presentation.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceTaxScreen(
    onNavigateBack: () -> Unit,
    viewModel: FinanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vergi Detayları", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MonthYearPicker(
                selectedMonth = uiState.selectedMonth,
                selectedYear = uiState.selectedYear,
                onMonthChanged = { month, year -> viewModel.setPeriod(month, year) }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Yıllık Özet • ${uiState.selectedYear}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Divider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                    
                    TaxRow("Toplam KDV (%20)", uiState.taxVatTotal)
                    TaxRow("Toplam Gelir Vergisi", uiState.taxIncomeTotal)
                    TaxRow("Toplam Matrah (Kart+MS)", uiState.taxableBaseYear)
                }
            }

            Text(
                "Çeyrek Bazlı Detaylar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaxTableRow(
                        c1 = "Çeyrek", c2 = "Kart", c3 = "MS",
                        c4 = "KDV", c5 = "Gelir V.", isHeader = true
                    )
                    uiState.taxQuarters.forEach { q ->
                        TaxTableRow(
                            c1 = "Q${q.quarter}",
                            c2 = moneyShort(q.cardIncome),
                            c3 = moneyShort(q.multiSportIncome),
                            c4 = moneyShort(q.vat),
                            c5 = moneyShort(q.quarterIncomeTax)
                        )
                    }
                }
            }
            
            Text(
                "Bilgi: KDV %20 sabit, matrah = CARD + MULTISPORT gelirleri. Gelir vergisi 2026 ücretli dilimleri; her çeyrek kümülatif matraha göre hesaplanır.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun TaxRow(label: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "₺${String.format(Locale.getDefault(), "%,.2f", amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TaxTableRow(
    c1: String, c2: String, c3: String, c4: String, c5: String,
    isHeader: Boolean = false
) {
    val weight = if (isHeader) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth()) {
        Text(c1, modifier = Modifier.weight(1.0f), style = MaterialTheme.typography.bodySmall, fontWeight = weight)
        Text(c2, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, fontWeight = weight)
        Text(c3, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, fontWeight = weight)
        Text(c4, modifier = Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, fontWeight = weight)
        Text(c5, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, fontWeight = weight)
    }
}

private fun moneyShort(v: Double): String =
    "₺${String.format(Locale.getDefault(), "%,.0f", v)}"
