package com.gymapp.data.local.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * [AppPreferences]'ın Android gerçeklemesi — `SharedPreferences` üzerinde.
 *
 * Dosya adı (`gym_app_prefs`) uygulamanın ilk sürümünden beri aynı ve öyle
 * kalmalı: değişseydi mevcut cihazlardaki salon adı sessizce varsayılana
 * dönerdi.
 *
 * Bu sınıf `app` modülünden buraya, `shared/androidMain`'e taşındı (i2).
 * Taşınırken alan eklenmedi; dosyanın eski hâlindeki "neler kaldırıldı"
 * gerekçeleri arayüzün KDoc'unda özetleniyor: oturum kopyaları ve okunmayan
 * ayarlar bilinçli olarak yok, yenisi eklenmeden önce "cihaza mı kullanıcıya
 * mı ait" sorusu sorulmalı.
 */
class AndroidTercihler(context: Context) : AppPreferences {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gym_app_prefs", Context.MODE_PRIVATE)

    override var salonName: String
        get() = prefs.getString(AppPreferences.SALON_ADI_ANAHTARI, null)
            ?: AppPreferences.VARSAYILAN_SALON_ADI
        set(value) = prefs.edit().putString(AppPreferences.SALON_ADI_ANAHTARI, value).apply()
}
