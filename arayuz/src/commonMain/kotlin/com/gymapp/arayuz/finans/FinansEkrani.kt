package com.gymapp.arayuz.finans

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymapp.arayuz.ortak.SaltOkunurSerit
import com.gymapp.domain.Money
import com.gymapp.domain.ParaBicimi
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.TarihBicimi

/** Tür süzgecinin değerleri; ViewModel de aynı metinleri bekliyor. */
object FinansSuzgeci {
    const val TUMU = "ALL"
    const val GELIR = "INCOME"
    const val GIDER = "EXPENSE"
    const val BEKLEYEN = "PENDING"
}

/**
 * Finans ekranı — `app`'teki `FinanceScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Yetkisiz görünüm ekranın içinde
 * [gorebilir] `false` iken defter hiç çizilmiyor; yerine sebebi yazan bir
 * ekran geliyor. Boş liste ya da sıfır dolu bir özet göstermek daha kötü
 * olurdu: eğitmen salonun hiç geliri olmadığını sanırdı. Giriş noktalarının
 * gizlenmesine ek bir kat — gizlenmiş düğme kural değil, yalnızca görüntü.
 *
 * ### Durum sınıfı yerine parametreler
 * `FinanceUiState` `app`'te kalıyor: varsayılan değerleri `LocalDate.now()`
 * kullanıyor ve o çağrı JVM'e özgü. Sınıfı taşımak, ortak modülde
 * derlenmeyen bir varsayılanı da beraberinde getirirdi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinansEkrani(
    gorebilir: Boolean,
    ay: Int,
    yil: Int,
    aylikCiro: Money,
    ucAylikCiro: Money,
    altiAylikCiro: Money,
    yillikCiro: Money,
    gelir: Money,
    gider: Money,
    netKar: Money,
    turSuzgeci: String,
    yontemSuzgeci: String,
    kayitlar: List<FinansKaydi>,
    eklemeAcik: Boolean,
    onGeri: () -> Unit,
    onDonemDegisti: (ay: Int, yil: Int) -> Unit,
    onTurSuzgeci: (String) -> Unit,
    onYontemSuzgeci: (String) -> Unit,
    onEklemeAc: () -> Unit,
    onEklemeKapat: () -> Unit,
    onKayitEkle: (tutar: String, kategori: String, aciklama: String, yontem: String, gelirMi: Boolean) -> Unit,
    snackbarDurumu: SnackbarHostState = remember { SnackbarHostState() },
) {
    if (!gorebilir) {
        YetkiYokEkrani(onGeri)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarDurumu) },
        topBar = {
            TopAppBar(
                title = { Text("Finansal Durum", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onEklemeAc,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "İşlem Ekle", tint = Color.White)
            }
        },
    ) { bosluk ->
        Column(modifier = Modifier.padding(bosluk).fillMaxSize()) {
            DonemSecici(ay = ay, yil = yil, onDonemDegisti = onDonemDegisti)

            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item { CiroKarti("Aylık Ciro", aylikCiro) }
                item { CiroKarti("3 Aylık Ciro", ucAylikCiro) }
                item { CiroKarti("6 Aylık Ciro", altiAylikCiro) }
                item { CiroKarti("Yıllık Ciro", yillikCiro) }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OzetKarti(
                    etiket = "Aylık Gelir",
                    tutar = gelir,
                    ikon = Icons.Default.TrendingUp,
                    renk = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                )
                OzetKarti(
                    etiket = "Aylık Gider",
                    tutar = gider,
                    ikon = Icons.Default.TrendingDown,
                    renk = Color(0xFFF44336),
                    modifier = Modifier.weight(1f),
                )
                OzetKarti(
                    etiket = "Net Kâr",
                    tutar = netKar,
                    ikon = Icons.Default.AccountBalanceWallet,
                    renk = if (!netKar.isNegative) Color(0xFF2196F3) else Color(0xFFFF9800),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    FinansSuzgeci.TUMU to "Tümü",
                    FinansSuzgeci.GELIR to "Gelir",
                    FinansSuzgeci.GIDER to "Gider",
                    FinansSuzgeci.BEKLEYEN to "Bekleyen",
                ).forEach { (deger, etiket) ->
                    FilterChip(
                        selected = turSuzgeci == deger,
                        onClick = { onTurSuzgeci(deger) },
                        label = { Text(etiket) },
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("ALL" to "Tümü", "CASH" to "Nakit", "CARD" to "Kart", "MULTISPORT" to "Multi")
                    .forEach { (deger, etiket) ->
                        FilterChip(
                            selected = yontemSuzgeci == deger,
                            onClick = { onYontemSuzgeci(deger) },
                            label = { Text(etiket) },
                        )
                    }
            }

            Spacer(Modifier.height(8.dp))

            if (kayitlar.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu dönemde işlem bulunamadı.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(kayitlar) { kayit -> KayitSatiri(kayit) }
                }
            }
        }
    }

    if (eklemeAcik) {
        KayitEklemeDiyalogu(
            onKapat = onEklemeKapat,
            onKaydet = onKayitEkle,
        )
    }
}

/**
 * Finans ekranına yetkisiz gelindiğinde gösterilen ekran.
 *
 * Boş bir liste ya da sıfır dolu bir özet göstermek daha kötü olurdu:
 * eğitmen salonun hiç geliri olmadığını sanırdı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YetkiYokEkrani(onGeri: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finansal Durum", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { bosluk ->
        Box(
            modifier = Modifier.padding(bosluk).fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            SaltOkunurSerit(
                "Finans ekranı salon sahibi ve yöneticiye açık. Kendi hakediş " +
                    "kayıtlarınızı üye ve randevu ekranlarından görebilirsiniz.",
            )
        }
    }
}

@Composable
private fun DonemSecici(
    ay: Int,
    yil: Int,
    onDonemDegisti: (ay: Int, yil: Int) -> Unit,
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
                if (ay == 0) onDonemDegisti(11, yil - 1)
                else onDonemDegisti(ay - 1, yil)
            }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Önceki Ay")
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${months[ay]} $yil",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Dönem Seçimi", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            
            IconButton(onClick = {
                if (ay == 11) onDonemDegisti(0, yil + 1)
                else onDonemDegisti(ay + 1, yil)
            }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Sonraki Ay")
            }
        }
    }
}

@Composable
private fun OzetKarti(
    etiket: String,
    tutar: Money,
    ikon: ImageVector,
    renk: Color,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = renk.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(ikon, contentDescription = null, tint = renk)
            Spacer(Modifier.height(8.dp))
            Text(etiket, style = MaterialTheme.typography.labelMedium, color = renk)
            Text(
                ParaBicimi.tl(tutar),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = renk
            )
        }
    }
}

@Composable
private fun CiroKarti(etiket: String, tutar: Money) {
    Card(
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(etiket, style = MaterialTheme.typography.labelSmall)
            Text(
                ParaBicimi.tlYuvarlak(tutar),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun KayitSatiri(kayit: FinansKaydi) {
    val gelirMi = kayit.isIncome
    
    val containerColor = if (kayit.isPending) {
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
                color = if (gelirMi) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFFF44336).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (gelirMi) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (gelirMi) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kayit.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (kayit.isPending) {
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
                        text = kayit.categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = when (kayit.paymentMethod) {
                            PaymentMethod.CARD -> "💳 Kart"
                            PaymentMethod.MULTISPORT -> "🏢 Multi"
                            PaymentMethod.CASH -> "💵 Nakit"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(TarihBicimi.gunKisaAySaat(kayit.occurredAtMs), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                kayit.note?.let {
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
                "${if (gelirMi) "+" else "-"}${ParaBicimi.tl(kayit.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (gelirMi) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KayitEklemeDiyalogu(
    onKapat: () -> Unit,
    /** (tutar metni, kategori, açıklama, ödeme yöntemi, gelir mi?) */
    onKaydet: (String, String, String, String, Boolean) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("OTHER") }
    var selectedMethod by remember { mutableStateOf("CASH") }
    var gelirMi by remember { mutableStateOf(false) }

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
        onDismissRequest = onKapat,
        title = { Text(if (gelirMi) "Gelir Ekle" else "Gider Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !gelirMi,
                        onClick = { gelirMi = false },
                        label = { Text("Gider") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = gelirMi,
                        onClick = { gelirMi = true },
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
                    onKaydet(amount, selectedCategory, description, selectedMethod, gelirMi)
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onKapat) {
                Text("İptal")
            }
        }
    )
}
