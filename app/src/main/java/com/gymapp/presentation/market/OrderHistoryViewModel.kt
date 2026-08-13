package com.gymapp.presentation.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.auth.requireTenantId
import com.gymapp.data.local.dao.OrderDao
import com.gymapp.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class OrderHistoryViewModel(
    private val orderDao: OrderDao,
    tenants: TenantProvider,
) : ViewModel() {

    val orders: StateFlow<List<OrderEntity>> = orderDao.observeAll(tenants.requireTenantId())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
