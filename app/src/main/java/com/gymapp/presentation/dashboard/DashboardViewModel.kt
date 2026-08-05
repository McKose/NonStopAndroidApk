package com.gymapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.domain.Membership
import com.gymapp.domain.MembershipState
import com.gymapp.domain.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val LOW_STOCK_THRESHOLD = 5

data class DashboardUiState(
    val userRole: String = "antrenör",
    val totalMembers: Int = 0,
    val activeMembers: Int = 0,
    val dailyAppointments: List<AppointmentEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val staffList: List<StaffEntity> = emptyList(),
    val criticalAlerts: List<String> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val appointmentRepository: com.gymapp.data.repository.AppointmentRepository,
    private val staffDao: com.gymapp.data.local.dao.StaffDao,
    private val productRepository: com.gymapp.data.repository.ProductRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        memberRepository.getAllMembers(),
        appointmentRepository.observeAll(),
        staffDao.getAllStaff(),
        productRepository.getAllProducts(),
        productRepository.observeStockByProduct(),
    ) { members, appointments, staff, products, stockByProduct ->
        val now = System.currentTimeMillis()
        val today = Periods.dayOf(now)

        val userRole = prefs.currentUserRole
        val currentUserId = prefs.currentUserId

        val filteredAppointments = if (userRole == "antrenör") {
            appointments.filter { it.staffId == currentUserId && it.startTimeMs in today }
        } else {
            appointments.filter { it.startTimeMs in today }
        }

        val filteredMembers = if (userRole == "antrenör") {
            val memberIds = appointments.filter { it.staffId == currentUserId }.map { it.memberId }.toSet()
            members.filter { it.id in memberIds }
        } else {
            members
        }

        val alerts = mutableListOf<String>()

        // Stock Alerts (Admin/Manager only)
        if (userRole != "antrenör") {
            products.forEach { product ->
                val onHand = stockByProduct[product.id] ?: 0
                if (onHand < LOW_STOCK_THRESHOLD) {
                    alerts.add("Kritik Stok: ${product.name} ($onHand adet kaldı)")
                }
            }
        }

        // Expiring Memberships (Next 3 days)
        val expiryFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault())
        val threeDaysLater = now + TimeUnit.DAYS.toMillis(3)
        members.forEach { member ->
            val endDate = member.endDateMs ?: return@forEach
            val isActive = Membership.stateOf(member.status, endDate, now) == MembershipState.ACTIVE
            if (isActive && endDate <= threeDaysLater) {
                val formatted = Instant.ofEpochMilli(endDate)
                    .atZone(ZoneId.systemDefault())
                    .format(expiryFormatter)
                alerts.add("Üyelik Bitiyor: ${member.fullName} ($formatted)")
            }
        }

        // Pending Payments — arşivlenmiş üyeler için uyarı üretme
        members.filter {
            it.paymentStatus == "PENDING" &&
                Membership.stateOf(it.status, it.endDateMs, now) != MembershipState.PASSIVE
        }.forEach {
            alerts.add("Ödeme Bekliyor: ${it.fullName} (₺${it.pricePaid})")
        }

        DashboardUiState(
            userRole = userRole,
            totalMembers = filteredMembers.size,
            // Üyelik durumu artık bitiş tarihinden türetiliyor; süresi dolmuş üye
            // arka plan işi çalışmasa bile "aktif" sayılmıyor.
            activeMembers = filteredMembers.count {
                Membership.stateOf(it.status, it.endDateMs, now) == MembershipState.ACTIVE
            },
            dailyAppointments = filteredAppointments,
            members = members,
            staffList = staff,
            criticalAlerts = alerts,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )
}
