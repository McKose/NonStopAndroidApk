package com.gymapp.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.gymapp.data.security.PasswordHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gym_app_prefs", Context.MODE_PRIVATE)

    // Not: Hakediş oranları artık per-staff (StaffEntity.commissionRate) tutuluyor.
    // MultiSport seans ücreti time-versioned (MultiSportRateEntity) tutuluyor.
    // Legacy anahtarlar (commission_rate, multisport_commission) v9 migration sonrası
    // okunmuyor — silinmelerine gerek yok, prefs'te boş yere yer tutar ama zararsız.

    var salonName: String
        get() = prefs.getString("salon_name", "NonStop Gym") ?: "NonStop Gym"
        set(value) = prefs.edit().putString("salon_name", value).apply()

    /** Hash'lenmiş yönetici şifresi. İlk kullanımda varsayılan "1234" tohumlanır. */
    private var salonPasswordHash: String
        get() {
            val existing = prefs.getString("salon_password_hash", null)
            if (existing != null) return existing
            val seeded = PasswordHasher.hash(DEFAULT_ADMIN_PASSWORD)
            prefs.edit().putString("salon_password_hash", seeded).apply()
            return seeded
        }
        set(value) = prefs.edit().putString("salon_password_hash", value).apply()

    fun verifySalonPassword(password: String): Boolean =
        PasswordHasher.verify(password, salonPasswordHash)

    fun updateSalonPassword(newPassword: String) {
        require(newPassword.isNotBlank()) { "Şifre boş olamaz" }
        salonPasswordHash = PasswordHasher.hash(newPassword)
    }

    var currentUserRole: String
        get() = prefs.getString("current_user_role", "") ?: ""
        set(value) = prefs.edit().putString("current_user_role", value).apply()

    var currentUserId: Long
        get() = prefs.getLong("current_user_id", -1L)
        set(value) = prefs.edit().putLong("current_user_id", value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean("is_logged_in", false)
        set(value) = prefs.edit().putBoolean("is_logged_in", value).apply()

    companion object {
        const val DEFAULT_ADMIN_PASSWORD = "1234"
    }
}
