package com.gymapp.presentation.packages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPackageScreen(
    packageId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: PackageViewModel = hiltViewModel()
) {
    var sessionCount by remember { mutableStateOf("10") }
    var type by remember { mutableStateOf("Fitness") } // Fitness, Fonksiyonel, Reformer
    var category by remember { mutableStateOf("BİREYSEL") } // BİREYSEL, DÜET, GRUP
    var price by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("30") }
    var isLoading by remember { mutableStateOf(packageId != null) }

    val types = listOf("Fitness", "Fonksiyonel", "Reformer")
    val categories = listOf("BİREYSEL", "DÜET", "GRUP")

    LaunchedEffect(packageId) {
        if (packageId != null) {
            viewModel.getPackageById(packageId)?.let { pkg ->
                sessionCount = pkg.sessionCount.toString()
                type = pkg.type
                category = pkg.category
                price = pkg.basePrice.toString()
                days = pkg.validityDays.toString()
            }
            isLoading = false
        }
    }

    // Generated Name: [Session Count] - [Type] - [Category]
    val generatedName = remember(sessionCount, type, category) {
        "$sessionCount - $type - $category"
    }

    Scaffold(
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Paket Adı (Otomatik)", style = MaterialTheme.typography.labelSmall)
                        Text(generatedName, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = sessionCount,
                    onValueChange = { sessionCount = it },
                    label = { Text("Seans Sayısı") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Text("Paket Türü")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("Kategori")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
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

                Button(
                    onClick = {
                        if (price.isNotBlank()) {
                            viewModel.addPackage(
                                id = packageId ?: 0L,
                                name = generatedName,
                                type = type,
                                category = category,
                                basePrice = price.toDoubleOrNull() ?: 0.0,
                                validityDays = days.toIntOrNull() ?: 30,
                                sessionCount = sessionCount.toIntOrNull() ?: 0
                            )
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = price.isNotBlank()
                ) {
                    Text(if (packageId == null) "Kaydet" else "Güncelle")
                }
            }
        }
    }
}
