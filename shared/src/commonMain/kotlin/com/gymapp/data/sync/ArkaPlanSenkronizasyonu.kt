package com.gymapp.data.sync

/**
 * Arka plan senkronizasyonunun planlanması — platformun iş zamanlayıcısına
 * açılan kapı.
 *
 * Arayüz `shared`'da çünkü çağıranlar (giriş, çıkış, açılış akışları) i3'te
 * ortak koda taşınacak ve `WorkManager`'ı (Android'e özgü) ortak koddan
 * çağıramazlar. Turun KENDİSİ zaten ortak: [SyncCoordinator] koşuyor,
 * [BackgroundSyncResult] karara bağlıyor. Platforma özgü kalan tek şey
 * "uygulama kapalıyken beni uyandır" kaydı — bu arayüz yalnızca onu soyutluyor.
 *
 * Gerçeklemeler:
 *  - Android: `WorkManagerSenkronizasyonu` (app modülünde) — 15 dakikada bir,
 *    ağ varken.
 *  - iOS ve masaüstü: [ArkaPlanYok] — aşağıya bakın.
 */
interface ArkaPlanSenkronizasyonu {
    /** Planlamayı kurar. Tekrar çağrılması güvenli: girişte ve oturum geri yüklendiğinde çağrılıyor. */
    fun baslat()

    /** Planlamayı kaldırır. Çıkışta çağrılıyor — çıkmış cihazın uyanıp "oturum yok" demesi boşa pil. */
    fun durdur()
}

/**
 * Hiçbir şey planlamayan gerçekleme — iOS ve masaüstü bunu kullanıyor.
 *
 * Bu bir eksik değil, VERİLMİŞ bir karar (bkz. docs/ios-plani.md, madde 3):
 *
 * iOS'ta arka plan çalışması (`BGTaskScheduler`) sistemin insafına kalmıştır —
 * Android'in "en geç 15 dakikada bir" güvencesinin karşılığı yoktur; sistem
 * işi saatlerce erteleyebilir ya da hiç çağırmayabilir. Güvenilmez bir kanala
 * güvenilir süs vermek, "telefon cebindeyken de eşitleniyor" diye VAR OLMAYAN
 * bir garantiyi vaat etmek olurdu. Bunun yerine vaat daraltıldı: uygulama
 * öndeyken senkronizasyon zaten dakikada bir koşuyor (bkz. docs/kararlar.md) —
 * iOS'ta geçerli olan yalnızca bu.
 *
 * Masaüstünde ise uygulama penceresi kapandığında süreç biter; "uygulama
 * kapalıyken koş" kavramının karşılığı yok.
 *
 * İleride biri iOS'a `BGTaskScheduler` eklemek isterse yeri bu arayüzün iOS
 * kabuğundaki yeni bir gerçeklemesidir — bu nesne "elimizden gelen bu" demek
 * için değil, sınırı dürüstçe çizmek için var.
 */
object ArkaPlanYok : ArkaPlanSenkronizasyonu {
    override fun baslat() = Unit
    override fun durdur() = Unit
}
