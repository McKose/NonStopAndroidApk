package com.gymapp.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.PostureCommentDao
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.MemberPackageEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.local.entity.PaymentType
import com.gymapp.data.local.entity.PostureCommentEntity
import com.gymapp.data.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val paymentType: PaymentType = PaymentType.CASH,
    val installmentCount: Int = 1,
    val selectedPackage: PackageEntity? = null,
    val discount: String = "0",
    val paymentStatus: String = "PAID",
    val paymentDateMs: Long? = System.currentTimeMillis(),
    val healthRisks: String = "",
    val healthNotes: String = "",
    val notes: String = "",
    val measureOnRegistration: Boolean = false,
    val fullNameError: String? = null,
    val phoneError: String? = null,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val createdMemberId: Long? = null,
    val submitError: String? = null,
    val isRenewal: Boolean = false,
    val memberId: Long? = null,
    val previewPrice: Double = 0.0,
    val previewSurcharge: Double = 0.0,
    val previewRatePercent: Double = 0.0
)

@HiltViewModel
class MemberViewModel @Inject constructor(
    private val repository: MemberRepository,
    private val packageRepository: com.gymapp.data.repository.PackageRepository,
    private val postureCommentDao: PostureCommentDao
) : ViewModel() {

    val packages = packageRepository.getAllPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    private val _formState = MutableStateFlow(RegisterFormState())
    val formState: StateFlow<RegisterFormState> = _formState.asStateFlow()

    fun onFullNameChange(value: String) = _formState.update { it.copy(fullName = value, fullNameError = null) }
    fun onPhoneChange(value: String) = _formState.update { it.copy(phone = value, phoneError = null) }
    fun onEmailChange(value: String) = _formState.update { it.copy(email = value) }
    fun onHealthRisksChange(value: String) = _formState.update { it.copy(healthRisks = value) }
    fun onHealthNotesChange(value: String) = _formState.update { it.copy(healthNotes = value) }
    fun onNotesChange(value: String) = _formState.update { it.copy(notes = value) }
    fun onMeasureOnRegistrationToggle(value: Boolean) =
        _formState.update { it.copy(measureOnRegistration = value) }
    fun onPaymentStatusChange(status: String) =
        _formState.update { it.copy(paymentStatus = status) }

    fun onDiscountChange(value: String) {
        _formState.update { it.copy(discount = value) }
        recalcPrice()
    }

    fun onPaymentTypeChange(type: PaymentType) {
        _formState.update { state ->
            val newInstallment = if (type == PaymentType.CASH || type == PaymentType.MULTISPORT) 1
                                 else state.installmentCount
            state.copy(paymentType = type, installmentCount = newInstallment)
        }
        recalcPrice()
    }

    fun onInstallmentChange(count: Int) {
        _formState.update { it.copy(installmentCount = count) }
        recalcPrice()
    }

    fun onPackageSelected(pkg: PackageEntity?) {
        _formState.update { it.copy(selectedPackage = pkg) }
        recalcPrice()
    }

    private fun recalcPrice() {
        val s = _formState.value
        val pkg = s.selectedPackage ?: run {
            _formState.update { it.copy(previewPrice = 0.0, previewSurcharge = 0.0, previewRatePercent = 0.0) }
            return
        }
        viewModelScope.launch {
            val bd = repository.calculatePriceBreakdown(
                packagePrice = pkg.basePrice,
                discount = s.discount.toDoubleOrNull() ?: 0.0,
                paymentType = s.paymentType,
                installmentCount = s.installmentCount
            )
            _formState.update {
                it.copy(
                    previewPrice = bd.finalPrice,
                    previewSurcharge = bd.surcharge,
                    previewRatePercent = bd.ratePercent
                )
            }
        }
    }

    fun submitRegistration() {
        val state = _formState.value
        var hasError = false
        var newState = state

        if (state.fullName.isBlank()) {
            newState = newState.copy(fullNameError = "Ad Soyad zorunludur")
            hasError = true
        }
        if (state.phone.isBlank()) {
            newState = newState.copy(phoneError = "Telefon zorunludur")
            hasError = true
        }

        if (hasError) {
            _formState.value = newState
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSubmitting = true, submitError = null) }

            val result: Result<Long> = if (state.isRenewal && state.memberId != null && state.selectedPackage != null) {
                repository.renewPackage(
                    memberId = state.memberId,
                    selectedPackage = state.selectedPackage,
                    paymentType = state.paymentType,
                    installmentCount = state.installmentCount,
                    discount = state.discount.toDoubleOrNull() ?: 0.0,
                    paymentStatus = state.paymentStatus,
                    paymentDateMs = state.paymentDateMs
                ).map { state.memberId }
            } else {
                repository.registerMember(
                    fullName = state.fullName,
                    phone = state.phone,
                    email = state.email,
                    selectedPackage = state.selectedPackage,
                    paymentType = state.paymentType,
                    installmentCount = state.installmentCount,
                    discount = state.discount.toDoubleOrNull() ?: 0.0,
                    paymentStatus = state.paymentStatus,
                    paymentDateMs = state.paymentDateMs,
                    healthRisks = state.healthRisks,
                    healthNotes = state.healthNotes,
                    notes = state.notes
                )
            }

            result.fold(
                onSuccess = { id ->
                    _formState.update { it.copy(isSubmitting = false, submitSuccess = true, createdMemberId = id) }
                },
                onFailure = { err ->
                    _formState.update { it.copy(isSubmitting = false, submitError = err.message) }
                }
            )
        }
    }

    fun loadMemberForRenewal(memberId: Long) {
        viewModelScope.launch {
            repository.getMemberById(memberId).firstOrNull()?.let { member ->
                _formState.update {
                    it.copy(
                        memberId = member.id,
                        fullName = member.fullName,
                        phone = member.phone,
                        email = member.email ?: "",
                        isRenewal = true
                    )
                }
            }
        }
    }

    fun getMemberById(id: Long): Flow<MemberEntity?> = repository.getMemberById(id)

    fun getMeasurements(memberId: Long): Flow<List<com.gymapp.data.local.entity.MeasurementEntity>> =
        repository.getMeasurementsForMember(memberId)

    suspend fun getMeasurementById(id: Long): com.gymapp.data.local.entity.MeasurementEntity? =
        repository.getMeasurementById(id)

    fun addMeasurement(measurement: com.gymapp.data.local.entity.MeasurementEntity) {
        viewModelScope.launch { repository.addMeasurement(measurement) }
    }

    fun updateMeasurement(measurement: com.gymapp.data.local.entity.MeasurementEntity) {
        viewModelScope.launch { repository.updateMeasurement(measurement) }
    }

    fun deleteMeasurement(measurement: com.gymapp.data.local.entity.MeasurementEntity) {
        viewModelScope.launch { repository.deleteMeasurement(measurement) }
    }

    fun updateMember(member: MemberEntity) {
        viewModelScope.launch { repository.updateMemberInfo(member) }
    }

    fun deleteMember(memberId: Long) {
        viewModelScope.launch { repository.deleteMember(memberId) }
    }

    fun resetForm() { _formState.value = RegisterFormState() }
    fun dismissError() { _formState.update { it.copy(submitError = null) } }

    fun markAsPaid(memberId: Long) {
        viewModelScope.launch { repository.updatePaymentStatus(memberId, true) }
    }

    // ─── Paket geçmişi / aktif paketler ──────────────────────────────────────
    fun getActivePackages(memberId: Long): Flow<List<MemberPackageEntity>> =
        repository.getActivePackagesForMember(memberId)

    fun getPackageHistory(memberId: Long): Flow<List<MemberPackageEntity>> =
        repository.getPackageHistoryForMember(memberId)

    // ─── Postür notları ──────────────────────────────────────────────────────
    fun getPostureComments(memberId: Long): Flow<List<PostureCommentEntity>> =
        postureCommentDao.getForMember(memberId)

    fun addPostureComment(memberId: Long, comment: String, dateMs: Long = System.currentTimeMillis()) {
        if (comment.isBlank()) return
        viewModelScope.launch {
            postureCommentDao.insert(
                PostureCommentEntity(
                    memberId = memberId,
                    dateMs = dateMs,
                    comment = comment.trim()
                )
            )
        }
    }

    fun updatePostureComment(entity: PostureCommentEntity) {
        viewModelScope.launch { postureCommentDao.update(entity) }
    }

    fun deletePostureComment(entity: PostureCommentEntity) {
        viewModelScope.launch { postureCommentDao.delete(entity) }
    }
}
