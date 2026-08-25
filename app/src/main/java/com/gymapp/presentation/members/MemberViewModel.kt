package com.gymapp.presentation.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.access.AppDestination
import com.gymapp.data.auth.CurrentUser
import com.gymapp.data.auth.StaffLink
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.repository.AppointmentRepository
import com.gymapp.data.repository.MemberRepository
import com.gymapp.domain.Decimals
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.PriceBreakdown
import com.gymapp.domain.Pricing
import com.gymapp.domain.SessionCarryOver
import com.gymapp.domain.MemberScope
import com.gymapp.domain.StaffRole
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Bir kez tüketilen kullanıcı bildirimleri (Snackbar). */
sealed interface MemberEvent {
    data object Deleted : MemberEvent
    data class Saved(val message: String) : MemberEvent
    data class Failed(val message: String) : MemberEvent
}

// ─── UI State ────────────────────────────────────────────────────────────────

data class MemberListUiState(
    val members: List<MemberEntity> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val role: StaffRole = StaffRole.TRAINER,
    val kapsam: MemberScope = MemberScope.ALL,
    /** Kapsam seçimi yalnızca kapsamı tanımlı olan eğitmene gösteriliyor. */
    val kapsamSecilebilir: Boolean = false,
    /** Eğitmen giriş yaptı ama personel kaydına bağlı değil; "benim üyelerim" tanımsız. */
    val personelBaglantisiYok: Boolean = false,
) {
    /** Çekmecedeki hedeflerin görünürlüğü tek kaynaktan. */
    fun gorebilir(destination: AppDestination): Boolean = destination.isVisibleTo(role)
}

data class RegisterFormState(
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val paymentType: PaymentMethod = PaymentMethod.CASH,
    val installmentCount: Int = 1,
    val selectedPackage: PackageEntity? = null,
    val discount: String = "0",
    val paymentStatus: PaymentState = PaymentState.PAID,
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
) {
    /**
     * Ücretin kalemleri — **saklanmıyor, durumdan türetiliyor**.
     *
     * Önceden `previewPrice` bir alandı ve fiyatı etkileyen dört ayrı işleyicinin
     * her biri onu yeniden hesaplamakla yükümlüydü. Beşinci bir işleyici eklemek
     * (ya da mevcut birinde hesabı unutmak) ekranda eski tutarın kalması demekti;
     * türetilmiş değerde o hata mümkün değil.
     */
    val breakdown: PriceBreakdown
        get() = Pricing.breakdown(
            basePrice = Money(selectedPackage?.basePriceMinor ?: 0L),
            discount = Money.ofMajor(Decimals.parseOrDefault(discount)),
            paymentType = paymentType,
            installmentCount = installmentCount,
        )

    /**
     * Yazılan iskonto paket fiyatını aşıyor mu?
     *
     * Aşınca sessizce kırpmak, kartta "1.000 − 5.000 = 0" gibi kendi içinde
     * tutarsız bir aritmetik bırakıyordu. Kırpma duruyor ama artık görünür.
     */
    val discountCapped: Boolean
        get() = breakdown.discountWasCapped(Money.ofMajor(Decimals.parseOrDefault(discount)))
}

class MemberViewModel(
    private val repository: MemberRepository,
    private val packageRepository: com.gymapp.data.repository.PackageRepository,
    private val appointmentRepository: AppointmentRepository,
    currentUser: CurrentUser,
) : ViewModel() {

    /**
     * Bir kez tüketilen bildirimler.
     *
     * Diğer üç ViewModel'de (paket, personel, market) bu kanal zaten vardı;
     * üye tarafı atlanmıştı. Eksikliği somut bir çökmeye yol açıyordu: silme
     * yolu fırlatıyor, çağrı çıplak bir `viewModelScope.launch` içinde
     * yapılıyor ve projede hiç `CoroutineExceptionHandler` olmadığı için
     * uygulama kapanıyordu.
     */
    private val _events = Channel<MemberEvent>(Channel.BUFFERED)
    val events: Flow<MemberEvent> = _events.receiveAsFlow()

    val packages = packageRepository.getAllPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)

    /**
     * Kapsam seçimi. Varsayılan [MemberScope.MINE]; yönetici rollerinde zaten
     * yok sayılıyor, dolayısıyla ayrı bir başlangıç değerine gerek yok.
     */
    private val _kapsam = MutableStateFlow(MemberScope.MINE)

    private val oturum = combine(currentUser.role, currentUser.staffLink, ::Pair)

    @OptIn(FlowPreview::class)
    private val aramaSonucu = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getAllMembers()
            else repository.searchMembers(query)
        }

    val listUiState: StateFlow<MemberListUiState> = combine(
        aramaSonucu,
        _isLoading,
        _kapsam,
        oturum,
        appointmentRepository.observeAll(),
    ) { members, loading, kapsam, oturumBilgisi, appointments ->
        val (role, link) = oturumBilgisi
        val staffId = (link as? StaffLink.Linked)?.staffId
        val isTrainer = role == StaffRole.TRAINER

        // Kapsam yalnızca "benim" tanımlıysa uygulanabilir. Bağlantısı olmayan
        // eğitmende süzmek, listeyi sessizce boşaltmak olurdu; ekran bunun
        // yerine sebebini yazıyor ve salonun listesini gösteriyor. Sunucu
        // tarafında da okuma zaten salona bağlı herkese açık.
        val kapsamSecilebilir = isTrainer && staffId != null
        val kendiUyeleri = kapsamSecilebilir && kapsam == MemberScope.MINE

        val gosterilecek = if (kendiUyeleri) {
            val memberIds = appointments
                .filter { it.staffId == staffId }
                .map { it.memberId }
                .toSet()
            members.filter { it.id in memberIds }
        } else {
            members
        }

        MemberListUiState(
            members = gosterilecek,
            isLoading = loading,
            searchQuery = _searchQuery.value,
            role = role,
            kapsam = if (kapsamSecilebilir) kapsam else MemberScope.ALL,
            kapsamSecilebilir = kapsamSecilebilir,
            personelBaglantisiYok = isTrainer && link is StaffLink.Unlinked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemberListUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onKapsamChange(kapsam: MemberScope) {
        _kapsam.value = kapsam
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
        _formState.update { it.copy(discount = value) }
    }

    fun onPaymentStatusChange(status: PaymentState) {
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
            it.copy(
                paymentType = type,
                installmentCount = Pricing.normalizeInstallment(type, it.installmentCount),
            )
        }
    }

    fun onInstallmentChange(count: Int) {
        _formState.update { it.copy(installmentCount = count) }
    }

    /**
     * Paket seçimi hatayı da temizler.
     *
     * "Lütfen bir üyelik paketi seçiniz." uyarısı yalnızca gönderimde siliniyordu:
     * kullanıcı paketi seçtikten sonra bile ekranda duruyor, düzeltilmiş bir
     * eksiği düzeltilmemiş gibi gösteriyordu. Ad ve telefon alanları bunu zaten
     * doğru yapıyordu.
     */
    fun onPackageSelected(pkg: PackageEntity?) {
        _formState.update {
            it.copy(
                selectedPackage = pkg,
                submitError = if (pkg != null) null else it.submitError,
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

    /** Üyenin işlem geçmişi; ekrandaki sabit metnin yerini alıyor. */
    fun getLedgerForMember(memberId: String): Flow<List<com.gymapp.data.local.entity.LedgerEntryEntity>> =
        repository.observeLedgerForMember(memberId)

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
            ).fold(
                onSuccess = { _events.send(MemberEvent.Saved("Ölçüm kaydedildi.")) },
                onFailure = { _events.send(MemberEvent.Failed(it.message ?: "Ölçüm kaydedilemedi.")) },
            )
        }
    }

    /**
     * Ölçüm kaydını siler (tombstone).
     *
     * Depo yolu baştan beri vardı ama hiçbir ekran çağırmıyordu: yanlış girilen
     * bir kilo ya da çevre ölçüsü kayıtta sonsuza kadar kalıyordu. Ölçüm
     * geçmişi zamanla karşılaştırmak için tutulduğundan, hatalı bir satır
     * grafiği kalıcı olarak yanlış gösterirdi.
     */
    fun deleteMeasurement(measurementId: String) {
        viewModelScope.launch {
            repository.deleteMeasurement(measurementId).fold(
                onSuccess = { _events.send(MemberEvent.Saved("Ölçüm silindi.")) },
                onFailure = { _events.send(MemberEvent.Failed(it.message ?: "Ölçüm silinemedi.")) },
            )
        }
    }

    fun updateMember(member: MemberEntity) {
        viewModelScope.launch {
            repository.updateMemberInfo(member).fold(
                onSuccess = { _events.send(MemberEvent.Saved("Bilgiler güncellendi.")) },
                onFailure = { _events.send(MemberEvent.Failed(it.message ?: "Güncellenemedi.")) },
            )
        }
    }

    fun deleteMember(memberId: String) {
        viewModelScope.launch {
            repository.deleteMember(memberId).fold(
                onSuccess = { _events.send(MemberEvent.Deleted) },
                onFailure = { _events.send(MemberEvent.Failed(it.message ?: "Üye silinemedi.")) },
            )
        }
    }

    fun resetForm() {
        _formState.value = RegisterFormState()
    }

    // KALDIRILDI: `dismissError`. Hiçbir ekran çağırmıyordu; asıl sorun olan
    // "düzeltilen eksiğin uyarısı ekranda kalıyor" durumu artık kaynağında
    // çözülüyor (bkz. [onPackageSelected]).

    /** Üyenin kalan borcu — tahsilat diyaloğu açılmadan önce okunur. */
    suspend fun outstandingBalance(memberId: String): Money = repository.outstandingBalance(memberId)

    /**
     * Tahsilat yazar.
     *
     * [amount] `null` ise kalan borcun tamamı. Ekran tutarı gösterip
     * değiştirilebilir kıldığı için normalde açıkça veriliyor; önceden tutar
     * hiç görünmüyor ve her zaman tamamı tahsil ediliyordu.
     */
    fun markAsPaid(memberId: String, amount: Money? = null) {
        viewModelScope.launch {
            repository.updatePaymentStatus(memberId, isPaid = true, amount = amount).fold(
                onSuccess = { _events.send(MemberEvent.Saved("Tahsilat kaydedildi.")) },
                onFailure = { _events.send(MemberEvent.Failed(it.message ?: "Tahsilat kaydedilemedi.")) },
            )
        }
    }
}

// KALDIRILDI: `basePriceMajor`. Fiyat önizlemesi TL (`Double`) üzerinden
// hesaplanmıyor artık; kalemler kuruş cinsinden `RegisterFormState.breakdown`
// ile geliyor ve ekran onları doğrudan yazıyor.
