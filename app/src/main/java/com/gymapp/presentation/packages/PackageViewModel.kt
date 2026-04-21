package com.gymapp.presentation.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.repository.PackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PackageViewModel @Inject constructor(
    private val repository: PackageRepository
) : ViewModel() {

    val packages: StateFlow<List<PackageEntity>> = repository.getAllPackages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addPackage(
        id: Long = 0,
        name: String,
        type: String,
        category: String,
        basePrice: Double,
        validityDays: Int,
        sessionCount: Int
    ) {
        viewModelScope.launch {
            val pkg = PackageEntity(
                id = id,
                name = name,
                type = type,
                category = category,
                basePrice = basePrice,
                validityDays = validityDays,
                sessionCount = sessionCount,
                serviceId = 1L // Varsayılan değer
            )
            repository.insertPackage(pkg)
        }
    }

    fun deletePackage(pkg: PackageEntity) {
        viewModelScope.launch {
            repository.deletePackage(pkg)
        }
    }

    suspend fun getPackageById(id: Long) = repository.getPackageById(id)
}
