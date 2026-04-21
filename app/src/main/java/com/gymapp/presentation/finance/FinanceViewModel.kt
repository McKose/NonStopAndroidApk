package com.gymapp.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.TransactionEntity
import com.gymapp.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
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
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
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
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FinanceUiState> = combine(
        _selectedMonth,
        _selectedYear,
        _filter,
        _methodFilter,
        repository.getAllTransactions()
    ) { month, year, filter, method, allTransactions ->
        val calendar = Calendar.getInstance()
        
        fun calculateRevenue(monthsBack: Int): Double {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -monthsBack)
            return allTransactions.filter { it.type == "INCOME" && it.date >= cal.timeInMillis && !it.isPending }.sumOf { it.amount }
        }

        calendar.set(year, month, 1, 0, 0, 0)
        val startTime = calendar.timeInMillis
        calendar.set(year, month, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        val endTime = calendar.timeInMillis

        val transactionsInPeriod = allTransactions.filter { it.date in startTime..endTime }
        
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
            monthlyRevenue = calculateRevenue(1),
            quarterlyRevenue = calculateRevenue(3),
            halfYearlyRevenue = calculateRevenue(6),
            yearlyRevenue = calculateRevenue(12),
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
