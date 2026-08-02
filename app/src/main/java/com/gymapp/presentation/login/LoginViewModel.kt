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

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    fun login(nickname: String, password: String, onLoginSuccess: () -> Unit) {
        if (_isSubmitting.value) return // çift tıklama koruması

        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null // önceki denemenin hatası ekranda kalmasın
            try {
                // Kullanıcı adı büyük/küçük harf ve boşluğa duyarlı olmamalı.
                val user = nickname.trim()

                if (user.equals(ADMIN_USER, ignoreCase = true) && password == prefs.salonPassword) {
                    prefs.currentUserRole = "admin"
                    prefs.currentUserId = ADMIN_USER_ID
                    onLoginSuccess()
                    return@launch
                }

                val staff = staffDao.getStaffByNickname(user)
                // NOT (Faz 1): şifreler hash'lenerek saklanmalı ve karşılaştırma
                // sabit zamanlı olmalı; kimlik doğrulama sunucuya taşınacak.
                if (staff != null && staff.isActive && staff.password == password) {
                    prefs.currentUserRole = staff.role
                    prefs.currentUserId = staff.id
                    onLoginSuccess()
                } else {
                    _error.value = "Kullanıcı adı veya şifre hatalı!"
                }
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    private companion object {
        const val ADMIN_USER = "admin"
        const val ADMIN_USER_ID = -1L
    }
}
