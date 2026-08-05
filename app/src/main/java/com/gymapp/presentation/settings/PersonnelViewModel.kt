package com.gymapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.repository.StaffRepository
import com.gymapp.domain.Money
import com.gymapp.domain.Rate
import com.gymapp.domain.StaffRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Bir kez tüketilen kullanıcı bildirimleri. */
sealed interface PersonnelEvent {
    data object Saved : PersonnelEvent
    data class Failed(val message: String) : PersonnelEvent
}

@HiltViewModel
class PersonnelViewModel @Inject constructor(
    private val repository: StaffRepository
) : ViewModel() {

    val staffList: Flow<List<StaffEntity>> = repository.getAllStaff()

    private val _events = Channel<PersonnelEvent>(Channel.BUFFERED)
    val events: Flow<PersonnelEvent> = _events.receiveAsFlow()

    /**
     * Personel ekler veya günceller.
     *
     * Sonuç **her zaman** bildirilir; önceki sürümde diyalog listenin uzunluğu
     * değişince kapanıyordu, dolayısıyla güncelleme başarılı olsa da kapanmıyor,
     * başarısız ekleme ise fark edilmiyordu.
     *
     * @param staffId `null` ise yeni personel.
     * @param commissionPercent hakediş **yüzdesi** (40 = %40); baz puana dönüşüm
     *        [Rate] içinde tek noktada yapılır.
     */
    fun saveStaff(
        staffId: String? = null,
        name: String,
        title: String,
        branch: String,
        commissionPercent: Double,
        salary: Double,
        phone: String,
        nickname: String,
        role: StaffRole,
        password: String? = null,
        isActive: Boolean = true,
    ) {
        viewModelScope.launch {
            repository.saveStaff(
                staffId = staffId,
                fullName = name,
                title = title,
                role = role,
                branch = branch,
                commissionBasisPoints = Rate.ofPercent(commissionPercent).basisPoints,
                monthlySalary = Money.ofMajor(salary),
                phone = phone,
                nickname = nickname,
                password = password?.takeIf { it.isNotBlank() },
                isActive = isActive,
            ).fold(
                onSuccess = { _events.send(PersonnelEvent.Saved) },
                onFailure = {
                    _events.send(PersonnelEvent.Failed(it.message ?: "Personel kaydedilemedi."))
                },
            )
        }
    }

    fun deleteStaff(staffId: String) {
        viewModelScope.launch {
            repository.deleteStaff(staffId)
        }
    }
}
