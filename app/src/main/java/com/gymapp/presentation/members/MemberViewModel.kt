package com.gymapp.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.repository.MemberRepository
import com.gymapp.domain.Decimals
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.Pricing
import com.gymapp.domain.SessionCarryOver
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── UI State ────────────────────────────────────────────────────────────────

data class MemberListUiState(
    val members: List<MemberEntity> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)

data class RegisterFormState(
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val paymentType: PaymentMethod = PaymentMethod.CASH,
    val installmentCount: Int = 1,
    val selectedPackage: PackageEntity? = null,
    val discount: String = "0",
    val paymentStatus: String = "PAID", // PAID, PENDING
    val paymentDateMs: Long? = System.currentTimeMillis(),
    val healthRisks: String = "",
    val healthNotes: String = "",
    val notes: String = "",
    // Validation
    val fullNameError: String? = null,
    val phoneError: String? = null,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitError: String? = null,
    val isRenewal: Boolean = false,
    val memberId: String? = null,
    /**
     * Yenilemede kalan seansların ne olacağı.
     *
     * Varsayılan [SessionCarryOver.CARRY]: üye o seansların parasını ödemiş.
     * Seçim ekranda açıkça duruyor, yani varsayılan bir politika dayatması değil
     * yalnızca imlecin başladığı yer.
     */
    val carryOver: SessionCarryOver = SessionCarryOver.CARRY,
    /**
     * Üyenin şu anki kalan seansı; `null` sınırsız paket ya da bilinmiyor.
     *
     * Ekranda seçimin gösterilip gösterilmeyeceğini bu belirliyor: devredecek
     * sayılabilir bir hak yoksa kullanıcıya sorulacak bir şey de yok.
     */
    val currentRemainingSessions: Int? = null,
    // Calculated price
    val previewPrice: Double = 0.0
)

class MemberViewModel(
    private val repository: MemberRepository,
    private val packageRepository: com.gymapp.data.repository.PackageRepository
) : ViewModel() {

    val packages = packageRepository.getAllPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)

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
                    members = members,
                    isLoading = loading,
                    searchQuery = _searchQuery.value
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MemberListUiState(isLoading = true)
            )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    /** Taksit seçenekleri vade farkı tablosuyla aynı kaynaktan gelir; ikisi sapamaz. */
    val installmentOptions: List<Int> = Pricing.installmentOptions

    fun onFullNameChange(value: String) {
        _formState.update { it.copy(fullName = value, fullNameError = null) }
    }

    fun onPhoneChange(value: String) {
        _formState.update { it.copy(phone = value, phoneError = null) }
    }

    fun onEmailChange(value: String) {
        _formState.update { it.copy(email = value) }
    }

    fun onDiscountChange(value: String) {
        _formState.update {
            val disc = Decimals.parseOrDefault(value)
            it.copy(
                discount = value,
                previewPrice = repository.calculateFinalPrice(
                    it.selectedPackage.basePriceMajor(),
                    disc,
                    it.paymentType,
                    it.installmentCount
                )
            )
        }
    }

    fun onPaymentStatusChange(status: String) {
        _formState.update { it.copy(paymentStatus = status) }
    }

    fun onHealthRisksChange(value: String) {
        _formState.update { it.copy(healthRisks = value) }
    }

    fun onHealthNotesChange(value: String) {
        _formState.update { it.copy(healthNotes = value) }
    }

    fun onPaymentTypeChange(type: PaymentMethod) {
        _formState.update {
            // Taksit yalnızca kartlı ödemede anlamlı; kural [Pricing] içinde tek noktada.
            val newInstallment = Pricing.normalizeInstallment(type, it.installmentCount)
            it.copy(
                paymentType = type,
                installmentCount = newInstallment,
                previewPrice = repository.calculateFinalPrice(
                    it.selectedPackage.basePriceMajor(),
                    Decimals.parseOrDefault(it.discount),
                    type,
                    newInstallment
                )
            )
        }
    }

    fun onInstallmentChange(count: Int) {
        _formState.update {
            it.copy(
                installmentCount = count,
                previewPrice = repository.calculateFinalPrice(
                    it.selectedPackage.basePriceMajor(),
                    Decimals.parseOrDefault(it.discount),
                    it.paymentType,
                    count
                )
            )
        }
    }

    fun onPackageSelected(pkg: PackageEntity?) {
        _formState.update {
            it.copy(
                selectedPackage = pkg,
                previewPrice = repository.calculateFinalPrice(
                    pkg.basePriceMajor(),
                    Decimals.parseOrDefault(it.discount),
                    it.paymentType,
                    it.installmentCount
                )
            )
        }
    }

    fun onCarryOverChange(value: SessionCarryOver) {
        _formState.update { it.copy(carryOver = value) }
    }

    fun onNotesChange(value: String) {
        _formState.update { it.copy(notes = value) }
    }

    fun submitRegistration() {
        val state = _formState.value
        var hasError = false
        var newState = state

        if (state.fullName.isBlank()) {
            newState = newState.copy(fullNameError = "Ad Soyad zorunludur")
            hasError = true
        }
        when {
            state.phone.isBlank() -> {
                newState = newState.copy(phoneError = "Telefon zorunludur")
                hasError = true
            }
            // Kayıttan önce doğrula: numara E.164'e çevrilemiyorsa tekillik kontrolü de anlamsız.
            PhoneNumber.normalizeTr(state.phone) == null -> {
                newState = newState.copy(phoneError = "Geçerli bir cep telefonu giriniz (5XX XXX XX XX)")
                hasError = true
            }
        }
        // Paket zorunlu: pakedsiz üyenin bitiş tarihi olmaz ve üyelik hiç sona ermez.
        if (state.selectedPackage == null) {
            newState = newState.copy(submitError = "Lütfen bir üyelik paketi seçiniz.")
            hasError = true
        }

        if (hasError) {
            _formState.value = newState
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true, submitError = null) }
            
            val result = if (state.isRenewal && state.memberId != null && state.selectedPackage != null) {
                repository.renewPackage(
                    memberId = state.memberId,
                    selectedPackage = state.selectedPackage,
                    paymentType = state.paymentType,
                    installmentCount = state.installmentCount,
                    discount = Decimals.parseOrDefault(state.discount),
                    paymentStatus = state.paymentStatus,
                    paymentDateMs = state.paymentDateMs,
                    carryOver = state.carryOver,
                ).map { state.memberId }
            } else {
                repository.registerMember(
                    fullName = state.fullName,
                    phone = state.phone,
                    email = state.email,
                    selectedPackage = state.selectedPackage,
                    paymentType = state.paymentType,
                    installmentCount = state.installmentCount,
                    discount = Decimals.parseOrDefault(state.discount),
                    paymentStatus = state.paymentStatus,
                    paymentDateMs = state.paymentDateMs,
                    healthRisks = state.healthRisks,
                    healthNotes = state.healthNotes,
                    notes = state.notes
                )
            }

            result.fold(
                onSuccess = { _formState.update { it.copy(isSubmitting = false, submitSuccess = true) } },
                onFailure = { error -> _formState.update { it.copy(isSubmitting = false, submitError = error.message) } }
            )
        }
    }

    fun loadMemberForRenewal(memberId: String) {
        viewModelScope.launch {
            repository.getMemberById(memberId).firstOrNull()?.let { member ->
                _formState.update {
                    it.copy(
                        memberId = member.id,
                        fullName = member.fullName,
                        phone = member.phone,
                        email = member.email ?: "",
                        isRenewal = true,
                        currentRemainingSessions = member.remainingSessions,
                    )
                }
            }
        }
    }

    fun getMemberById(id: String): Flow<MemberEntity?> {
        return repository.getMemberById(id)
    }

    fun getMeasurements(memberId: String): Flow<List<com.gymapp.data.local.entity.MeasurementEntity>> =
        repository.getMeasurementsForMember(memberId)

    fun addMeasurement(
        memberId: String,
        height: Double,
        weight: Double,
        shoulder: Double,
        chest: Double,
        waist: Double,
        hips: Double,
        leg: Double,
        arm: Double,
        notes: String,
    ) {
        viewModelScope.launch {
            repository.addMeasurement(
                memberId = memberId,
                height = height,
                weight = weight,
                shoulder = shoulder,
                chest = chest,
                waist = waist,
                hips = hips,
                leg = leg,
                arm = arm,
                notes = notes,
            )
        }
    }

    fun updateMember(member: MemberEntity) {
        viewModelScope.launch {
            repository.updateMemberInfo(member)
        }
    }

    fun deleteMember(memberId: String) {
        viewModelScope.launch {
            repository.deleteMember(memberId)
        }
    }

    fun resetForm() {
        _formState.value = RegisterFormState()
    }

    fun dismissError() {
        _formState.update { it.copy(submitError = null) }
    }

    fun markAsPaid(memberId: String) {
        viewModelScope.launch {
            repository.updatePaymentStatus(memberId, true)
        }
    }
}

/**
 * Paket fiyatının TL karşılığı — **yalnızca ekran önizlemesi** için.
 *
 * Kaydedilen tutar kuruş üzerinden [com.gymapp.domain.Pricing.finalPrice] ile hesaplanır;
 * bu dönüşüm hesaba girmez.
 */
private fun PackageEntity?.basePriceMajor(): Double = Money(this?.basePriceMinor ?: 0L).asDouble
