package com.gymapp.arayuz.finans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.access.AppDestination
import com.gymapp.data.auth.CurrentUser
import com.gymapp.data.repository.FinanceRepository
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.Money
import com.gymapp.domain.Now
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.Periods
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * @property selectedMonth **0 tabanlı** ay. Varsayılanı YOK ve olmamalı:
 *           önceden `LocalDate.now().monthValue - 1` idi, yani veri sınıfının
 *           varsayılan parametresi saati okuyordu. Bu hem JVM'e bağlıydı hem de
 *           `FinanceUiState()`i saf olmayan bir çağrı hâline getiriyordu — aynı
 *           argümanlarla iki kez kurulduğunda farklı nesne üretebiliyordu.
 *           Açılış dönemini artık ViewModel bir kez hesaplayıp geçiriyor.
 */
data class FinanceUiState(
    val selectedMonth: Int,
    val selectedYear: Int,
    val entries: List<FinansKaydi> = emptyList(),
    val totalIncome: Money = Money.ZERO,
    val totalExpense: Money = Money.ZERO,
    val totalProfit: Money = Money.ZERO,
    val monthlyRevenue: Money = Money.ZERO,
    val quarterlyRevenue: Money = Money.ZERO,
    val halfYearlyRevenue: Money = Money.ZERO,
    val yearlyRevenue: Money = Money.ZERO,
    val selectedFilter: String = "ALL", // ALL, INCOME, EXPENSE, PENDING
    val selectedPaymentMethod: String = "ALL",
    val isLoading: Boolean = false,
)

sealed interface FinanceEvent {
    data object Saved : FinanceEvent
    data class Failed(val message: String) : FinanceEvent
}

class FinanceViewModel(
    private val repository: FinanceRepository,
    currentUser: CurrentUser,
) : ViewModel() {

    /**
     * Bu kullanıcı finans ekranını görebilir mi?
     *
     * Giriş noktalarını gizlemek tek başına yetmiyor: gizlenmiş bir düğme kural
     * değil, yalnızca görüntü. Ekranın kendisi de soruyor ki hedefe başka bir
     * yoldan gelen (ileride eklenecek bir kısayol, bir bildirim) yetkisiz
     * kullanıcı salonun cirosunu görmesin.
     *
     * Bunun bir **güvenlik sınırı olmadığını** açıkça yazmak gerekiyor: defter
     * satırları sunucuda salona bağlı herkesin okumasına açık (migrasyon
     * `0004`) ve cihazdaki kopyada zaten duruyorlar. Buradaki kontrol arayüz
     * kararı; gerçek sınır yazma tarafında.
     */
    val gorebilir: StateFlow<Boolean> = currentUser.role
        .map { AppDestination.FINANCE.isVisibleTo(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _filter = MutableStateFlow("ALL")
    private val _methodFilter = MutableStateFlow("ALL")

    /**
     * Ekranın açılışta gösterdiği dönem — saat **bir kez** okunuyor.
     *
     * Ay ve yıl ayrı ayrı `LocalDate.now()` çağırmıyor artık: iki okuma arasında
     * yıl dönebilir ve ekran "Aralık 2027" gibi olmayan bir dönem açardı.
     */
    private val acilisDonemi = Periods.donemOf(Now.epochMillis())

    private val _selectedMonth = MutableStateFlow(acilisDonemi.ayIndeksi0)
    private val _selectedYear = MutableStateFlow(acilisDonemi.yil)

    private val _events = Channel<FinanceEvent>(Channel.BUFFERED)
    val events: Flow<FinanceEvent> = _events.receiveAsFlow()

    /**
     * Tek kaynak: finans defteri.
     *
     * Ters kayıtla iptal edilmiş kayıtlar listede görünür (denetim izi) ama
     * toplamlara girmez.
     */
    private val allEntries: Flow<List<FinansKaydi>> =
        repository.observeLedgerBetween(startMs = 0L, endMs = Long.MAX_VALUE)
            .map { ledger ->
                val reversedIds = ledger.mapNotNull { it.reversesId }.toSet()
                ledger.map { it.finansKaydinaCevir(reversedIds) }
                    .sortedByDescending { entry -> entry.occurredAtMs }
            }

    val uiState: StateFlow<FinanceUiState> = combine(
        _selectedMonth,
        _selectedYear,
        _filter,
        _methodFilter,
        allEntries,
    ) { month, year, filter, method, entries ->
        val nowMs = Now.epochMillis()

        /** Son [monthsBack] aylık tahsilat. Üst sınır `nowMs`: ileri tarihli kayıt ciroyu şişiremez. */
        fun revenue(monthsBack: Int): Money {
            val window = Periods.lastMonths(monthsBack, nowMs)
            return entries
                .filter { it.isIncome && !it.isPending && !it.isVoided && it.occurredAtMs in window }
                .fold(Money.ZERO) { acc, e -> acc + e.amount }
        }

        // Yarı açık aralık: gün/ay sınırındaki kayıtlar kaybolmaz.
        val period = Periods.month(year, month)
        val inPeriod = entries.filter { it.occurredAtMs in period }

        val income = inPeriod
            .filter { it.isIncome && !it.isPending && !it.isVoided }
            .fold(Money.ZERO) { acc, e -> acc + e.amount }
        val expense = inPeriod
            .filter { !it.isIncome && !it.isPending && !it.isVoided }
            .fold(Money.ZERO) { acc, e -> acc + e.amount }

        var visible = when (filter) {
            "INCOME" -> inPeriod.filter { it.isIncome }
            "EXPENSE" -> inPeriod.filter { !it.isIncome }
            "PENDING" -> inPeriod.filter { it.isPending }
            else -> inPeriod
        }
        if (method != "ALL") {
            visible = visible.filter { it.paymentMethod.name == method }
        }

        FinanceUiState(
            entries = visible,
            totalIncome = income,
            totalExpense = expense,
            totalProfit = income - expense,
            monthlyRevenue = revenue(1),
            quarterlyRevenue = revenue(3),
            halfYearlyRevenue = revenue(6),
            yearlyRevenue = revenue(12),
            selectedMonth = month,
            selectedYear = year,
            selectedFilter = filter,
            selectedPaymentMethod = method,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState(
            selectedMonth = acilisDonemi.ayIndeksi0,
            selectedYear = acilisDonemi.yil,
            isLoading = true,
        )
    )

    fun setFilter(filter: String) { _filter.value = filter }
    fun setMethodFilter(method: String) { _methodFilter.value = method }

    fun setPeriod(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value = year
    }

    /**
     * Elle kayıt ekler. Yeni yazımların tamamı **deftere** gider.
     *
     * Sonuç her durumda kullanıcıya bildirilir; önceki sürümde hata sessizce yutuluyordu.
     */
    fun addEntry(
        amountText: String,
        categoryName: String,
        description: String,
        paymentMethodName: String,
        isIncome: Boolean,
        occurredAtMs: Long = Now.epochMillis(),
    ) {
        val amount = Money.parseOrNull(amountText)
        if (amount == null || !amount.isPositive) {
            _events.trySend(FinanceEvent.Failed("Geçerli bir tutar giriniz."))
            return
        }
        if (description.isBlank()) {
            _events.trySend(FinanceEvent.Failed("Açıklama boş olamaz."))
            return
        }

        val category = runCatching { LedgerCategory.valueOf(categoryName) }
            .getOrDefault(LedgerCategory.OTHER)
        val method = runCatching { PaymentMethod.valueOf(paymentMethodName) }
            .getOrDefault(PaymentMethod.CASH)

        viewModelScope.launch {
            val result = if (isIncome) {
                repository.addIncome(amount, category, description, method, occurredAtMs)
            } else {
                repository.addExpense(amount, category, description, method, occurredAtMs)
            }
            result.fold(
                onSuccess = { _events.send(FinanceEvent.Saved) },
                onFailure = { _events.send(FinanceEvent.Failed(it.message ?: "Kayıt eklenemedi.")) },
            )
        }
    }

    /** Hatalı kaydı ters kayıtla iptal eder; kayıt silinmez, denetim izi korunur. */
    fun voidEntry(entry: FinansKaydi, reason: String = "Kullanıcı düzeltmesi") {
        viewModelScope.launch {
            repository.voidEntry(entry.id, reason).fold(
                onSuccess = { _events.send(FinanceEvent.Saved) },
                onFailure = { _events.send(FinanceEvent.Failed(it.message ?: "İptal edilemedi.")) },
            )
        }
    }
}
