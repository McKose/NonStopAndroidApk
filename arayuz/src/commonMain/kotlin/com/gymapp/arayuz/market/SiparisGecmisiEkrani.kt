package com.gymapp.arayuz.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.domain.Money
import com.gymapp.domain.ParaBicimi
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import com.gymapp.domain.TarihBicimi

/**
 * Sipariş geçmişi — `app`'teki `OrderHistoryScreen`'in ortak modüle taşınmış hâli.
 *
 * Durum dışarıdan: siparişler ve üye adları parametre, geri gezinme geri çağrı.
 *
 * `uyeAdlari` ayrı bir eşleme olarak geliyor çünkü sipariş satırında yalnızca
 * üye KİMLİĞİ var; adı ViewModel iki sorguyu birleştirerek buluyor. Ekranın
 * kendisi sorgu yapmıyor ve yapmamalı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiparisGecmisiEkrani(
    siparisler: List<OrderEntity>,
    uyeAdlari: Map<String, String>,
    onGeri: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sipariş Geçmişi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { bosluk ->
        if (siparisler.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bosluk),
                contentAlignment = Alignment.Center,
            ) {
                Text("Henüz sipariş bulunmuyor.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(bosluk),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(siparisler) { siparis ->
                    SiparisSatiri(siparis, uyeAdlari)
                }
            }
        }
    }
}

@Composable
private fun SiparisSatiri(siparis: OrderEntity, uyeAdlari: Map<String, String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sipariş #${siparis.id.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = ParaBicimi.tl(Money(siparis.finalPriceMinor)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        // Ham UUID yerine ad. Önceden "Üye ID: 9f3c1a0e-…"
                        // basılıyordu; hangi üyenin ne aldığı ekrandan
                        // okunamıyordu. Adı bulunamayan kimlik silinmiş üyeye
                        // ait: kimliği göstermek yine okunmaz olurdu.
                        text = when (val uyeId = siparis.memberId) {
                            null -> "Misafir Müşteri"
                            else -> uyeAdlari[uyeId] ?: "Silinmiş üye"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = TarihBicimi.gunKisaAyYilSaat(siparis.dateMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val odendi = siparis.paymentStatus == PaymentState.PAID
                    Badge(
                        containerColor = if (odendi) Color(0xFF4CAF50) else Color(0xFFF44336),
                    ) {
                        Text(
                            text = if (odendi) "ÖDENDİ" else "BEKLEMEDE",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (siparis.paymentMethod) {
                            PaymentMethod.CASH -> "Nakit"
                            PaymentMethod.CARD -> "Kart"
                            PaymentMethod.MULTISPORT -> "MultiSpor"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
