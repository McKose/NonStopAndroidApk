package com.gymapp.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.gymapp.domain.StaffRole

class AppPreferences(
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gym_app_prefs", Context.MODE_PRIVATE)

    // KALDIRILDI: `commissionRate` / `multiSportCommission`. Yalnızca Ayarlar
    // ekranı yazıp okuyordu; gerçek hakediş hesabı `staff.commissionBasisPoints`
    // üzerinden yapılıyor (`AppointmentRepository`). Salon sahibi buradan oranı
    // değiştirdiğinde hiçbir hesap değişmiyor, ama değiştirdiğini sanıyordu.
    // Okunmayan bir ayarı tutmak, ileride birinin ona güvenmesi riskini canlı
    // tutardı — `salonPassword` da tam olarak bu yüzden kaldırılmıştı.

    var salonName: String
        get() = prefs.getString("salon_name", "NonStop Gym") ?: "NonStop Gym"
        set(value) = prefs.edit().putString("salon_name", value).apply()

    // KALDIRILDI: `salonPassword`. Giriş Supabase Auth'a taşındı; bu tercih
    // hiçbir yerde okunmuyordu ve varsayılanı "1234" idi. Okunmayan bir şifre
    // alanını tutmak, ileride birinin ona güvenmesi riskini canlı tutardı.

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
