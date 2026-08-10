package com.gymapp.presentation.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.repository.PackageRepository
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
    data class Failed(val message: String) : PackageEvent
}

class PackageViewModel(
    private val repository: PackageRepository
) : ViewModel() {

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
        viewModelScope.launch {
            repository.deletePackage(packageId)
        }
    }

    suspend fun getPackageById(id: String): PackageEntity? = repository.getPackageById(id)
}
