package com.gymapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.repository.AppointmentRepository
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.StaffRepository
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.Periods
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

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

class CalendarViewModel(
    private val appointmentRepository: AppointmentRepository,
    memberRepository: MemberRepository,
    staffRepository: StaffRepository,
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
        memberRepository.getAllMembers(),
        staffRepository.getAllStaff(),
        _selectedDate
    ) { appointments, members, staffList, date ->
        // Yarı açık gün aralığı: gün sınırındaki randevular artık kaybolmuyor.
        //
        // Domain katmanı ortak koda (KMP) taşındığı için tarihleri **epoch millis**
        // olarak konuşuyor; `java.time` bu ekranın kendi sınırında kalıyor.
        val day = Periods.dayOf(date.atStartOfDay(zone).toInstant().toEpochMilli())
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
     * Uygunluk kuralları (üyelik geçerli mi, seans hakkı var mı, eğitmen o saatte
     * boş mu) repository içinde kayıtla aynı transaction'da doğrulanır; burada
     * yalnızca sonucun kullanıcıya bildirilmesi kalıyor.
     */
    fun addAppointment(memberId: String, staffId: String, hour: Int, trainingType: TrainingType) {
        viewModelScope.launch {
            val startDateTime = _selectedDate.value.atTime(hour, 0)
            val startMs = startDateTime.atZone(zone).toInstant().toEpochMilli()
            val endMs = startDateTime.plusHours(1).atZone(zone).toInstant().toEpochMilli()

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
