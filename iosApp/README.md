# iOS kabuğu

Uygulamanın iOS tarafı. Burada **ekran, gezinme ve iş kuralı yok** — hepsi
ortak Kotlin modüllerinde (`:arayuz` ve `:shared`) ve Android ile birebir aynı
kod. Bu klasördeki üç dosyanın tek işi bir pencere açıp ortak arayüzü içine
koymak.

## Açmak

```sh
brew install xcodegen        # bir kereye mahsus
cd iosApp
xcodegen generate
open GymApp.xcodeproj
```

`GymApp.xcodeproj` **depoya işlenmiyor**, `project.yml`den üretiliyor. Sebebi
`project.yml`nin başında yazılı; özeti: `project.pbxproj` elle okunamayan
kimliklerle dolu ve birleştirme çatışmalarında pratikte çözülemiyor.

Xcode'da derlerken ortak framework Gradle ile otomatik üretiliyor (proje
içindeki "Ortak framework'ü derle" adımı). Ayrı bir komut çalıştırmak
gerekmiyor.

## Sunucu ayarları

`SUPABASE_URL` ve `SUPABASE_ANON_KEY` derleme ayarı olarak geliyor ve depoya
işlenmiyor — Android'deki `local.properties` düzeninin aynısı. Xcode'da
şemanın build ayarlarına girilebilir, ya da:

```sh
xcodegen generate
xcodebuild -project GymApp.xcodeproj -scheme GymApp -sdk iphonesimulator \
  SUPABASE_URL=https://... SUPABASE_ANON_KEY=... build
```

Boş bırakılırlarsa uygulama **açılıyor ama sunucuya bağlanmıyor**: giriş ekranı
neyin eksik olduğunu yazıyor. Bu bilinçli — eksik ayarda çökmek sebebi yığın
izinde bırakırdı.

## Bilinen eksik: oturum kalıcı değil

Android'de oturum Keystore ile şifreli saklanıyor. iOS'ta karşılığı Keychain
ve o gerçekleme **henüz yok**; şimdilik bellekte tutuluyor, yani uygulama
kapanınca yeniden giriş gerekiyor.

`NSUserDefaults` alternatif değil: şifresiz ve iCloud yedeklemesine dahil.
TestFlight'a çıkmadan önce yapılacak.

## İmza

Simülatör derlemesi sertifika istemiyor; CI de imzasız derliyor. Apple
Developer üyeliği yalnızca **gerçek cihaza yükleme** ve TestFlight için
gerekiyor.
