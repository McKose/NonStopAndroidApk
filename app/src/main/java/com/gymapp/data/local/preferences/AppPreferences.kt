package com.gymapp.data.local.preferences

import android.content.Context
import android.content.SharedPreferences

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

    // KALDIRILDI: `currentUserRole` / `currentUserId` ve `clearSession`.
    //
    // İkisi de oturumun cihazdaki **kopyasıydı** ve yalnızca girişte
    // yazılıyordu. Kopya olmaları tek başına sorun değil; yalnızca girişte
    // yazılmaları sorundu: uygulama açılışında oturum `SessionManager.restore()`
    // ile geri yükleniyor ve o yol buraya hiç dokunmuyordu. Sunucuda rolü
    // düşürülen kullanıcı, cihazda giriş ekranından geçmediği sürece eski
    // yetkisiyle çalışmaya devam ediyordu.
    //
    // Rol artık tek kaynaktan ve **tepkili** okunuyor: `CurrentUser`. Personel
    // kimliği de orada, üstelik akış olarak — bkz. `StaffLink`.
    //
    // Bu dosyada oturuma ait hiçbir şey kalmadı; geriye yalnızca cihaza özel
    // görünüm tercihi (`salonName`) kaldı, o da çıkışta silinmiyor çünkü
    // kullanıcıya değil cihaza ait.
}
