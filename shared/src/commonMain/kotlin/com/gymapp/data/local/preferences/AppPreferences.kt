package com.gymapp.data.local.preferences

/**
 * Cihaza özel görünüm tercihleri.
 *
 * Tek alan kaldı ve bu bilinçli: oturum kopyaları (`currentUserRole`,
 * `currentUserId`), okunmayan ayarlar (`commissionRate`, `salonPassword`)
 * önceki fazlarda tek tek söküldü — gerekçeleri Android gerçeklemesinin
 * dosyasında duruyor. Buraya yeni alan eklemeden önce sorulacak soru:
 * bu bilgi CİHAZA mı ait, KULLANICIYA mı? Kullanıcıya aitse yeri burası
 * değil, sunucu.
 *
 * Arayüz `shared`'da çünkü Ayarlar ekranı i3'te ortak arayüze taşınacak ve
 * `SharedPreferences`'ı (Android'e özgü) ortak koddan çağıramaz. Desen
 * [com.gymapp.data.auth.SessionStore] ile aynı: ortak arayüz, platform
 * gerçeklemesi, bağlamayı DI yapar.
 */
interface AppPreferences {
    var salonName: String

    companion object {
        /** Üç platform gerçeklemesinin ortak varsayılanı. */
        const val VARSAYILAN_SALON_ADI = "NonStop Gym"

        /** Tercih anahtarı — üç platformda da aynı ad kullanılıyor. */
        const val SALON_ADI_ANAHTARI = "salon_name"
    }
}
