# NonStop Gym Management System

Modern ve kapsamlı bir spor salonu yönetim otomasyonu. Bu uygulama, spor salonu işletmecilerinin üye takibi, finansal yönetim, personel hakedişleri ve randevu sistemini tek bir platformdan yönetmesini sağlar.

## 🚀 Temel Özellikler

### 👥 Üye Yönetimi
- **Kayıt ve Profil:** Detaylı üye kaydı, iletişim bilgileri ve üyelik durumu.
- **Vücut Analizi:** Ölçüm geçmişi takibi ve görsel grafikler.
- **Postür Analizi:** Fotoğraflı postür analizi ve eğitmen yorumları.
- **Paket Yönetimi:** Üyelik paketleri, dondurma ve yenileme işlemleri.

### 💰 Finansal Yönetim (Dashboard)
- **Ciro Analizi:** Aylık, 3/6 Aylık ve Yıllık bazda ciro raporları.
- **Gider Takibi:** Manuel gider girişleri ve otomatik vergi kayıtları.
- **Vergi Otomasyonu:** Türkiye 2026 vergi dilimlerine göre KDV ve Gelir Vergisi matrah hesaplamaları.
- **Ödeme Yöntemleri:** Nakit, Kart ve MultiSport (Seans bazlı) ödeme takibi.

### 📅 Randevu ve Takvim
- **Ders Planlama:** Antrenör bazlı randevu takvimi.
- **MultiSport Entegrasyonu:** Seans bazlı otomatik ücretlendirme ve hakediş.

### 👤 Personel ve Ayarlar
- **Personel Takibi:** Branş bazlı personel yönetimi ve iletişim.
- **Hakediş Sistemi:** Eğitmenlere özel yüzdelik hakediş veya sabit maaş tanımlama.
- **Salon Özelleştirme:** Salon adı, taksit komisyonları ve MultiSport birim ücret ayarları.

## 🛠️ Teknik Altyapı
- **Dil:** Kotlin
- **Mimari:** MVVM (Model-View-ViewModel) + Clean Architecture
- **UI:** Jetpack Compose (Modern Declarative UI)
- **Dependency Injection:** Hilt
- **Veritabanı:** Room DB (Local persistence)
- **Navigasyon:** Jetpack Navigation Compose

## 📦 Kurulum ve Çalıştırma
1. Bu depoyu klonlayın: `git clone https://github.com/McKose/NonStopAndroidApk.git`
2. Android Studio (Ladybug veya sonrası) ile projeyi açın.
3. Gerekli SDK ve Gradle bağımlılıklarının yüklenmesini bekleyin.
4. Bir emülatör veya fiziksel cihaz üzerinden `Run` butonuna basın.

## 🔐 Güvenlik ve Oturum
- Uygulama, ilk girişte kullanıcı adı ve şifre gerektirir.
- Manuel çıkış yapılana kadar oturum cihazda saklanır (Uygulama kapatılsa dahi şifre tekrar sorulmaz).

## 📝 Lisans
Bu proje özel mülkiyet altındadır ve izinsiz kopyalanamaz veya dağıtılamaz.
