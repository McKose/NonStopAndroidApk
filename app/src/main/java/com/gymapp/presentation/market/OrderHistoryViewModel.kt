package com.gymapp.presentation.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.local.dao.OrderDao
import com.gymapp.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class OrderHistoryViewModel(
    private val orderDao: OrderDao,
    private val tenants: TenantProvider,
) : ViewModel() {

    /**
     * Salon kimliği **abone olunduğunda** okunuyor, ViewModel kurulurken değil.
     *
     * Önceki hâlde özellik ilklendiricisinde `requireTenantId()` çağrılıyordu.
     * Projedeki diğer dokuz kullanımın hepsi bir *getter* üzerinden okuyor
     * (`get() = tenants.requireTenantId()`), yani çağrı anında; bu ekran tek
     * istisnaydı. Farkın bedeli: oturum düşmüşken ekran açıldığında ViewModel'in
     * kendisi kuramıyor ve uygulama kapanıyordu.
     *
     * Oturum yoksa liste boş kalıyor — `requireTenantId()` ile fırlatmak yerine.
     * Fırlatmak burada bir şey kazandırmaz: bu bir okuma yolu, yanlış salona veri
     * yazma riski yok. `SyncCoordinator` da aynı yerde `currentTenantId()` okuyup
     * `null` durumunu sessizce geçiyor. Asıl düzeltme A2'de: oturum düştüğünde
     * kullanıcı zaten giriş ekranına gönderiliyor, yani bu dal normalde
     * görülmüyor.
     */
    val orders: StateFlow<List<OrderEntity>> = flow {
        val tenantId = tenants.currentTenantId()
        if (tenantId != null) emitAll(orderDao.observeAll(tenantId))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
