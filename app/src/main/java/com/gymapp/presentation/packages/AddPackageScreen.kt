package com.gymapp.presentation.packages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gymapp.domain.Money
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType
import com.gymapp.domain.labelTr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPackageScreen(
    packageId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PackageViewModel = koinViewModel()
) {
    // Sınırsız (abonman) paket seans sayısı taşımaz; `-1` sentinel'i yerine
    // ayrı bir anahtar kullanılıyor, böylece geçersiz değer girilemiyor.
    var unlimited by remember { mutableStateOf(false) }
    var sessionCount by remember { mutableStateOf("10") }
    var type by remember { mutableStateOf(TrainingType.FITNESS) }
    var category by remember { mutableStateOf(PackageCategory.INDIVIDUAL) }
    var price by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("30") }
    var isLoading by remember { mutableStateOf(packageId != null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(packageId) {
        if (packageId != null) {
            viewModel.getPackageById(packageId)?.let { pkg ->
                unlimited = pkg.sessionCount == null
                sessionCount = pkg.sessionCount?.toString() ?: "10"
                type = pkg.type
                category = pkg.category
                price = Money(pkg.basePriceMinor).asDouble.toString()
                days = pkg.validityDays.toString()
            }
            isLoading = false
        }
    }

    // Kaydetme sonucu **her zaman** bildirilir; önceden hata sessizce yutuluyor
    // ve ekran başarılıymış gibi kapanıyordu.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PackageEvent.Saved -> onNavigateBack()
                is PackageEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val sessionLabel = if (unlimited) "Sınırsız" else sessionCount.ifBlank { "0" }
    val generatedName = remember(sessionLabel, type, category) {
        "$sessionLabel - ${type.labelTr()} - ${category.labelTr()}"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (packageId == null) "Yeni Paket Ekle" else "Paketi Düzenle") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Paket Adı (Otomatik)", style = MaterialTheme.typography.labelSmall)
                        Text(
                            generatedName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sınırsız (Abonman)", modifier = Modifier.weight(1f))
                    Switch(checked = unlimited, onCheckedChange = { unlimited = it })
                }

                if (!unlimited) {
                    OutlinedTextField(
                        value = sessionCount,
                        onValueChange = { sessionCount = it },
                        label = { Text("Seans Sayısı") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Text("Paket Türü")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrainingType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.labelTr()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Kategori")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PackageCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.labelTr()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Baz Fiyat (TL)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("Geçerlilik Süresi (Gün)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                val canSave = price.toDoubleOrNull() != null &&
                    (days.toIntOrNull() ?: 0) > 0 &&
                    (unlimited || (sessionCount.toIntOrNull() ?: 0) > 0)

                Button(
                    onClick = {
                        viewModel.savePackage(
                            packageId = packageId,
                            name = generatedName,
                            type = type,
                            category = category,
                            basePrice = price.toDoubleOrNull() ?: 0.0,
                            validityDays = days.toIntOrNull() ?: 30,
                            sessionCount = if (unlimited) null else sessionCount.toIntOrNull(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text(if (packageId == null) "Kaydet" else "Güncelle")
                }
            }
        }
    }
}
