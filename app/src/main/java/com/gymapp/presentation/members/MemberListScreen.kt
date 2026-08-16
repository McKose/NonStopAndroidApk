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
import com.gymapp.data.access.AppDestination
import com.gymapp.presentation.common.ReadOnlyNotice
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.domain.Membership
import com.gymapp.domain.MembershipState
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentState
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Tahsilat sonucu artık bildiriliyor: `markAsPaid` sonucunu yutuyordu, yani
    // reddedilen bir tahsilat başarılı olandan ayırt edilemiyordu.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MemberEvent.Saved -> snackbarHostState.showSnackbar(event.message)
                is MemberEvent.Failed -> snackbarHostState.showSnackbar(event.message)
                is MemberEvent.Deleted -> Unit // silme detay ekranından yapılıyor
            }
        }
    }

    // Tahsilat diyaloğu: hangi üye için açıldığı ve o üyenin kalan borcu.
    var tahsilatUyesi by remember { mutableStateOf<MemberEntity?>(null) }
    var tahsilatBorcu by remember { mutableStateOf<Money?>(null) }
    tahsilatUyesi?.let { uye ->
        LaunchedEffect(uye.id) { tahsilatBorcu = viewModel.outstandingBalance(uye.id) }
        tahsilatBorcu?.let { borc ->
            PaymentDialog(
                memberName = uye.fullName,
                outstanding = borc,
                onConfirm = { tutar ->
                    tahsilatUyesi = null
                    tahsilatBorcu = null
                    viewModel.markAsPaid(uye.id, tutar)
                },
                onDismiss = {
                    tahsilatUyesi = null
                    tahsilatBorcu = null
                },
            )
        }
    }

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
                // Çekmece artık rolün göremediği hedefi çizmiyor.
                //
                // Önceden bu liste koşulsuzdu: pano eğitmene Finans'ı
                // gizliyordu ama aynı ekran buradan iki dokunuşla açılıyordu.
                // Yani kısıt gerçek bir kısıt değil, yalnızca bir ekranda
                // görünmeyen bir düğmeydi. Görünürlük kararı artık
                // `AppDestination` içinde, tek yerde.
                if (uiState.gorebilir(AppDestination.FINANCE)) {
                    NavigationDrawerItem(
                        label = { Text("Finans") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToFinance()
                        },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) }
                    )
                }
                if (uiState.gorebilir(AppDestination.PACKAGES)) {
                    NavigationDrawerItem(
                        label = { Text("Ders Paketleri") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToPackages()
                        },
                        icon = { Icon(Icons.Default.Inventory, contentDescription = null) }
                    )
                }
                if (uiState.gorebilir(AppDestination.MARKET)) {
                    NavigationDrawerItem(
                        label = { Text("Market") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToMarket()
                        },
                        icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) }
                    )
                }
                if (uiState.gorebilir(AppDestination.SETTINGS)) {
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
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
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

                // Kapsam görünür bir seçim: hangi listeye bakıldığı ekranda
                // yazıyor ve tek dokunuşla değişiyor. Sessiz süzme, eğitmenin
                // yeni kaydettiği (henüz randevusu olmayan) üyeyi bulamaması
                // demek olurdu.
                if (uiState.kapsamSecilebilir) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.kapsam == MemberScope.MINE,
                            onClick = { viewModel.onKapsamChange(MemberScope.MINE) },
                            label = { Text("Üyelerim") },
                        )
                        FilterChip(
                            selected = uiState.kapsam == MemberScope.ALL,
                            onClick = { viewModel.onKapsamChange(MemberScope.ALL) },
                            label = { Text("Tüm üyeler") },
                        )
                    }
                }

                // Bağlantısı olmayan eğitmende "üyelerim" tanımsız; liste
                // süzülmüyor ve sebebi yazıyor.
                if (uiState.personelBaglantisiYok) {
                    ReadOnlyNotice(
                        text = "Hesabınız bir personel kaydına bağlı olmadığı için " +
                            "\"üyelerim\" ayrımı yapılamıyor; salonun tamamı listeleniyor.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.members.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Boş listenin sebebi kapsam olabilir; "üye bulunamadı"
                        // demek kullanıcıyı salonda hiç üye yok sanmaya iterdi.
                        Text(
                            if (uiState.kapsam == MemberScope.MINE) {
                                "Size atanmış randevusu olan üye yok. " +
                                    "Salonun tamamı için \"Tüm üyeler\" seçin."
                            } else {
                                "Üye bulunamadı."
                            }
                        )
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
                                onPaymentReceived = { tahsilatUyesi = member }
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
                    if (member.paymentStatus == PaymentState.PENDING) {
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
