package com.gymapp.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.sync.SyncCoordinator
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val sessions: SessionManager,
    private val sync: SyncCoordinator,
    private val arkaPlan: ArkaPlanSenkronizasyonu,
    syncQueue: SyncQueue,
) : ViewModel() {

    /**
     * Senkronizasyonun durumu ve bekleyen değişiklik sayısı.
     *
     * Ekranda gösterilmesinin sebebi teknik merak değil: görünmez bir
     * senkronizasyon, çalışmadığında da çalışıyormuş gibi görünür. Bekleyen
     * sayının uzun süre düşmemesi, kullanıcının fark edebileceği tek belirti.
     */
    val syncState: StateFlow<SyncState> = sync.state

    val pendingCount: StateFlow<Int> = syncQueue.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun syncNow() {
        sync.requestSync()
    }

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
            // Arka plan işi de duruyor: bırakılsaydı çıkış yapmış cihaz 15
            // dakikada bir uyanıp "oturum yok" deyip geri yatardı.
            arkaPlan.durdur()
            onLogout()
        }
    }
}
