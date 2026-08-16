package com.gymapp.presentation.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.data.repository.PackageRepository
import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.Money
import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bir kez tüketilen kullanıcı bildirimleri. */
sealed interface PackageEvent {
    data object Saved : PackageEvent
    data object Deleted : PackageEvent
    data class Failed(val message: String) : PackageEvent
}

class PackageViewModel(
    private val repository: PackageRepository,
    prefs: AppPreferences,
) : ViewModel() {

    /**
     * Bu kullanıcı paket (fiyat listesi) yazabilir mi?
     *
     * Sunucu kuralı fiyat listesini salon sahibi ve yöneticiye açıyor
     * (migrasyon `0004`); eğitmenin paket fiyatını değiştirmesi için sebep yok,
     * satış yapabilmesi için de gerekmiyor. Buradaki kontrol, yetkisiz
     * kullanıcının işi yapıp sonucu ancak senkronizasyon turunda 403 olarak
     * öğrenmesini önlüyor.
     */
    val canWrite: Boolean = SyncTable.PACKAGES.isWritableBy(prefs.currentUserRole)

    val packages: StateFlow<List<PackageEntity>> = repository.getAllPackages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _events = Channel<PackageEvent>(Channel.BUFFERED)
    val events: Flow<PackageEvent> = _events.receiveAsFlow()

    /**
     * Paketi kaydeder.
     *
     * Kimlik ve zaman damgaları repository'de üretilir; ekran entity kurmaz.
     * Sonuç **her zaman** bildirilir — önceden hata sessizce yutuluyordu.
     *
     * @param sessionCount `null` ise sınırsız (abonman) paket.
     */
    fun savePackage(
        packageId: String? = null,
        name: String,
        type: TrainingType,
        category: PackageCategory,
        basePrice: Double,
        validityDays: Int,
        sessionCount: Int?,
    ) {
        // Ekranda düğme gizleniyor ama kontrol burada da var: gizlenmiş bir
        // düğme kural değil, yalnızca görüntü.
        if (!canWrite) {
            viewModelScope.launch { _events.send(PackageEvent.Failed(YETKI_YOK)) }
            return
        }

        viewModelScope.launch {
            repository.savePackage(
                packageId = packageId,
                name = name,
                type = type,
                category = category,
                basePrice = Money.ofMajor(basePrice),
                validityDays = validityDays,
                sessionCount = sessionCount,
            ).fold(
                onSuccess = { _events.send(PackageEvent.Saved) },
                onFailure = { _events.send(PackageEvent.Failed(it.message ?: "Paket kaydedilemedi.")) },
            )
        }
    }

    fun deletePackage(packageId: String) {
        if (!canWrite) {
            viewModelScope.launch { _events.send(PackageEvent.Failed(YETKI_YOK)) }
            return
        }
        viewModelScope.launch {
            repository.deletePackage(packageId).fold(
                onSuccess = { _events.send(PackageEvent.Deleted) },
                onFailure = { _events.send(PackageEvent.Failed(it.message ?: "Paket silinemedi.")) },
            )
        }
    }

    suspend fun getPackageById(id: String): PackageEntity? = repository.getPackageById(id)

    private companion object {
        const val YETKI_YOK = "Paketleri yalnızca salon sahibi ve yönetici değiştirebilir."
    }
}
