package com.gymapp.presentation.packages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.PackageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageListScreen(
    onNavigateToAdd: (Long?) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PackageViewModel = hiltViewModel()
) {
    val packages by viewModel.packages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paket Yönetimi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToAdd(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Paket Ekle")
            }
        }
    ) { padding ->
        if (packages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Henüz paket tanımlanmamış.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(packages) { pkg ->
                    PackageItem(
                        pkg = pkg, 
                        onDelete = { viewModel.deletePackage(pkg) },
                        onClick = { onNavigateToAdd(pkg.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun PackageItem(pkg: PackageEntity, onDelete: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pkg.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${pkg.basePrice} TL • ${pkg.validityDays} Gün",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (pkg.type == "ABONMAN") "Sınırsız Seans" else "${pkg.sessionCount} Seans",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
            }
        }
    }
}
