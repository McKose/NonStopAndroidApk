package com.gymapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.entity.StaffEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonnelViewModel @Inject constructor(
    private val staffDao: StaffDao
) : ViewModel() {

    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val staffList: Flow<List<StaffEntity>> = staffDao.getAllStaff()

    /** @param commissionFraction hakediş **kesri** (0.40 = %40) — ekran yüzdeyi burada çevirir. */
    fun addStaff(name: String, title: String, branch: String, commissionFraction: Double, salary: Double, phone: String, nickname: String, role: String) {
        if (name.isBlank() || nickname.isBlank() || phone.isBlank()) {
            _error.value = "Lütfen zorunlu alanları (Ad, Kullanıcı Adı, Telefon) doldurun."
            return
        }

        viewModelScope.launch {
            try {
                val existing = staffDao.getStaffByNickname(nickname)
                if (existing != null) {
                    _error.value = "Bu kullanıcı adı zaten alınmış."
                    return@launch
                }

                staffDao.insertStaff(
                    StaffEntity(
                        fullName = name,
                        title = title,
                        branch = branch,
                        // DÜZELTME: hakediş oranı daha önce hourlyRate'e yazılıyor, hesapta ise
                        // commissionRate okunuyordu; bu yüzden hakediş her zaman 0 çıkıyordu.
                        commissionRate = commissionFraction,
                        monthlySalary = salary,
                        phone = phone,
                        nickname = nickname,
                        role = role,
                        // NOT (Faz 1): şifreler hash'lenmeli ve ilk girişte değiştirme zorunlu olmalı.
                        password = DEFAULT_STAFF_PASSWORD
                    )
                )
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Personel eklenirken bir hata oluştu."
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun updateStaff(staff: StaffEntity) {
        viewModelScope.launch {
            // Kullanıcı adı başka bir personele aitse güncellemeyi reddet.
            val clash = staffDao.getStaffByNickname(staff.nickname)
            if (clash != null && clash.id != staff.id) {
                _error.value = "Bu kullanıcı adı zaten alınmış."
                return@launch
            }
            staffDao.updateStaff(staff)
            _error.value = null
        }
    }

    private companion object {
        const val DEFAULT_STAFF_PASSWORD = "123"
    }
}
