package com.gymapp.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gym_app_prefs", Context.MODE_PRIVATE)

    var commissionRate: Float
        get() = prefs.getFloat("commission_rate", 10f)
        set(value) = prefs.edit().putFloat("commission_rate", value).apply()

    var multiSportCommission: Float
        get() = prefs.getFloat("multisport_commission", 15f)
        set(value) = prefs.edit().putFloat("multisport_commission", value).apply()

    var salonName: String
        get() = prefs.getString("salon_name", "NonStop Gym") ?: "NonStop Gym"
        set(value) = prefs.edit().putString("salon_name", value).apply()

    var salonPassword: String
        get() = prefs.getString("salon_password", "1234") ?: "1234"
        set(value) = prefs.edit().putString("salon_password", value).apply()

    var currentUserRole: String
        get() = prefs.getString("current_user_role", "") ?: ""
        set(value) = prefs.edit().putString("current_user_role", value).apply()

    var currentUserId: Long
        get() = prefs.getLong("current_user_id", -1L)
        set(value) = prefs.edit().putLong("current_user_id", value).apply()
}
