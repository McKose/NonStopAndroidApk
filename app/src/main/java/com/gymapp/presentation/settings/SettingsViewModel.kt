package com.gymapp.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.local.preferences.AppPreferences
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val sessions: SessionManager,
) : ViewModel() {

    var commissionRate by mutableStateOf(prefs.commissionRate)
    var multiSportCommission by mutableStateOf(prefs.multiSportCommission)
    var salonName by mutableStateOf(prefs.salonName)

    fun updateCommissionRate(value: Float) {
        commissionRate = value
        prefs.commissionRate = value
    }

    fun updateMultiSportCommission(value: Float) {
        multiSportCommission = value
        prefs.multiSportCommission = value
    }

    fun updateSalonName(value: String) {
        salonName = value
        prefs.salonName = value
    }

    /**
     * Çıkış — oturum hem sunucu tarafında hem cihazda kapatılıyor.
     *
     * `prefs.clearSession()` tek başına yetmez: o yalnızca rol ve personel
     * kimliğini siliyor, jeton ve salon kimliği [SessionManager]'da duruyordu.
     * Yarım bir çıkış, giriş ekranına dönmüş ama hâlâ veri gönderebilen bir
     * uygulama demek olurdu.
     */
    fun logout(onLogout: () -> Unit) {
        viewModelScope.launch {
            sessions.signOut()
            prefs.clearSession()
            onLogout()
        }
    }
}
