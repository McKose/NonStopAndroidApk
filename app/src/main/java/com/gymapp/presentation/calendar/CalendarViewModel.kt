package com.gymapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.AppointmentDao
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.domain.appointment.CompleteAppointmentUseCase

data class CalendarUiState(
    val appointments: List<AppointmentEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val staffList: List<StaffEntity> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val appointmentDao: AppointmentDao,
    private val memberDao: MemberDao,
    private val staffDao: StaffDao,
    private val prefs: AppPreferences,
    private val completeAppointmentUseCase: CompleteAppointmentUseCase
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(Calendar.getInstance())
    val selectedDate: StateFlow<Calendar> = _selectedDate.asStateFlow()

    val uiState: StateFlow<CalendarUiState> = combine(
        appointmentDao.getAllAppointments(),
        memberDao.getAllMembers(),
        staffDao.getAllStaff(),
        _selectedDate
    ) { appointments, members, staffList, date ->
        val startOfDay = (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        
        val endOfDay = (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        CalendarUiState(
            appointments = appointments.filter { it.startTimeMs in startOfDay..endOfDay },
            members = members,
            staffList = staffList,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState(isLoading = true)
    )

    fun setDate(date: Calendar) {
        _selectedDate.value = date
    }

    fun addAppointment(memberId: Long, staffId: Long, hour: Int, trainingType: String) {
        viewModelScope.launch {
            val start = (_selectedDate.value.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
            }.timeInMillis
            
            val end = (_selectedDate.value.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour + 1)
                set(Calendar.MINUTE, 0)
            }.timeInMillis

            appointmentDao.insertAppointment(
                AppointmentEntity(
                    memberId = memberId,
                    staffId = staffId,
                    trainingType = trainingType,
                    startTimeMs = start,
                    endTimeMs = end
                )
            )
        }
    }

    fun updateAppointmentStatus(appointmentId: Long, status: String, notes: String) {
        viewModelScope.launch {
            completeAppointmentUseCase(appointmentId, status, notes)
        }
    }
}
