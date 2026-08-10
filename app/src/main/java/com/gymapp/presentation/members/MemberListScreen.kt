package com.gymapp.presentation.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.domain.Membership
import com.gymapp.domain.MembershipState
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.labelTr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberListScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToPackages: () -> Unit,
    onNavigateToFinance: () -> Unit,
    onNavigateToMarket: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRenew: (String) -> Unit,
    viewModel: MemberViewModel = koinViewModel()
) {
    val uiState by viewModel.listUiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                NavigationDrawerItem(
                    label = { Text("Üyeler") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Menu, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text("Finans") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToFinance()
                    },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text("Ders Paketleri") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToPackages()
                    },
                    icon = { Icon(Icons.Default.Inventory, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text("Market") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToMarket()
                    },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text("Ayarlar") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Üye Listesi") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onNavigateToRegister) {
                    Icon(Icons.Default.Add, contentDescription = "Üye Ekle")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("İsim veya telefon ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.members.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Üye bulunamadı.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.members) { member ->
                            MemberItem(
                                member = member,
                                onClick = { onNavigateToDetail(member.id) },
                                onRenewClick = { onNavigateToRenew(member.id) },
                                onPaymentReceived = { viewModel.markAsPaid(member.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemberItem(
    member: MemberEntity,
    onClick: () -> Unit,
    onRenewClick: () -> Unit,
    onPaymentReceived: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = member.fullName, style = MaterialTheme.typography.titleMedium)
                    if (member.paymentStatus != "PAID") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.padding(start = 8.dp).clickable { onPaymentReceived() }
                        ) {
                            Text(
                                "Ödeme Bekliyor",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Text(
                    text = PhoneNumber.formatForDisplay(member.phone),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Durum kayıtlı kolondan değil bitiş tarihinden türetiliyor; süresi
                // dolmuş üye artık "Aktif" görünmüyor. Paket kimliği (UUID) kullanıcıya
                // hiçbir şey ifade etmediği için gösterilmiyor.
                val state = Membership.stateOf(member.status, member.endDateMs, System.currentTimeMillis())
                Text(
                    text = "Durum: ${state.labelTr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state == MembershipState.ACTIVE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            
            Button(
                onClick = onRenewClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Yenile", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
