package com.gymapp.arayuz.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.auth.CurrentUser
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.ProductRepository
import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.DeliveryStatus
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Bir kez tüketilen kullanıcı bildirimleri (Snackbar). */
sealed interface MarketEvent {
    data class OrderCompleted(val orderId: String) : MarketEvent
    data object ProductSaved : MarketEvent
    data object ProductDeleted : MarketEvent
    data class Failed(val message: String) : MarketEvent
}

/** Kullanıcının form üzerinde değiştirdiği alanlar tek bir state'te toplanır. */
private data class MarketForm(
    val cart: Map<String, Int> = emptyMap(),
    val selectedMemberId: String? = null,
    val paymentType: String = "CASH",
    val paymentStatus: PaymentState = PaymentState.PAID,
    val deliveryStatus: String = "POST_DELIVERY",
    val discount: Double = 0.0,
    val notes: String = "",
    val isCheckingOut: Boolean = false,
)

class MarketViewModel(
    private val repository: ProductRepository,
    private val memberRepository: MemberRepository,
    currentUser: CurrentUser,
) : ViewModel() {

    /**
     * Bu kullanıcı ürün tanımı (fiyat listesi) yazabilir mi?
     *
     * Yalnızca **ürün tanımını** kapsıyor; satış değil. Sunucu kuralı da aynı
     * ayrımı yapıyor: `products` salon sahibi ve yöneticiye açık, `orders` ile
     * `stock_movements` üç role de. Eğitmen satış yapabilmeli — satış zaten
     * işinin kendisi — ama ürünün fiyatını değiştirememeli.
     *
     * Tepkili okunuyor (bkz. `CurrentUser`); toplayan yokken de doğru olması
     * gerektiği için `Eagerly`, başlangıç değeri en dar yetki.
     */
    val canManageProducts: StateFlow<Boolean> = currentUser.role
        .map { SyncTable.PRODUCTS.isWritableBy(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _form = MutableStateFlow(MarketForm())

    /**
     * Önceki sürümde 11 ayrı akış `Array<Any?>` üzerinden `combine` edilip tek tek
     * cast'leniyordu; dizideki sıra değişince çalışma anında `ClassCastException` riski vardı.
     * Form alanları tek bir veri sınıfında toplandığı için artık tip güvenli.
     */
    val uiState: StateFlow<MarketDurumu> = combine(
        repository.getAllProducts(),
        repository.observeStockByProduct(),
        memberRepository.getAllMembers(),
        _form,
    ) { products, stock, members, form ->
        MarketDurumu(
            products = products,
            stockByProduct = stock,
            members = members,
            cart = form.cart,
            selectedMemberId = form.selectedMemberId,
            paymentType = form.paymentType,
            paymentStatus = form.paymentStatus,
            deliveryStatus = form.deliveryStatus,
            discount = form.discount,
            notes = form.notes,
            isLoading = false,
            isCheckingOut = form.isCheckingOut,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MarketDurumu(isLoading = true)
    )

    private val _events = Channel<MarketEvent>(Channel.BUFFERED)
    val events: Flow<MarketEvent> = _events.receiveAsFlow()

    fun addToCart(product: ProductEntity) {
        val onHand = uiState.value.stockOf(product.id)
        _form.update { form ->
            val count = form.cart[product.id] ?: 0
            if (count >= onHand) return@update form
            form.copy(cart = form.cart + (product.id to count + 1))
        }
    }

    fun removeFromCart(productId: String) {
        _form.update { form ->
            val count = form.cart[productId] ?: return@update form
            val cart = if (count > 1) form.cart + (productId to count - 1) else form.cart - productId
            form.copy(cart = cart)
        }
    }

    fun selectMember(memberId: String?) = _form.update { it.copy(selectedMemberId = memberId) }
    fun setPaymentType(type: String) = _form.update { it.copy(paymentType = type) }
    fun setPaymentStatus(status: PaymentState) = _form.update { it.copy(paymentStatus = status) }
    fun setDeliveryStatus(status: String) = _form.update { it.copy(deliveryStatus = status) }
    fun setNotes(notes: String) = _form.update { it.copy(notes = notes) }

    fun setDiscount(amount: Double) {
        val safe = if (amount.isFinite() && amount > 0.0) amount else 0.0
        _form.update { it.copy(discount = safe) }
    }

    /**
     * Siparişi tamamlar.
     *
     * Sonuç artık **her zaman** kullanıcıya bildirilir; önceki sürümde hata state'i hiçbir
     * ekranda okunmadığı için başarısız satış başarılı satıştan ayırt edilemiyordu.
     */
    fun checkout() {
        val form = _form.value
        if (form.cart.isEmpty()) {
            _events.trySend(MarketEvent.Failed("Sepet boş."))
            return
        }
        if (form.isCheckingOut) return // çift tıklama koruması

        viewModelScope.launch {
            _form.update { it.copy(isCheckingOut = true) }

            val result = repository.processOrder(
                memberId = form.selectedMemberId,
                cartItems = form.cart,
                paymentMethod = runCatching { PaymentMethod.valueOf(form.paymentType) }
                    .getOrDefault(PaymentMethod.CASH),
                paymentStatus = form.paymentStatus,
                deliveryStatus = runCatching { DeliveryStatus.valueOf(form.deliveryStatus) }
                    .getOrDefault(DeliveryStatus.POST_DELIVERY),
                discount = Money.ofMajor(form.discount),
                notes = form.notes.takeIf { it.isNotBlank() },
            )

            result.fold(
                onSuccess = { orderId ->
                    // Yalnızca başarıda sepeti temizle.
                    _form.value = MarketForm()
                    _events.send(MarketEvent.OrderCompleted(orderId))
                },
                onFailure = { error ->
                    _form.update { it.copy(isCheckingOut = false) }
                    _events.send(MarketEvent.Failed(error.message ?: "Sipariş tamamlanamadı."))
                }
            )
        }
    }

    fun saveProduct(
        productId: String? = null,
        name: String,
        category: String,
        price: Double,
        stock: Int,
    ) {
        // Ekranda düğme gizleniyor ama kontrol burada da var: gizlenmiş bir
        // düğme kural değil, yalnızca görüntü.
        if (!canManageProducts.value) {
            viewModelScope.launch { _events.send(MarketEvent.Failed(YETKI_YOK)) }
            return
        }

        viewModelScope.launch {
            repository.saveProduct(
                productId = productId,
                name = name,
                category = category,
                price = Money.ofMajor(price),
                desiredStock = stock,
            ).fold(
                // Başarı da bildiriliyor: önceden yalnızca hata gönderiliyordu,
                // yani kaydın gerçekten yazılıp yazılmadığı ekrandan anlaşılmıyordu.
                onSuccess = { _events.send(MarketEvent.ProductSaved) },
                onFailure = { _events.send(MarketEvent.Failed(it.message ?: "Ürün kaydedilemedi.")) },
            )
        }
    }

    fun deleteProduct(productId: String) {
        if (!canManageProducts.value) {
            viewModelScope.launch { _events.send(MarketEvent.Failed(YETKI_YOK)) }
            return
        }
        viewModelScope.launch {
            repository.deleteProduct(productId).fold(
                onSuccess = { _events.send(MarketEvent.ProductDeleted) },
                onFailure = { _events.send(MarketEvent.Failed(it.message ?: "Ürün silinemedi.")) },
            )
        }
    }

    private companion object {
        const val YETKI_YOK = "Ürün tanımlarını yalnızca salon sahibi ve yönetici değiştirebilir."
    }
}
