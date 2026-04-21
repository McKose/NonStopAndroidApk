package com.gymapp.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.gymapp.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences
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

    fun updateSalonPassword(newPassword: String) {
        prefs.updateSalonPassword(newPassword)
    }

    fun logout(onLogout: () -> Unit) {
        prefs.currentUserRole = ""
        prefs.currentUserId = -1L
        onLogout()
    }
}
