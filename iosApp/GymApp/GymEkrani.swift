import SwiftUI
import UIKit
import GymApp

/// Ortak Compose arayüzünü SwiftUI'ya bağlar.
///
/// Kotlin tarafındaki tek giriş noktası `GymUygulamasiViewController`; Koin,
/// oturum yönetimi, gezinme ve ekran modelleri Swift'e hiç geçmiyor. Sebebi
/// o fonksiyonun KDoc'unda: Kotlin/Native köprüsünden geçen her tip Swift'te
/// kullanımı zorlaşan bir karşılığa dönüşüyor.
struct GymEkrani: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        // `IosGirisKt` — sınıf adı FONKSİYONDAN değil, Kotlin DOSYASINDAN
        // türüyor: `IosGiris.kt` -> `IosGirisKt`. Kotlin/Native üst düzey
        // fonksiyonları böyle sarmalıyor. Dolayısıyla o dosyanın adı
        // değiştirilirse bu satır da değişmeli; Swift derleyicisi hatayı
        // "unresolved identifier" olarak verir.
        IosGirisKt.GymUygulamasiViewController(
            supabaseUrl: Ayarlar.supabaseUrl,
            supabaseAnonKey: Ayarlar.supabaseAnonKey
        )
    }

    /// Güncelleme yok: durumun tamamı Compose tarafında yaşıyor ve SwiftUI'nın
    /// aktaracağı bir şey bulunmuyor.
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Derleme sırasında enjekte edilen sunucu ayarları.
///
/// Değerler `Info.plist`ten okunuyor, oraya da `project.yml`deki derleme
/// ayarlarından geliyor ve depoya İŞLENMİYOR — Android'deki `local.properties`
/// düzeninin aynısı.
///
/// Eksik olmaları uygulamayı düşürmüyor: ortak `supabaseModule` o durumda ne
/// yapılması gerektiğini söyleyen karşılıkları bağlıyor ve giriş ekranı sebebi
/// yazıyor. Burada hata fırlatmak, uygulamayı açılışta düşürür ve sebebi yığın
/// izinde bırakırdı.
private enum Ayarlar {
    static var supabaseUrl: String { plistDegeri("SUPABASE_URL") }
    static var supabaseAnonKey: String { plistDegeri("SUPABASE_ANON_KEY") }

    private static func plistDegeri(_ anahtar: String) -> String {
        (Bundle.main.object(forInfoDictionaryKey: anahtar) as? String) ?? ""
    }
}
