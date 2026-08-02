package com.gymapp.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.dao.InstallmentCommissionDao
import com.gymapp.data.local.dao.MultiSportRateDao
import com.gymapp.data.local.entity.InstallmentCommissionEntity
import com.gymapp.data.local.entity.MultiSportRateEntity
import com.gymapp.data.local.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val installmentDao: InstallmentCommissionDao,
    private val multiSportRateDao: MultiSportRateDao
) : ViewModel() {

    var salonName by mutableStateOf(prefs.salonName)

    // ─── Taksit komisyonları (1..12) ─────────────────────────────────────────
    val installmentRates: StateFlow<List<InstallmentCommissionEntity>> =
        installmentDao.getAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun updateInstallmentRate(installmentCount: Int, ratePercent: Double) {
        viewModelScope.launch {
            installmentDao.upsert(
                InstallmentCommissionEntity(
                    installmentCount = installmentCount,
                    ratePercent = ratePercent
                )
            )
        }
    }

    // ─── MultiSport seans ücreti (tarihli) ───────────────────────────────────
    val multiSportHistory: StateFlow<List<MultiSportRateEntity>> =
        multiSportRateDao.getAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setMultiSportRate(newAmount: Double, note: String? = null) {
        viewModelScope.launch {
            multiSportRateDao.supersede(newAmount, note)
        }
    }

    // ─── Genel ───────────────────────────────────────────────────────────────
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
        prefs.isLoggedIn = false
        onLogout()
    }
}
