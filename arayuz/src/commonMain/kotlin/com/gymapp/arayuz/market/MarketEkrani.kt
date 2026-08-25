package com.gymapp.arayuz.market

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.domain.Decimals
import com.gymapp.domain.Money
import com.gymapp.domain.ParaBicimi
import com.gymapp.domain.PaymentState
import com.gymapp.domain.labelTr

/** Bu adedin altındaki stok kartta uyarı rengiyle çiziliyor. */
private const val LOW_STOCK_THRESHOLD = 5

/**
 * Market / POS — `app`'teki `MarketScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Yetki ürün tanımıyla sınırlı
 * [urunYonetebilir] yalnızca ürün ekleme/düzenleme/silme girişlerini
 * etkiliyor; satış (sepet, ödeme) herkese açık. Eğitmen satış yapabilmeli
 * ama ürünün fiyatını değiştirememeli — ayrım bilinçli.
 *
 * ### Sepet tutarı ekranda hesaplanmıyor
 * `MarketDurumu.cartTotal` / `cartDiscount` / `cartFinal` durumdan
 * türetiliyor. Ekran bu hesabı iki ayrı yerde `Double` ile tekrarlıyordu;
 * gerçek tutar ise kuruş tam sayısıyla hesaplanıyor. İki ayrı aritmetik,
 * gösterilen ile çekilen tutarın sapması demekti.
 *
 * ### Ödeme sayfası dışarıdan kapanıyor
 * Sipariş BAŞARILI olduğunda kapanıyor, reddedildiğinde açık kalıyor —
 * başarısız sipariş sessizce "başarılı" gibi görünmesin diye. Bu kararı
 * ekran veremez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketEkrani(
    durum: MarketDurumu,
    urunYonetebilir: Boolean,
    urunEklemeAcik: Boolean,
    duzenlenenUrun: ProductEntity?,
    odemeAcik: Boolean,
    onGeri: () -> Unit,
    onSiparisGecmisi: () -> Unit,
    onUrunEklemeAc: () -> Unit,
    onUrunEklemeKapat: () -> Unit,
    onUrunDuzenle: (ProductEntity?) -> Unit,
    onSepeteEkle: (ProductEntity) -> Unit,
    onSepettenCikar: (String) -> Unit,
    onUrunSil: (String) -> Unit,
    onUrunKaydet: (urunId: String?, ad: String, kategori: String, fiyat: Double, stok: Int) -> Unit,
    onOdemeAc: () -> Unit,
    onOdemeKapat: () -> Unit,
    onUyeSec: (String?) -> Unit,
    onOdemeTuru: (String) -> Unit,
    onOdemeDurumu: (PaymentState) -> Unit,
    onTeslimDurumu: (String) -> Unit,
    onIskonto: (String) -> Unit,
    onNotlar: (String) -> Unit,
    onOdemeOnayla: () -> Unit,
    snackbarDurumu: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarDurumu) },
        topBar = {
            TopAppBar(
                title = { Text("Market / POS", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = onSiparisGecmisi) {
                        Icon(Icons.Default.History, contentDescription = "Sipariş Geçmişi")
                    }
                    // Ürün tanımı yetkisi olmayanda bu giriş hiç çizilmiyor.
                    // Satış (sepet, ödeme) etkilenmiyor.
                    if (urunYonetebilir) {
                        IconButton(onClick = onUrunEklemeAc) {
                            Icon(Icons.Default.AddBusiness, contentDescription = "Ürün Yönetimi")
                        }
                    }
                },
            )
        },
    ) { bosluk ->
        Box(modifier = Modifier.fillMaxSize().padding(bosluk)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(durum.products) { urun ->
                    UrunKarti(
                        product = urun,
                        onHand = durum.stockOf(urun.id),
                        cartCount = durum.cart[urun.id] ?: 0,
                        onAdd = { onSepeteEkle(urun) },
                        onRemove = { onSepettenCikar(urun.id) },
                        onEdit = { onUrunDuzenle(urun) },
                        onDelete = { onUrunSil(urun.id) },
                        canManage = urunYonetebilir,
                    )
                }
            }

            if (durum.cart.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ) {
                    val adet = durum.cart.values.sum()
                    Row(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("$adet Ürün", style = MaterialTheme.typography.labelMedium)
                            Text(
                                ParaBicimi.tl(durum.cartTotal),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Button(
                            onClick = onOdemeAc,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp).padding(start = 16.dp),
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

    if (urunEklemeAcik) {
        UrunDiyalogu(
            onDismiss = onUrunEklemeKapat,
            onConfirm = { ad, kategori, fiyat, stok -> onUrunKaydet(null, ad, kategori, fiyat, stok) },
        )
    }

    duzenlenenUrun?.let { urun ->
        UrunDiyalogu(
            product = urun,
            currentStock = durum.stockOf(urun.id),
            onDismiss = { onUrunDuzenle(null) },
            onConfirm = { ad, kategori, fiyat, stok -> onUrunKaydet(urun.id, ad, kategori, fiyat, stok) },
        )
    }

    if (odemeAcik) {
        ModalBottomSheet(
            onDismissRequest = onOdemeKapat,
            sheetState = rememberModalBottomSheetState(),
        ) {
            OdemeIcerigi(
                durum = durum,
                onMemberSelect = onUyeSec,
                onPaymentTypeSelect = onOdemeTuru,
                onPaymentStatusSelect = onOdemeDurumu,
                onDeliveryStatusSelect = onTeslimDurumu,
                onDiscountChange = onIskonto,
                onNotesChange = onNotlar,
                // Sheet burada kapatılmıyor: sonuç olayla gelince kapanıyor,
                // böylece başarısız sipariş sessizce "başarılı" gibi görünmüyor.
                onConfirm = onOdemeOnayla,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OdemeIcerigi(
    durum: MarketDurumu,
    onMemberSelect: (String?) -> Unit,
    onPaymentTypeSelect: (String) -> Unit,
    onPaymentStatusSelect: (PaymentState) -> Unit,
    onDeliveryStatusSelect: (String) -> Unit,
    onDiscountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    var memberExpanded by remember { mutableStateOf(false) }
    var discountText by remember { mutableStateOf(if (durum.discount > 0) durum.discount.toString() else "") }

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
            val selectedMemberName = durum.members.find { it.id == durum.selectedMemberId }?.fullName ?: "Misafir (Guest)"
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
                durum.members.forEach { member ->
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
                    selected = durum.paymentType == valStr,
                    onClick = { onPaymentTypeSelect(valStr) },
                    label = { Text(label) }
                )
            }
        }

        // Ödeme Durumu
        Text("Ödeme Durumu", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Sabit metin listesi yerine enum: yeni bir durum eklendiğinde
            // ekran kendiliğinden güncel kalıyor ve etiket tek yerden geliyor.
            // `secenek`, ekranın `durum` parametresiyle çakışmasın diye:
            // lambda değişkeni de `durum` olsaydı hangisinin okunduğu
            // görünmez biçimde değişirdi.
            PaymentState.entries.forEach { secenek ->
                FilterChip(
                    selected = durum.paymentStatus == secenek,
                    onClick = { onPaymentStatusSelect(secenek) },
                    label = { Text(secenek.labelTr()) }
                )
            }
        }

        // Teslimat Durumu
        Text("Teslimat Durumu", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("PRE_DELIVERY" to "Teslimden Önce", "POST_DELIVERY" to "Teslimden Sonra").forEach { (valStr, label) ->
                FilterChip(
                    selected = durum.deliveryStatus == valStr,
                    onClick = { onDeliveryStatusSelect(valStr) },
                    label = { Text(label) }
                )
            }
        }

        // Sipariş notu alanı formda **yoktu**: durum, işleyici ve kayda yazma
        // (`orders.notes`) zaten vardı, yalnızca girilecek kutu eklenmemişti.
        // Her sipariş notsuz kaydediliyordu.
        OutlinedTextField(
            value = durum.notes,
            onValueChange = onNotesChange,
            label = { Text("Sipariş Notu") },
            placeholder = { Text("Örn: kasadan teslim alınacak") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text("Ara Toplam: ${ParaBicimi.tl(durum.cartTotal)}", style = MaterialTheme.typography.bodyMedium)
            if (durum.cartDiscount.isPositive) {
                Text("İndirim: -${ParaBicimi.tl(durum.cartDiscount)}", style = MaterialTheme.typography.bodyMedium, color = Color.Red)
            }
            // Girilen iskonto sepeti aşıyorsa sessizce kırpmak yerine söyleniyor:
            // kasiyer 80 yazıp 50 uygulandığını görmeliyse, görmeli.
            if (Money.ofMajor(durum.discount) > durum.cartDiscount) {
                Text(
                    "Girilen indirim sepeti aşıyor; ${ParaBicimi.tl(durum.cartDiscount)} uygulandı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red,
                )
            }
            Text("Genel Toplam: ${ParaBicimi.tl(durum.cartFinal)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        Button(
            onClick = onConfirm,
            enabled = !durum.isCheckingOut,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (durum.isCheckingOut) {
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
private fun UrunKarti(
    product: ProductEntity,
    /** Eldeki stok — ürün satırındaki sayaçtan değil, hareket toplamından gelir. */
    onHand: Int,
    cartCount: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    /**
     * Ürün tanımı düzenlenebilir mi?
     *
     * `false` ise uzun basma menüsü hiç açılmıyor. Menüyü açıp içindeki
     * seçenekleri gizlemek de olurdu ama boş bir menü "bozuk" görünürdü.
     */
    canManage: Boolean,
) {
    val isLowStock = onHand < LOW_STOCK_THRESHOLD
    var showMenu by remember { mutableStateOf(false) }

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        border = if (isLowStock) CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFFFF9800))) else CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .combinedClickable(
                onClick = onAdd,
                onLongClick = { if (canManage) showMenu = true }
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
private fun UrunDiyalogu(
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
                    val p = Decimals.parseOrDefault(price)
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
