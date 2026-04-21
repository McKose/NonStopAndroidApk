package com.gymapp.presentation.common

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalErrorHandler @Inject constructor() {
    private val _errors = MutableSharedFlow<String>()
    val errors = _errors.asSharedFlow()

    suspend fun handleError(throwable: Throwable) {
        val message = when (throwable) {
            is java.net.UnknownHostException -> "İnternet bağlantısı yok."
            is java.sql.SQLException -> "Veritabanı hatası oluştu."
            else -> throwable.localizedMessage ?: "Beklenmedik bir hata oluştu."
        }
        Log.e("GlobalErrorHandler", "Error: ${throwable.message}", throwable)
        _errors.emit(message)
    }

    suspend fun showMessage(message: String) {
        _errors.emit(message)
    }
}
