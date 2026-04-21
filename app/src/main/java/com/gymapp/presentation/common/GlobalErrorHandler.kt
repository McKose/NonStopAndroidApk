package com.gymapp.presentation.common

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.sql.SQLException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalErrorHandler @Inject constructor() {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = _errors.asSharedFlow()

    suspend fun handleError(throwable: Throwable) {
        // Coroutine iptalini asla sessize alma; yukarı yayılmalı
        if (throwable is CancellationException) throw throwable

        val message = when (throwable) {
            is UnknownHostException -> "İnternet bağlantısı yok."
            is SocketTimeoutException -> "Bağlantı zaman aşımına uğradı."
            is SQLiteConstraintException -> "Kayıt mevcut veya bir kısıtlama ihlal edildi."
            is SQLException -> "Veritabanı hatası oluştu."
            is IOException -> "Giriş/çıkış hatası oluştu."
            is IllegalArgumentException -> throwable.localizedMessage ?: "Geçersiz değer."
            is IllegalStateException -> throwable.localizedMessage ?: "Geçersiz durum."
            is NullPointerException -> "Beklenmeyen boş değer."
            else -> throwable.localizedMessage ?: "Beklenmedik bir hata oluştu."
        }
        Log.e("GlobalErrorHandler", "Error: ${throwable.message}", throwable)
        _errors.emit(message)
    }

    suspend fun showMessage(message: String) {
        _errors.emit(message)
    }
}
