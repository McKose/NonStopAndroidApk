package com.gymapp.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.local.entity.PaymentType
import com.gymapp.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI State ────────────────────────────────────────────────────────────────

data class MemberListUiState(
    val members: List<MemberEntity> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)

data class RegisterFormState(
    val fullName: String      = "",
    val phone: String         = "",
    val email: String         = "",
    val paymentType: PaymentType = PaymentType.CASH,
    val installmentCount: Int = 1,
    val selectedPackage: PackageEntity? = null,
    val notes: String         = "",
    // Validation hataları
    val fullNameError: String?  = null,
    val phoneError: String?     = null,
    val isSubmitting: Boolean   = false,
    val submitSuccess: Boolean  = false,
    val submitError: String?    = null,
    // Hesaplanan fiyat (anlık önizleme için)
    val previewPrice: Double    = 0.0
)

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class MemberViewModel @Inject constructor(
    private val repository: MemberRepository
) : ViewModel() {

    // Liste ekranı state'i
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading   = MutableStateFlow(false)

    @OptIn(FlowPreview::class)
    val listUiState: StateFlow<MemberListUiState> =
        _searchQuery
            .debounce(300)
            .flatMapLatest { query ->
                if (query.isBlank()) repository.getAllMembers()
                else repository.searchMembers(query)
            }
            .combine(_isLoading) { members, loading ->
                MemberListUiState(
                    members     = members,
                    isLoading   = loading,
                    searchQuery = _searchQuery.value
                )
            }
            .stateIn(
                scope           = viewModelScope,
                started         = SharingStarted.WhileSubscribed(5_000),
                initialValue    = MemberListUiState(isLoading = true)
            )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // Kayıt formu state'i
    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    fun onFullNameChange(value: String) {
        _formState.update { it.copy(fullName = value, fullNameError = null) }
    }

    fun onPhoneChange(value: String) {
        _formState.update { it.copy(phone = value, phoneError = null) }
    }

    fun onEmailChange(value: String) {
        _formState.update { it.copy(email = value) }
    }

    fun onPaymentTypeChange(type: PaymentType) {
        _formState.update {
            val newInstallment = if (type == PaymentType.CASH) 1 else it.installmentCount
            it.copy(
                paymentType      = type,
                installmentCount = newInstallment,
                previewPrice     = calculatePreview(it.selectedPackage, type, newInstallment)
            )
        }
    }

    fun onInstallmentChange(count: Int) {
        _formState.update {
            it.copy(
                installmentCount = count,
                previewPrice     = calculatePreview(it.selectedPackage, it.paymentType, count)
            )
        }
    }

    fun onPackageSelected(pkg: PackageEntity?) {
        _formState.update {
            it.copy(
                selectedPackage = pkg,
                previewPrice    = calculatePreview(pkg, it.paymentType, it.installmentCount)
            )
        }
    }

    fun onNotesChange(value: String) {
        _formState.update { it.copy(notes = value) }
    }

    /** DÜZELTME #2 — Fiyat önizlemesi Repository üzerinden (UI hesaplamıyor) */
    private fun calculatePreview(
        pkg: PackageEntity?,
        paymentType: PaymentType,
        installments: Int
    ): Double = pkg?.let {
        repository.calculateFinalPrice(it.basePrice, paymentType, installments)
    } ?: 0.0

    fun submitRegistration() {
        val state = _formState.value

        // Validasyon
        var hasError = false
        var newState = state

        if (state.fullName.isBlank()) {
            newState = newState.copy(fullNameError = "Ad Soyad zorunludur")
            hasError = true
        }
        if (state.phone.isBlank() || !state.phone.matches(Regex("^[0-9]{10,13}\$"))) {
            newState = newState.copy(phoneError = "Geçerli bir telefon numarası giriniz")
            hasError = true
        }
        if (hasError) {
            _formState.value = newState
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true, submitError = null) }

            val result = repository.registerMember(
                fullName         = state.fullName,
                phone            = state.phone,
                email            = state.email.takeIf { it.isNotBlank() },
                birthDateMs      = null,
                selectedPackage  = state.selectedPackage,
                paymentType      = state.paymentType,
                installmentCount = state.installmentCount,
                notes            = state.notes
            )

            result.fold(
                onSuccess = {
                    _formState.update {
                        it.copy(isSubmitting = false, submitSuccess = true)
                    }
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(isSubmitting = false, submitError = error.message)
                    }
                }
            )
        }
    }

    fun resetForm() {
        _formState.value = RegisterFormState()
    }

    fun dismissError() {
        _formState.update { it.copy(submitError = null) }
    }
}
