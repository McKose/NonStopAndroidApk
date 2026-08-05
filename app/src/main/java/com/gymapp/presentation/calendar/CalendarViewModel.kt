package com.gymapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.repository.AppointmentRepository
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.Membership
import com.gymapp.domain.Periods
import com.gymapp.domain.TrainingType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CalendarUiState(
    val appointments: List<AppointmentEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val staffList: List<StaffEntity> = emptyList(),
    val isLoading: Boolean = false
)

/** Bir kez tüketilen kullanıcı bildirimleri. */
sealed interface CalendarEvent {
    data object AppointmentSaved : CalendarEvent
    data object StatusUpdated : CalendarEvent
    data class Failed(val message: String) : CalendarEvent
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val appointmentRepository: AppointmentRepository,
    private val memberDao: MemberDao,
    private val staffDao: StaffDao,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * `Calendar` yerine `LocalDate`.
     *
     * `Calendar` değiştirilebilir olduğu için aynı nesne mutasyonla geri yazıldığında
     * `StateFlow` eşitlik kontrolünde değişiklik görmüyor ve ekran güncellenmiyordu.
     */
    private val _selectedDate = MutableStateFlow(LocalDate.now(zone))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _events = Channel<CalendarEvent>(Channel.BUFFERED)
    val events: Flow<CalendarEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<CalendarUiState> = combine(
        appointmentRepository.observeAll(),
        memberDao.getAllMembers(),
        staffDao.getAllStaff(),
        _selectedDate
    ) { appointments, members, staffList, date ->
        // Yarı açık gün aralığı: gün sınırındaki randevular artık kaybolmuyor.
        val day = Periods.day(date, zone)
        CalendarUiState(
            appointments = appointments.filter { it.startTimeMs in day },
            members = members,
            staffList = staffList,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState(isLoading = true)
    )

    fun setDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * Randevu oluşturur.
     *
     * Eklenen kontroller:
     *  - üyeliğin geçerli ve seans hakkının olması
     *  - seçilen eğitmenin o saatte başka randevusunun olmaması (çift rezervasyon)
     */
    fun addAppointment(memberId: Long, staffId: Long, hour: Int, trainingType: TrainingType) {
        viewModelScope.launch {
            val startDateTime = _selectedDate.value.atTime(hour, 0)
            val startMs = startDateTime.atZone(zone).toInstant().toEpochMilli()
            val endMs = startDateTime.plusHours(1).atZone(zone).toInstant().toEpochMilli()

            val member = memberDao.getMemberById(memberId)
            if (member == null) {
                _events.send(CalendarEvent.Failed("Üye bulunamadı."))
                return@launch
            }
            if (!Membership.canBookSession(
                    storedStatus = member.status,
                    endDateMs = member.endDateMs,
                    remainingSessions = member.remainingSessions,
                    nowMs = System.currentTimeMillis()
                )
            ) {
                _events.send(
                    CalendarEvent.Failed("${member.fullName}: üyelik aktif değil ya da seans hakkı kalmadı.")
                )
                return@launch
            }

            if (appointmentRepository.hasOverlap(staffId, startMs, endMs)) {
                _events.send(CalendarEvent.Failed("Seçilen eğitmenin bu saatte başka randevusu var."))
                return@launch
            }

            appointmentRepository.create(
                memberId = memberId,
                staffId = staffId,
                trainingType = trainingType,
                startTimeMs = startMs,
                endTimeMs = endMs,
            ).fold(
                onSuccess = { _events.send(CalendarEvent.AppointmentSaved) },
                onFailure = { _events.send(CalendarEvent.Failed(it.message ?: "Randevu oluşturulamadı.")) },
            )
        }
    }

    fun updateAppointmentStatus(appointmentId: String, state: AppointmentState, notes: String) {
        viewModelScope.launch {
            appointmentRepository.processStatus(
                appointmentId = appointmentId,
                state = state,
                notes = notes.takeIf { it.isNotBlank() },
            ).fold(
                onSuccess = { _events.send(CalendarEvent.StatusUpdated) },
                onFailure = { _events.send(CalendarEvent.Failed(it.message ?: "Randevu güncellenemedi.")) }
            )
        }
    }
}
