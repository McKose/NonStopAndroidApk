package com.gymapp.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.gymapp.domain.StaffRole
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

    /**
     * Oturumdaki kullanıcının yetki seviyesi.
     *
     * Önceden serbest metin (`"antrenör"`) tutuluyordu ve yetki kontrolleri bu
     * metnin birebir eşleşmesine bağlıydı; tek harflik bir fark sessizce yetki
     * veriyor ya da alıyordu. Tanınmayan değerde **en dar** yetkiye düşülür.
     */
    var currentUserRole: StaffRole
        get() = runCatching {
            StaffRole.valueOf(prefs.getString("current_user_role_v2", null) ?: "")
        }.getOrDefault(StaffRole.TRAINER)
        set(value) = prefs.edit().putString("current_user_role_v2", value.name).apply()

    /**
     * Oturumdaki kullanıcının kimliği; oturum yoksa boş.
     *
     * Anahtar bilinçli olarak yeni (`_v2`): kimlikler `Long`'dan `String`'e geçtiği için
     * eski anahtar `getString` ile okunduğunda `ClassCastException` fırlatırdı.
     */
    var currentUserId: String
        get() = prefs.getString("current_user_id_v2", "") ?: ""
        set(value) = prefs.edit().putString("current_user_id_v2", value).apply()

    /** Oturum kapatma — rol ve kimlik birlikte temizlenir. */
    fun clearSession() {
        prefs.edit()
            .remove("current_user_role_v2")
            .remove("current_user_id_v2")
            .apply()
    }
}
