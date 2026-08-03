package com.gymapp.presentation.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketUiState(
    val products: List<ProductEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val cart: Map<Long, Int> = emptyMap(), // ProductId -> Quantity
    val selectedMemberId: Long? = null,
    val paymentType: String = "CASH",
    val paymentStatus: String = "PAID",
    val deliveryStatus: String = "POST_DELIVERY",
    val discount: Double = 0.0,
    val notes: String = "",
    /** Ürün kimliği → eldeki stok (hareketlerin toplamı). */
    val stockByProduct: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isCheckingOut: Boolean = false,
) {
    fun stockOf(productId: Long): Int = stockByProduct[productId] ?: 0
}

/** Bir kez tüketilen kullanıcı bildirimleri (Snackbar). */
sealed interface MarketEvent {
    data class OrderCompleted(val orderId: Long) : MarketEvent
    data class Failed(val message: String) : MarketEvent
}

/** Kullanıcının form üzerinde değiştirdiği alanlar tek bir state'te toplanır. */
private data class MarketForm(
    val cart: Map<Long, Int> = emptyMap(),
    val selectedMemberId: Long? = null,
    val paymentType: String = "CASH",
    val paymentStatus: String = "PAID",
    val deliveryStatus: String = "POST_DELIVERY",
    val discount: Double = 0.0,
    val notes: String = "",
    val isCheckingOut: Boolean = false,
)

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val memberRepository: MemberRepository
) : ViewModel() {

    private val _form = MutableStateFlow(MarketForm())

    /**
     * Önceki sürümde 11 ayrı akış `Array<Any?>` üzerinden `combine` edilip tek tek
     * cast'leniyordu; dizideki sıra değişince çalışma anında `ClassCastException` riski vardı.
     * Form alanları tek bir veri sınıfında toplandığı için artık tip güvenli.
     */
    val uiState: StateFlow<MarketUiState> = combine(
        repository.getAllProducts(),
        repository.observeStockByProduct(),
        memberRepository.getAllMembers(),
        _form,
    ) { products, stock, members, form ->
        MarketUiState(
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
        initialValue = MarketUiState(isLoading = true)
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

    fun removeFromCart(productId: Long) {
        _form.update { form ->
            val count = form.cart[productId] ?: return@update form
            val cart = if (count > 1) form.cart + (productId to count - 1) else form.cart - productId
            form.copy(cart = cart)
        }
    }

    fun selectMember(memberId: Long?) = _form.update { it.copy(selectedMemberId = memberId) }
    fun setPaymentType(type: String) = _form.update { it.copy(paymentType = type) }
    fun setPaymentStatus(status: String) = _form.update { it.copy(paymentStatus = status) }
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
                paymentType = form.paymentType,
                paymentStatus = form.paymentStatus,
                deliveryStatus = form.deliveryStatus,
                discount = form.discount,
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

    fun addProduct(id: Long = 0, name: String, category: String, price: Double, stock: Int) {
        viewModelScope.launch {
            repository.saveProduct(
                product = ProductEntity(
                    id = id,
                    name = name.trim(),
                    category = category.trim(),
                    price = price.coerceAtLeast(0.0),
                    // Sayaç kolonu geçiş süresince duruyor ama artık doğruluk kaynağı değil;
                    // eldeki stok hareket toplamından geliyor.
                    stockCount = 0,
                ),
                desiredStock = stock.coerceAtLeast(0),
            ).onFailure { _events.send(MarketEvent.Failed(it.message ?: "Ürün kaydedilemedi.")) }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }
}
