package com.gymapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

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
    private val appointmentDao: com.gymapp.data.local.dao.AppointmentDao,
    private val staffDao: com.gymapp.data.local.dao.StaffDao,
    private val productDao: com.gymapp.data.local.dao.ProductDao,
    private val prefs: AppPreferences
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        memberRepository.getAllMembers(),
        appointmentDao.getAllAppointments(),
        staffDao.getAllStaff(),
        productDao.getAllProducts()
    ) { members, appointments, staff, products ->
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val userRole = prefs.currentUserRole
        val currentUserId = prefs.currentUserId

        val filteredAppointments = if (userRole == "antrenör") {
            appointments.filter { it.staffId == currentUserId && it.startTimeMs in startOfDay..endOfDay }
        } else {
            appointments.filter { it.startTimeMs in startOfDay..endOfDay }
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
            products.filter { it.stockCount < 5 }.forEach { 
                alerts.add("Kritik Stok: ${it.name} (${it.stockCount} adet kaldı)")
            }
        }

        // Expiring Memberships (Next 3 days)
        val threeDaysLater = now + (3 * 24 * 60 * 60 * 1000)
        members.filter { 
            it.status == "ACTIVE" && 
            it.endDateMs != null && 
            it.endDateMs!! in now..threeDaysLater 
        }.forEach {
            alerts.add("Üyelik Bitiyor: ${it.fullName} (${java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(java.util.Date(it.endDateMs!!))})")
        }

        // Pending Payments
        members.filter { it.paymentStatus == "PENDING" }.forEach {
            alerts.add("Ödeme Bekliyor: ${it.fullName} (₺${it.pricePaid})")
        }

        DashboardUiState(
            userRole = userRole,
            totalMembers = filteredMembers.size,
            activeMembers = filteredMembers.count { it.status == "ACTIVE" },
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
