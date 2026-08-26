package com.gymapp.arayuz.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.dao.OrderDao
import com.gymapp.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class OrderHistoryViewModel(
    private val orderDao: OrderDao,
    private val memberDao: MemberDao,
    private val tenants: TenantProvider,
) : ViewModel() {

    /**
     * Salon kimliği **abone olunduğunda** okunuyor, ViewModel kurulurken değil.
     *
     * Önceki hâlde özellik ilklendiricisinde `requireTenantId()` çağrılıyordu.
     * Projedeki diğer dokuz kullanımın hepsi bir *getter* üzerinden okuyor
     * (`get() = tenants.requireTenantId()`), yani çağrı anında; bu ekran tek
     * istisnaydı. Farkın bedeli: oturum düşmüşken ekran açıldığında ViewModel'in
     * kendisi kurulamıyor ve uygulama kapanıyordu.
     */
    val orders: StateFlow<List<OrderEntity>> = flow {
        val tenantId = tenants.currentTenantId()
        if (tenantId != null) emitAll(orderDao.observeAll(tenantId))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Üye kimliği → ad.
     *
     * Sipariş geçmişi "Üye ID: 9f3c1a0e-…" diye ham UUID basıyordu; hangi üyenin
     * ne aldığı ekrandan okunamıyordu. Sipariş satırı yalnızca kimliği taşıyor,
     * ad üye tablosunda — eşleme burada yapılıyor ki ekran veri katmanına
     * inmesin.
     *
     * Silinmiş üyenin adı da gösterilebilsin diye liste değil harita: eşleşme
     * bulunamazsa ekran "Silinmiş üye" diyor, ham kimliğe geri düşmüyor.
     */
    val memberNames: StateFlow<Map<String, String>> = flow {
        val tenantId = tenants.currentTenantId()
        if (tenantId != null) {
            emitAll(memberDao.getAllMembers(tenantId).map { list -> list.associate { it.id to it.fullName } })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )
}
