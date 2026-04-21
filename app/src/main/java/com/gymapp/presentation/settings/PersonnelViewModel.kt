package com.gymapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.security.PasswordHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonnelViewModel @Inject constructor(
    private val staffDao: StaffDao
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _lastCreatedTempPassword = MutableStateFlow<String?>(null)
    val lastCreatedTempPassword = _lastCreatedTempPassword.asStateFlow()

    val staffList: Flow<List<StaffEntity>> = staffDao.getAllStaff()

    fun addStaff(
        name: String,
        title: String,
        branch: String,
        rate: Double,
        salary: Double,
        phone: String,
        nickname: String,
        role: String,
        initialPassword: String? = null
    ) {
        if (name.isBlank() || nickname.isBlank() || phone.isBlank()) {
            _error.value = "Lütfen zorunlu alanları (Ad, Kullanıcı Adı, Telefon) doldurun."
            return
        }

        viewModelScope.launch {
            try {
                val existing = staffDao.getStaffByNickname(nickname.trim())
                if (existing != null) {
                    _error.value = "Bu kullanıcı adı zaten alınmış."
                    return@launch
                }

                val rawPassword = initialPassword?.takeIf { it.isNotBlank() }
                    ?: generateTempPassword()

                staffDao.insertStaff(
                    StaffEntity(
                        fullName = name.trim(),
                        title = title,
                        branch = branch,
                        hourlyRate = rate,
                        monthlySalary = salary,
                        phone = phone.trim(),
                        nickname = nickname.trim(),
                        role = role,
                        password = PasswordHasher.hash(rawPassword)
                    )
                )
                _error.value = null
                _lastCreatedTempPassword.value = rawPassword
            } catch (e: Exception) {
                _error.value = "Personel eklenirken bir hata oluştu: ${e.localizedMessage ?: ""}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearLastCreatedTempPassword() {
        _lastCreatedTempPassword.value = null
    }

    fun updateStaff(staff: StaffEntity) {
        viewModelScope.launch {
            staffDao.updateStaff(staff)
        }
    }

    fun resetStaffPassword(staff: StaffEntity): String {
        val newPassword = generateTempPassword()
        viewModelScope.launch {
            staffDao.updateStaff(staff.copy(password = PasswordHasher.hash(newPassword)))
        }
        _lastCreatedTempPassword.value = newPassword
        return newPassword
    }

    private fun generateTempPassword(): String {
        val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..8).map { chars.random() }.joinToString("")
    }
}
