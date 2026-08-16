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
import org.koin.androidx.compose.koinViewModel
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.presentation.common.ReadOnlyNotice
import com.gymapp.domain.Money
import com.gymapp.domain.labelTr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageListScreen(
    onNavigateToAdd: (String?) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PackageViewModel = koinViewModel()
) {
    val packages by viewModel.packages.collectAsState()
    val canWrite by viewModel.canWrite.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Silme sonucu artık her durumda bildiriliyor. Önceden `deletePackage`
    // fırlatıyor ve sonuç hiçbir yerde okunmuyordu: başarısız silme başarılı
    // silmeden ayırt edilemiyor, üstelik uygulama kapanıyordu.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PackageEvent.Saved -> Unit // kaydetme başka ekranda
                is PackageEvent.Deleted -> snackbarHostState.showSnackbar("Paket silindi.")
                is PackageEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Yetkisi olmayanda düğme hiç çizilmiyor. Pasif (disabled) bırakmak
            // da bir seçenekti; gizlemek tercih edildi çünkü ekranın altındaki
            // şerit sebebi zaten söylüyor ve pasif bir düğme "belki açılır"
            // izlenimi verirdi.
            if (canWrite) {
                FloatingActionButton(onClick = { onNavigateToAdd(null) }) {
                    Icon(Icons.Default.Add, contentDescription = "Paket Ekle")
                }
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
                if (!canWrite) {
                    item {
                        ReadOnlyNotice(
                            "Paketleri görüntüleyebilirsiniz. Değiştirmek salon " +
                                "sahibi ve yönetici yetkisi gerektiriyor."
                        )
                    }
                }
                items(packages) { pkg ->
                    PackageItem(
                        pkg = pkg,
                        // Yetki yoksa satır düzenleme ekranını açmıyor ve silme
                        // simgesi çizilmiyor.
                        onDelete = if (canWrite) ({ viewModel.deletePackage(pkg.id) }) else null,
                        onClick = { if (canWrite) onNavigateToAdd(pkg.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun PackageItem(pkg: PackageEntity, onDelete: (() -> Unit)?, onClick: () -> Unit) {
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
                    text = "₺${Money(pkg.basePriceMinor)} • ${pkg.validityDays} Gün",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    // Sınırsızlık `null` seans kotasından anlaşılır; `-1` sentinel'i kalktı.
                    text = pkg.sessionCount?.let { "$it Seans" } ?: "Sınırsız Seans",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "${pkg.type.labelTr()} • ${pkg.category.labelTr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
                }
            }
        }
    }
}
