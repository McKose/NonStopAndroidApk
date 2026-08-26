// `Instant` kotlinx-datetime 0.7'den beri `kotlin.time.Instant`'a takma ad;
// asıl tip yazılıyor (bkz. Periods.kt) ve bu Kotlin sürümünde deneysel işaretli.
@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.gymapp.arayuz.takvim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.repository.AppointmentRepository
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.StaffRepository
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.Now
import com.gymapp.domain.Periods
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class CalendarUiState(
    val appointments: List<AppointmentEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val staffList: List<StaffEntity> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * Cihazın takvimine göre bugün.
 *
 * `LocalDate.now(zone)` karşılığı; kotlinx-datetime'da böyle bir kısayol yok,
 * saat okuma ile takvime çevirme ayrı adımlar. Saat tek noktadan ([Now])
 * okunuyor ki ileride test edilebilir bir saat enjekte etmek mümkün kalsın.
 */
private fun bugun(zone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(Now.epochMillis()).toLocalDateTime(zone).date

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

    private val zone: TimeZone = TimeZone.currentSystemDefault()

    /**
     * `Calendar` yerine `LocalDate`.
     *
     * `Calendar` değiştirilebilir olduğu için aynı nesne mutasyonla geri yazıldığında
     * `StateFlow` eşitlik kontrolünde değişiklik görmüyor ve ekran güncellenmiyordu.
     *
     * Tip artık `java.time.LocalDate` değil `kotlinx.datetime.LocalDate`; ikisinin
     * gün aritmetiği bu kullanım için aynı, ama ikincisi iOS'ta da derleniyor.
     */
    private val _selectedDate = MutableStateFlow(bugun(zone))

    /**
     * Seçili gün — **epoch milisaniye**, `LocalDate` değil.
     *
     * Ekran zaten epoch ms konuşuyor (bkz. `TakvimEkrani`), dolayısıyla tarih
     * tipini dışarı vermenin bir faydası yoktu; zararı vardı: gün ekleme/çıkarma
     * çağıranın işi oluyordu ve `MainActivity` bunu `java.time` ile yapıyordu.
     * Gün aritmetiği artık tamamen burada — ileri/geri/bugün üç metot.
     */
    val secilenGunMs: StateFlow<Long> = _selectedDate
        .map { it.atStartOfDayIn(zone).toEpochMilliseconds() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = _selectedDate.value.atStartOfDayIn(zone).toEpochMilliseconds(),
        )

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
        val day = Periods.dayOf(date.atStartOfDayIn(zone).toEpochMilliseconds())
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

    fun oncekiGun() {
        _selectedDate.update { it.minus(1, DateTimeUnit.DAY) }
    }

    fun sonrakiGun() {
        _selectedDate.update { it.plus(1, DateTimeUnit.DAY) }
    }

    fun bugune() {
        _selectedDate.value = bugun(zone)
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
            // Randevu süresi sabit bir saat. Bitiş, yerel saate 1 eklenerek
            // DEĞİL ana 1 saat eklenerek bulunuyor: yerel saatte +1, yaz saati
            // geçişinin olduğu günde ya iki saatlik ya sıfır saatlik bir randevu
            // üretir. Türkiye 2016'dan beri kalıcı UTC+3 olduğu için bugün fark
            // etmiyor, ama kural cihazın dilimine göre çalışıyor ve uygulama
            // yurt dışındaki bir telefonda da açılabilir.
            val basStamp = _selectedDate.value.atTime(hour, 0).toInstant(zone)
            val startMs = basStamp.toEpochMilliseconds()
            val endMs = basStamp.plus(1, DateTimeUnit.HOUR).toEpochMilliseconds()

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
