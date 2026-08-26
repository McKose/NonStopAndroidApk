import SwiftUI

/// Uygulamanın iOS girişi.
///
/// Kabuk bilinçli olarak İNCE: burada ekran, gezinme ya da iş kuralı yok.
/// Hepsi ortak Kotlin modülünde (`:arayuz`) ve Android ile birebir aynı kod.
/// Bu dosyanın tek işi bir pencere açıp ortak arayüzü içine koymak.
@main
struct GymAppApp: App {
    var body: some Scene {
        WindowGroup {
            GymEkrani()
                // Compose kendi güvenli alan yönetimini yapıyor; SwiftUI de
                // ayrıca uygularsa üstte ve altta çift boşluk oluşuyor.
                .ignoresSafeArea()
        }
    }
}
