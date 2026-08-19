package com.gymapp.data.local.preferences

import platform.Foundation.NSUserDefaults

/**
 * [AppPreferences]'ın iOS gerçeklemesi — `NSUserDefaults` üzerinde.
 *
 * `NSUserDefaults`, `SharedPreferences`'ın birebir karşılığı: uygulamaya özel,
 * anahtar-değer, cihazda kalıcı. Saklanan tek şey salon adı — hassas veri
 * DEĞİL; oturum jetonu gibi hassas şeylerin iOS'taki yeri Keychain olacak
 * (bkz. `SessionStore` KDoc'u), buraya asla konmamalı.
 *
 * Anahtar adı Android'le aynı tutuldu. Cihazlar arası bir taşıma yok — iki
 * platform ayrı cihazlar — ama aynı kavramın iki ayrı adla yaşaması, ileride
 * "iOS'ta neden farklı" sorusunu doğururdu.
 */
class IosTercihler : AppPreferences {

    private val defaults = NSUserDefaults.standardUserDefaults

    override var salonName: String
        get() = defaults.stringForKey(AppPreferences.SALON_ADI_ANAHTARI)
            ?: AppPreferences.VARSAYILAN_SALON_ADI
        set(value) = defaults.setObject(value, forKey = AppPreferences.SALON_ADI_ANAHTARI)
}
