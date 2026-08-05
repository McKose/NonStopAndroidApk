package com.gymapp.presentation.market

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.domain.Money
import java.util.Locale

private const val LOW_STOCK_THRESHOLD = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOrders: () -> Unit,
    viewModel: MarketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showCheckoutSheet by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Sipariş sonucu her durumda kullanıcıya bildirilir.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MarketEvent.OrderCompleted -> {
                    showCheckoutSheet = false
                    snackbarHostState.showSnackbar("Sipariş tamamlandı (#${event.orderId.take(8)})")
                }
                is MarketEvent.Failed ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Market / POS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToOrders) {
                        Icon(Icons.Default.History, contentDescription = "Sipariş Geçmişi")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.AddBusiness, contentDescription = "Ürün Yönetimi")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.products) { product ->
                    ProductGridItem(
                        product = product,
                        onHand = uiState.stockOf(product.id),
                        cartCount = uiState.cart[product.id] ?: 0,
                        onAdd = { viewModel.addToCart(product) },
                        onRemove = { viewModel.removeFromCart(product.id) },
                        onEdit = { productToEdit = product },
                        onDelete = { viewModel.deleteProduct(product.id) }
                    )
                }
            }

            if (uiState.cart.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    val totalItems = uiState.cart.values.sum()
                    val totalPrice = uiState.cart.map { (id, qty) -> 
                        Money(uiState.products.find { it.id == id }?.priceMinor ?: 0L).asDouble * qty 
                    }.sum()

                    Row(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("$totalItems Ürün", style = MaterialTheme.typography.labelMedium)
                            Text("₺${String.format(Locale.getDefault(), "%.2f", totalPrice)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { showCheckoutSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp).padding(start = 16.dp)
                        ) {
                            Text("ÖDEME AL", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProductDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, category, price, stock ->
                viewModel.saveProduct(name = name, category = category, price = price, stock = stock)
                showAddDialog = false
            }
        )
    }

    if (productToEdit != null) {
        AddProductDialog(
            product = productToEdit,
            currentStock = uiState.stockOf(productToEdit!!.id),
            onDismiss = { productToEdit = null },
            onConfirm = { name, category, price, stock ->
                viewModel.saveProduct(
                    productId = productToEdit!!.id,
                    name = name,
                    category = category,
                    price = price,
                    stock = stock
                )
                productToEdit = null
            }
        )
    }

    if (showCheckoutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCheckoutSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            CheckoutContent(
                uiState = uiState,
                onMemberSelect = viewModel::selectMember,
                onPaymentTypeSelect = viewModel::setPaymentType,
                onPaymentStatusSelect = viewModel::setPaymentStatus,
                onDeliveryStatusSelect = viewModel::setDeliveryStatus,
                onDiscountChange = { viewModel.setDiscount(it.toDoubleOrNull() ?: 0.0) },
                // Sheet burada kapatılmaz: sonuç OrderCompleted olayıyla gelince kapanır,
                // böylece başarısız sipariş sessizce "başarılı" gibi görünmez.
                onConfirm = viewModel::checkout
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutContent(
    uiState: MarketUiState,
    onMemberSelect: (String?) -> Unit,
    onPaymentTypeSelect: (String) -> Unit,
    onPaymentStatusSelect: (String) -> Unit,
    onDeliveryStatusSelect: (String) -> Unit,
    onDiscountChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    var memberExpanded by remember { mutableStateOf(false) }
    var discountText by remember { mutableStateOf(if (uiState.discount > 0) uiState.discount.toString() else "") }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Sipariş Detayları", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Üye Seçimi
        ExposedDropdownMenuBox(
            expanded = memberExpanded,
            onExpandedChange = { memberExpanded = it }
        ) {
            val selectedMemberName = uiState.members.find { it.id == uiState.selectedMemberId }?.fullName ?: "Misafir (Guest)"
            OutlinedTextField(
                value = selectedMemberName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Müşteri / Üye") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(memberExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = memberExpanded,
                onDismissRequest = { memberExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Misafir (Guest)") },
                    onClick = {
                        onMemberSelect(null)
                        memberExpanded = false
                    }
                )
                uiState.members.forEach { member ->
                    DropdownMenuItem(
                        text = { Text(member.fullName) },
                        onClick = {
                            onMemberSelect(member.id)
                            memberExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = discountText,
            onValueChange = {
                discountText = it
                onDiscountChange(it)
            },
            label = { Text("İndirim Tutarı (TL)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        // Ödeme Yöntemi
        Text("Ödeme Yöntemi", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CASH" to "Nakit", "CARD" to "Kart").forEach { (valStr, label) ->
                FilterChip(
                    selected = uiState.paymentType == valStr,
                    onClick = { onPaymentTypeSelect(valStr) },
                    label = { Text(label) }
                )
            }
        }

        // Ödeme Durumu
        Text("Ödeme Durumu", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("PAID" to "Ödendi", "PENDING" to "Ödenmedi").forEach { (valStr, label) ->
                FilterChip(
                    selected = uiState.paymentStatus == valStr,
                    onClick = { onPaymentStatusSelect(valStr) },
                    label = { Text(label) }
                )
            }
        }

        // Teslimat Durumu
        Text("Teslimat Durumu", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("PRE_DELIVERY" to "Teslimden Önce", "POST_DELIVERY" to "Teslimden Sonra").forEach { (valStr, label) ->
                FilterChip(
                    selected = uiState.deliveryStatus == valStr,
                    onClick = { onDeliveryStatusSelect(valStr) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val totalPrice = uiState.cart.map { (id, qty) -> 
            Money(uiState.products.find { it.id == id }?.priceMinor ?: 0L).asDouble * qty 
        }.sum()
        val finalPrice = totalPrice - uiState.discount

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text("Ara Toplam: ₺${String.format(Locale.getDefault(), "%.2f", totalPrice)}", style = MaterialTheme.typography.bodyMedium)
            if (uiState.discount > 0) {
                Text("İndirim: -₺${String.format(Locale.getDefault(), "%.2f", uiState.discount)}", style = MaterialTheme.typography.bodyMedium, color = Color.Red)
            }
            Text("Genel Toplam: ₺${String.format(Locale.getDefault(), "%.2f", finalPrice)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        Button(
            onClick = onConfirm,
            enabled = !uiState.isCheckingOut,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (uiState.isCheckingOut) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else Text("SİPARİŞİ ONAYLA")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProductGridItem(
    product: ProductEntity,
    /** Eldeki stok — ürün satırındaki sayaçtan değil, hareket toplamından gelir. */
    onHand: Int,
    cartCount: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = onHand < LOW_STOCK_THRESHOLD
    var showMenu by remember { mutableStateOf(false) }

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        border = if (isLowStock) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFF9800))) else CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .combinedClickable(
                onClick = onAdd,
                onLongClick = { showMenu = true }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.height(12.dp))
                Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("Stok: $onHand", style = MaterialTheme.typography.bodySmall, color = if (isLowStock) Color.Red else Color.Gray)
                Spacer(Modifier.weight(1f))
                Text("₺${Money(product.priceMinor)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Düzenle") },
                    onClick = {
                        onEdit()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Sil", color = Color.Red) },
                    onClick = {
                        onDelete()
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
            }

            if (cartCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(bottomStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        IconButton(
                            onClick = { onRemove() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Azalt", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = cartCount.toString(),
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductDialog(
    product: ProductEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Int) -> Unit,
    /** Düzenlemede mevcut eldeki stok; kaydedilen değer düzeltme hareketine dönüşür. */
    currentStock: Int = 0,
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var category by remember { mutableStateOf(product?.category ?: "Market Ürünleri") }
    var price by remember { mutableStateOf(product?.let { Money(it.priceMinor).asDouble.toString() } ?: "") }
    var stock by remember { mutableStateOf(if (product != null) currentStock.toString() else "") }
    var categoryExpanded by remember { mutableStateOf(false) }
    val categories = listOf("Market Ürünleri", "Supplement", "Ekipman")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Yeni Ürün Ekle" else "Ürünü Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ürün Adı") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Fiyat") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Stok Adedi") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = price.toDoubleOrNull() ?: 0.0
                    val s = stock.toIntOrNull() ?: 0
                    if (name.isNotBlank()) {
                        onConfirm(name, category, p, s)
                    }
                }
            ) {
                Text(if (product == null) "Ekle" else "Güncelle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
