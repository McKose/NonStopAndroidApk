package com.gymapp.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.TransactionEntity
import com.gymapp.data.repository.FinanceRepository
import com.gymapp.domain.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class FinanceUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalProfit: Double = 0.0,
    val monthlyRevenue: Double = 0.0,
    val quarterlyRevenue: Double = 0.0,
    val halfYearlyRevenue: Double = 0.0,
    val yearlyRevenue: Double = 0.0,
    val selectedMonth: Int = LocalDate.now().monthValue - 1, // 0 tabanlı
    val selectedYear: Int = LocalDate.now().year,
    val selectedFilter: String = "ALL", // ALL, INCOME, EXPENSE
    val selectedPaymentMethod: String = "ALL", // ALL, CASH, CARD, MULTISPORT
    val isLoading: Boolean = false
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repository: FinanceRepository
) : ViewModel() {

    private val _filter = MutableStateFlow("ALL")
    private val _methodFilter = MutableStateFlow("ALL")
    private val _selectedMonth = MutableStateFlow(LocalDate.now().monthValue - 1)
    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FinanceUiState> = combine(
        _selectedMonth,
        _selectedYear,
        _filter,
        _methodFilter,
        repository.getAllTransactions()
    ) { month, year, filter, method, allTransactions ->
        val nowMs = System.currentTimeMillis()

        /**
         * Son [monthsBack] aylık ciro.
         *
         * Üst sınır artık `nowMs`: ileri tarihli (yanlış girilmiş) kayıtlar ciroyu şişiremez.
         */
        fun calculateRevenue(monthsBack: Long): Double {
            val window = Periods.lastMonths(monthsBack, nowMs)
            return allTransactions
                .filter { it.type == "INCOME" && !it.isPending && it.date in window }
                .sumOf { it.amount }
        }

        // Yarı açık aralık: `23:59:59` + temizlenmeyen milisaniye kaynaklı sınır hataları biter.
        val period = Periods.month(year, month)
        val transactionsInPeriod = allTransactions.filter { it.date in period }
        
        val income = transactionsInPeriod.filter { it.type == "INCOME" && !it.isPending }.sumOf { it.amount }
        val expense = transactionsInPeriod.filter { it.type == "EXPENSE" && !it.isPending }.sumOf { it.amount }
        
        var filteredList = when(filter) {
            "INCOME" -> transactionsInPeriod.filter { it.type == "INCOME" }
            "EXPENSE" -> transactionsInPeriod.filter { it.type == "EXPENSE" }
            "PENDING" -> transactionsInPeriod.filter { it.isPending }
            else -> transactionsInPeriod
        }

        if (method != "ALL") {
            filteredList = filteredList.filter { it.paymentMethod == method }
        }

        FinanceUiState(
            transactions = filteredList,
            totalIncome = income,
            totalExpense = expense,
            totalProfit = income - expense,
            monthlyRevenue = calculateRevenue(1L),
            quarterlyRevenue = calculateRevenue(3L),
            halfYearlyRevenue = calculateRevenue(6L),
            yearlyRevenue = calculateRevenue(12L),
            selectedMonth = month,
            selectedYear = year,
            selectedFilter = filter,
            selectedPaymentMethod = method,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceUiState(isLoading = true)
    )

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    fun setMethodFilter(method: String) {
        _methodFilter.value = method
    }

    fun setPeriod(month: Int, year: Int) {
        _selectedMonth.value = month
        _selectedYear.value = year
    }

    fun addExpense(
        amount: Double,
        category: String,
        description: String,
        paymentMethod: String,
        date: Long = System.currentTimeMillis(),
        note: String? = null,
        isPending: Boolean = false,
        type: String = "EXPENSE"
    ) {
        viewModelScope.launch {
            repository.addTransaction(
                TransactionEntity(
                    amount = amount,
                    type = type,
                    category = category,
                    description = description,
                    paymentMethod = paymentMethod,
                    date = date,
                    note = note,
                    isPending = isPending
                )
            )
        }
    }
}
