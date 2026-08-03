package com.gymapp.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.repository.FinanceRepository
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class FinanceUiState(
    val entries: List<FinanceEntry> = emptyList(),
    val totalIncome: Money = Money.ZERO,
    val totalExpense: Money = Money.ZERO,
    val totalProfit: Money = Money.ZERO,
    val monthlyRevenue: Money = Money.ZERO,
    val quarterlyRevenue: Money = Money.ZERO,
    val halfYearlyRevenue: Money = Money.ZERO,
    val yearlyRevenue: Money = Money.ZERO,
    val selectedMonth: Int = LocalDate.now().monthValue - 1, // 0 tabanlı
    val selectedYear: Int = LocalDate.now().year,
    val selectedFilter: String = "ALL", // ALL, INCOME, EXPENSE, PENDING
    val selectedPaymentMethod: String = "ALL",
    val isLoading: Boolean = false,
)

sealed interface FinanceEvent {
    data object Saved : FinanceEvent
    data class Failed(val message: String) : FinanceEvent
}

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _filter = MutableStateFlow("ALL")
    private val _methodFilter = MutableStateFlow("ALL")
    private val _selectedMonth = MutableStateFlow(LocalDate.now().monthValue - 1)
    private val _selectedYear = MutableStateFlow(LocalDate.now().year)

    private val _events = Channel<FinanceEvent>(Channel.BUFFERED)
    val events: Flow<FinanceEvent> = _events.receiveAsFlow()

    /**
     * Geçiş süresince iki kaynak birden okunur; yazıcılar deftere taşındıkça
     * eski kaynağın katkısı kendiliğinden boşalır ve sonunda kaldırılır.
     *
     * Ters kayıtla iptal edilmiş kayıtlar listede görünür (denetim izi) ama
     * toplamlara girmez.
     */
    private val allEntries: Flow<List<FinanceEntry>> = combine(
        repository.getLegacyTransactions(),
        repository.observeLedgerBetween(startMs = 0L, endMs = Long.MAX_VALUE),
    ) { legacy, ledger ->
        val reversedIds = ledger.mapNotNull { it.reversesId }.toSet()
        val merged = legacy.map { it.toFinanceEntry() } +
            ledger.map { it.toFinanceEntry(reversedIds) }
        merged.sortedByDescending { it.occurredAtMs }
    }

    val uiState: StateFlow<FinanceUiState> = combine(
        _selectedMonth,
        _selectedYear,
        _filter,
        _methodFilter,
        allEntries,
    ) { month, year, filter, method, entries ->
        val nowMs = System.currentTimeMillis()

        /** Son [monthsBack] aylık tahsilat. Üst sınır `nowMs`: ileri tarihli kayıt ciroyu şişiremez. */
        fun revenue(monthsBack: Long): Money {
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
            monthlyRevenue = revenue(1L),
            quarterlyRevenue = revenue(3L),
            halfYearlyRevenue = revenue(6L),
            yearlyRevenue = revenue(12L),
            selectedMonth = month,
            selectedYear = year,
            selectedFilter = filter,
            selectedPaymentMethod = method,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState(isLoading = true)
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
        occurredAtMs: Long = System.currentTimeMillis(),
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

    /** Hatalı kaydı ters kayıtla iptal eder. Eski kaynaktaki kayıtlar iptal edilemez. */
    fun voidEntry(entry: FinanceEntry, reason: String = "Kullanıcı düzeltmesi") {
        if (entry.id.startsWith("legacy-")) {
            _events.trySend(FinanceEvent.Failed("Bu kayıt eski kaynaktan geliyor, iptal edilemez."))
            return
        }
        viewModelScope.launch {
            repository.voidEntry(entry.id, reason).fold(
                onSuccess = { _events.send(FinanceEvent.Saved) },
                onFailure = { _events.send(FinanceEvent.Failed(it.message ?: "İptal edilemedi.")) },
            )
        }
    }
}
