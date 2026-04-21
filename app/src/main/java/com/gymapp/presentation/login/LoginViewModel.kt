package com.gymapp.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.StaffDao
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.data.security.PasswordHasher
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
        val trimmedNickname = nickname.trim()
        if (trimmedNickname.isEmpty() || password.isEmpty()) {
            _error.value = "Kullanıcı adı ve şifre zorunludur."
            return
        }

        viewModelScope.launch {
            if (trimmedNickname.equals("admin", ignoreCase = true) &&
                prefs.verifySalonPassword(password)
            ) {
                prefs.currentUserRole = "admin"
                prefs.currentUserId = -1L
                _error.value = null
                onLoginSuccess()
                return@launch
            }

            val staff = staffDao.getStaffByNickname(trimmedNickname)
            if (staff != null && staff.isActive && PasswordHasher.verify(password, staff.password)) {
                prefs.currentUserRole = staff.role
                prefs.currentUserId = staff.id
                _error.value = null
                onLoginSuccess()
            } else {
                _error.value = "Kullanıcı adı veya şifre hatalı!"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
