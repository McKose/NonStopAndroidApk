package com.gymapp.presentation.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketUiState(
    val products: List<ProductEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val cart: Map<Long, Int> = emptyMap(),
    val selectedMemberId: Long? = null,
    val paymentType: String = "CASH",
    val paymentStatus: String = "PAID",
    val deliveryStatus: String = "POST_DELIVERY",
    val discount: Double = 0.0,
    val notes: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

private data class CheckoutOptions(
    val paymentType: String,
    val paymentStatus: String,
    val deliveryStatus: String,
    val notes: String,
    val discount: Double,
    val isSuccess: Boolean,
    val error: String?
)

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val memberRepository: MemberRepository
) : ViewModel() {

    private val _cart = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val _selectedMemberId = MutableStateFlow<Long?>(null)
    private val _paymentType = MutableStateFlow("CASH")
    private val _paymentStatus = MutableStateFlow("PAID")
    private val _deliveryStatus = MutableStateFlow("POST_DELIVERY")
    private val _notes = MutableStateFlow("")
    private val _isSuccess = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _discount = MutableStateFlow(0.0)

    private val checkoutOptions: Flow<CheckoutOptions> = combine(
        _paymentType,
        _paymentStatus,
        _deliveryStatus,
        _notes,
        _discount,
        _isSuccess,
        _error
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        CheckoutOptions(
            paymentType = values[0] as String,
            paymentStatus = values[1] as String,
            deliveryStatus = values[2] as String,
            notes = values[3] as String,
            discount = values[4] as Double,
            isSuccess = values[5] as Boolean,
            error = values[6] as String?
        )
    }

    val uiState: StateFlow<MarketUiState> = combine(
        repository.getAllProducts(),
        memberRepository.getAllMembers(),
        _cart,
        _selectedMemberId,
        checkoutOptions
    ) { products, members, cart, memberId, opts ->
        MarketUiState(
            products = products,
            members = members,
            cart = cart,
            selectedMemberId = memberId,
            paymentType = opts.paymentType,
            paymentStatus = opts.paymentStatus,
            deliveryStatus = opts.deliveryStatus,
            notes = opts.notes,
            discount = opts.discount,
            isSuccess = opts.isSuccess,
            error = opts.error,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MarketUiState(isLoading = true)
    )

    fun addToCart(product: ProductEntity) {
        val current = _cart.value.toMutableMap()
        val count = current.getOrDefault(product.id, 0)
        if (count < product.stockCount) {
            current[product.id] = count + 1
            _cart.value = current
        }
    }

    fun removeFromCart(productId: Long) {
        val current = _cart.value.toMutableMap()
        val count = current.getOrDefault(productId, 0)
        if (count > 1) {
            current[productId] = count - 1
        } else {
            current.remove(productId)
        }
        _cart.value = current
    }

    fun selectMember(memberId: Long?) {
        _selectedMemberId.value = memberId
    }

    fun setPaymentType(type: String) {
        _paymentType.value = type
    }

    fun setPaymentStatus(status: String) {
        _paymentStatus.value = status
    }

    fun setDeliveryStatus(status: String) {
        _deliveryStatus.value = status
    }

    fun setDiscount(amount: Double) {
        _discount.value = amount
    }

    fun setNotes(notes: String) {
        _notes.value = notes
    }

    fun checkout() {
        val state = uiState.value
        if (state.cart.isEmpty()) {
            _error.value = "Sepet boş"
            return
        }

        viewModelScope.launch {
            _error.value = null
            val result = repository.processOrder(
                memberId = state.selectedMemberId,
                cartItems = state.cart,
                products = state.products,
                paymentType = state.paymentType,
                paymentStatus = state.paymentStatus,
                deliveryStatus = state.deliveryStatus,
                discount = state.discount,
                notes = state.notes
            )

            result.fold(
                onSuccess = {
                    _cart.value = emptyMap()
                    _discount.value = 0.0
                    _isSuccess.value = true
                },
                onFailure = {
                    _error.value = it.message
                }
            )
        }
    }

    fun resetStatus() {
        _isSuccess.value = false
        _error.value = null
    }

    fun addProduct(id: Long = 0, name: String, category: String, price: Double, stock: Int) {
        viewModelScope.launch {
            repository.saveProduct(
                ProductEntity(
                    id = id,
                    name = name,
                    category = category,
                    price = price,
                    stockCount = stock
                )
            )
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }
}
