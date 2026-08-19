package com.gymapp.presentation.market

import com.gymapp.domain.ParaBicimi
import com.gymapp.domain.TarihBicimi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.domain.PaymentState
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: OrderHistoryViewModel = koinViewModel()
) {
    val orders by viewModel.orders.collectAsState()
    val memberNames by viewModel.memberNames.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sipariş Geçmişi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        if (orders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Henüz sipariş bulunmuyor.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders) { order ->
                    OrderItem(order, memberNames)
                }
            }
        }
    }
}

@Composable
fun OrderItem(order: OrderEntity, memberNames: Map<String, String> = emptyMap()) {
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sipariş #${order.id.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = ParaBicimi.tl(Money(order.finalPriceMinor)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        // Ham UUID yerine ad. Önceden "Üye ID: 9f3c1a0e-…"
                        // basılıyordu; hangi üyenin ne aldığı ekrandan
                        // okunamıyordu. Adı bulunamayan kimlik silinmiş üyeye
                        // ait: kimliği göstermek yine okunmaz olurdu.
                        text = when (val uyeId = order.memberId) {
                            null -> "Misafir Müşteri"
                            else -> memberNames[uyeId] ?: "Silinmiş üye"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = TarihBicimi.gunKisaAyYilSaat(order.dateMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Badge(
                        containerColor = if (order.paymentStatus == PaymentState.PAID) Color(0xFF4CAF50) else Color(0xFFF44336)
                    ) {
                        Text(
                            text = if (order.paymentStatus == PaymentState.PAID) "ÖDENDİ" else "BEKLEMEDE",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (order.paymentMethod) {
                            PaymentMethod.CASH -> "Nakit"
                            PaymentMethod.CARD -> "Kart"
                            PaymentMethod.MULTISPORT -> "MultiSpor"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
