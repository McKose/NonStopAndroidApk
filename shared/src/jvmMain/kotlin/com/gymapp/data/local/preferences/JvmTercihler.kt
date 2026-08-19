package com.gymapp.data.local.preferences

import java.util.prefs.Preferences

/**
 * [AppPreferences]'ın masaüstü (JVM) gerçeklemesi — `java.util.prefs` üzerinde.
 *
 * `Preferences` JVM'in kendi kalıcı anahtar-değer deposu: Windows'ta kayıt
 * defterine, Linux/macOS'ta kullanıcı dizinindeki dosyalara yazar. Ek
 * bağımlılık gerektirmediği için seçildi — masaüstü kabuğunun tek işi
 * ekranları göstermek, tercih saklama altyapısı kurmayı hak etmiyor.
 *
 * @param dugum test yalıtımı için değiştirilebilir; üretimde varsayılan
 *   kalmalı ki kullanıcının kaydettiği ad sürümler arasında kaybolmasın.
 */
class JvmTercihler(
    dugum: String = "com/gymapp",
) : AppPreferences {

    private val prefs: Preferences = Preferences.userRoot().node(dugum)

    override var salonName: String
        get() = prefs.get(AppPreferences.SALON_ADI_ANAHTARI, AppPreferences.VARSAYILAN_SALON_ADI)
        set(value) = prefs.put(AppPreferences.SALON_ADI_ANAHTARI, value)
}
