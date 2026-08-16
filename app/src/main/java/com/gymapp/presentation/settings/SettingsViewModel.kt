package com.gymapp.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.sync.SyncCoordinator
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val sessions: SessionManager,
    private val sync: SyncCoordinator,
    private val arkaPlan: ArkaPlanSenkronizasyonu,
    private val database: GymDatabase,
    private val syncQueue: SyncQueue,
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

    // KALDIRILDI: `commissionRate` / `multiSportCommission`. Yalnızca Ayarlar
    // ekranının kendisi yazıp okuyordu; gerçek hakediş `staff.commissionBasisPoints`
    // üzerinden hesaplanıyor. Kullanıcı oranı değiştirdiğini sanıyor, hiçbir
    // hesap değişmiyordu.
    var salonName by mutableStateOf(prefs.salonName)

    fun updateSalonName(value: String) {
        salonName = value
        prefs.salonName = value
    }

    /**
     * Çıkışta gönderilmemiş değişiklik sayısı; `null` ise soru sorulmuyor.
     *
     * Cihazdaki veri çıkışta siliniyor (aşağıya bakın). Kuyrukta bekleyen kayıt
     * varken bunu sessizce yapmak, kullanıcının henüz hiçbir yere ulaşmamış
     * işini yok etmek olurdu — o yüzden önce sayı gösteriliyor.
     */
    private val _cikistaBekleyen = MutableStateFlow<Int?>(null)
    val cikistaBekleyen: StateFlow<Int?> = _cikistaBekleyen.asStateFlow()

    /**
     * Çıkış isteği. Kuyruk doluysa önce soruyor, boşsa doğrudan çıkıyor.
     */
    fun requestLogout(onLogout: () -> Unit) {
        viewModelScope.launch {
            // Sayı çıkıştan ÖNCE okunuyor: `signOut` salon kimliğini siliyor ve
            // sonrasında kuyruk sorgulanamaz.
            val bekleyen = runCatching { syncQueue.pendingCount() }.getOrDefault(0)
            if (bekleyen > 0) _cikistaBekleyen.value = bekleyen else performLogout(onLogout)
        }
    }

    fun cancelLogout() {
        _cikistaBekleyen.value = null
    }

    fun confirmLogout(onLogout: () -> Unit) {
        _cikistaBekleyen.value = null
        viewModelScope.launch { performLogout(onLogout) }
    }

    /**
     * Çıkış — oturum hem sunucu tarafında hem cihazda kapatılıyor, **ve yerel
     * veritabanı temizleniyor.**
     *
     * Oturuma ait tek durum artık [SessionManager]'da: rol ve personel kimliği
     * için ayrıca temizlenmesi gereken bir tercih kalmadı (ikisi de oturumdan
     * türetiliyor, bkz. `CurrentUser`). Eskiden `prefs.clearSession()` da
     * çağrılıyordu ve o tek başına yetmiyordu — jeton ile salon kimliği
     * burada duruyor. Yarım bir çıkış, giriş ekranına dönmüş ama hâlâ veri
     * gönderebilen bir uygulama demek olurdu.
     *
     * ### Veritabanı neden temizleniyor
     * Temizlenmediğinde cihazda kalan satırlar bir sonraki kullanıcıya açıktı.
     * Somut: ADMIN giriş yapar, personel tablosunu (maaşlar dahil — ekranda
     * gösteriliyor) ve defteri indirir, çıkar; aynı cihazda TRAINER giriş yapar
     * ve bu satırları **salt okunur olarak görür**. Rol kontrolü yalnızca yazma
     * düğmelerini gizliyordu, veriyi değil. Sunucu tarafındaki erişim kuralları
     * doğru çalışıyordu; sızıntı tamamen cihazda kalan kopyadaydı.
     */
    private suspend fun performLogout(onLogout: () -> Unit) {
        // Önce arka plan işi duruyor: bırakılsaydı çıkış yapmış cihaz 15
        // dakikada bir uyanıp "oturum yok" deyip geri yatardı. Ayrıca yarıda
        // kalmış bir turun temizlenen tabloya yazmasını da engelliyor.
        arkaPlan.durdur()
        // Room'un `clearAllTables`'ı ortak (KMP) yüzeyde yok; silme ortak kodda
        // açıkça yazıldı ki iOS'ta da geçerli olsun ve testi gerçek SQLite
        // üzerinde koşabilsin. Askıya alınabilir olduğu için Room kendi iş
        // parçacığına geçiyor — ana iş parçacığı bloke olmuyor.
        database.maintenanceDao().wipeAll()
        sessions.signOut()
        onLogout()
    }
}
