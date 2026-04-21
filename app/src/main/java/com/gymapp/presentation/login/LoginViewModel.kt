package com.gymapp.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val staffDao: StaffDao,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun login(nickname: String, password: String, onLoginSuccess: () -> Unit) {
        viewModelScope.launch {
            if (nickname == "admin" && password == prefs.salonPassword) {
                prefs.currentUserRole = "admin"
                prefs.currentUserId = -1L
                onLoginSuccess()
                return@launch
            }

            val staff = staffDao.getStaffByNickname(nickname)
            if (staff != null && staff.password == password) {
                prefs.currentUserRole = staff.role
                prefs.currentUserId = staff.id
                onLoginSuccess()
            } else {
                _error.value = "Kullanıcı adı veya şifre hatalı!"
            }
        }
    }
}
