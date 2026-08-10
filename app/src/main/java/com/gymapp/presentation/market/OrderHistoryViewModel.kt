package com.gymapp.presentation.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.OrderDao
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.domain.Ids
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class OrderHistoryViewModel(
    private val orderDao: OrderDao
) : ViewModel() {

    val orders: StateFlow<List<OrderEntity>> = orderDao.observeAll(Ids.DEFAULT_TENANT)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
