# iOS planı — Android uygulamasını iPhone'a taşımak

**Hedef:** salon yöneticisi, uygulamanın tamamını (üye, paket, randevu, market,
finans) iPhone 14 Pro'da kullanabilsin. Web paneli bu işin karşılığı değil —
panel bilinçli olarak yalnızca üç tabloya yazıyor; para kuralları ortak Kotlin
modülünde ve orada kalacak.

**Yöntem:** Compose Multiplatform. Ekranlar zaten Jetpack Compose'la yazılmış
ve Compose Multiplatform **aynı paket adlarını** kullanıyor — ekran kodundaki
279 `androidx.compose.*` import'unun neredeyse hiçbiri değişmiyor. Ekranlar
ortak bir arayüz modülüne taşınıyor; Android uygulaması inceliyor, yanına iOS
ve masaüstü kabukları geliyor.

## Bugünkü durumun ölçümü (tahmin değil)

| Katman | Satır | Durum |
|---|---|---|
| Ortak modül (veri, iş kuralları, senkronizasyon) | 13 730 | iOS'ta **zaten derleniyor**, testleri Kotlin/Native'de koşuyor |
| Arayüz (13 ekran, Compose) | 4 327 | Taşınacak — import'lar büyük ölçüde aynı kalıyor |
| ViewModel + tema + giriş noktası | 2 734 | Kısmen taşınacak |

Android'e gerçekten bağlı kod **5 dosyada**:

| Dosya | Bağımlılık | iOS karşılığı |
|---|---|---|
| `AppPreferences.kt` | SharedPreferences | `NSUserDefaults` (`expect`/`actual`) |
| `ArkaPlanSenkronizasyonu.kt` | WorkManager | Aşağıda — tek gerçek tasarım işi |
| `Theme.kt` | dinamik renk (Android 12+) | iOS'ta sabit palet |
| `MainActivity.kt` | Activity | iOS kabuğunda `UIViewController` |
| `GymApplication.kt` | Application | iOS kabuğunda `main` |

İşi kolaylaştıran üç gerçek: DI **zaten Koin** (multiplatform; Hilt olsaydı
haftalar eklerdi), Room 2.7 KMP destekli ve iOS'ta derleniyor, `R.string`
kullanımı yok (yazılar Compose içinde).

## Modül yapısı

```
shared/      veri + iş kuralları (bugünkü hâli, değişmiyor)
arayuz/      YENİ — ekranlar, tema, gezinme (android + ios + jvm)
app/         Android kabuğu (MainActivity + bildirim + WorkManager)
masaustu/    YENİ — Windows/macOS/Linux'ta pencere olarak açan kabuk
iosApp/      YENİ — Xcode projesi (SwiftUI tek dosya + framework)
```

### Masaüstü hedefi neden var — test stratejisinin kalbi

iOS simülatörü **yalnızca macOS'ta çalışır**; Windows'ta "iOS simülatörü"
yoktur. Bu projede geliştirme Linux'ta yapılıyor ve Gradle yerelde
koşamıyor (Google Maven ağ ilkesince kapalı) — yani ekranları görmenin bir
yolu gerekiyor.

Çözüm: aynı ortak arayüz, masaüstünde de çalışıyor. `sqlite-bundled` JVM'de
sorunsuz (veritabanı testleri iki aydır orada koşuyor), yani masaüstü uygulaması
**gerçek veritabanı ve gerçek senkronizasyonla** açılıyor — sahte değil.

- Kullanıcı kendi bilgisayarında `gradlew :masaustu:run` ile ekranları görüyor.
- CI, masaüstü sürümünü Linux'ta `xvfb` altında açıp ekran görüntüsü alabiliyor
  → ekranların gerçekten çizildiği bu oturumdan da doğrulanabiliyor.
- iOS derlemesi CI'daki macOS işinde doğrulanıyor (bugün de öyle).

Masaüstü, iOS'un birebir kanıtı değil — dokunma davranışı, güvenli alanlar ve
yaşam döngüsü farklı. Ama "ekran çiziliyor mu, akış çalışıyor mu" sorusunun
%90'ını ucuza yanıtlıyor; kalan %10 gerçek cihazda TestFlight ile.

## Fazlar (PR dilimleri)

| PR | İçerik | Kanıt |
|---|---|---|
| i1 | `arayuz` + `masaustu` iskeleti: tema, Koin, giriş ekranının taşınmış hâli. `app/` HENÜZ dokunulmuyor. | CI: üç hedef derleniyor; masaüstü xvfb ekran görüntüsü |
| i2 | Platform dikişleri: `AppPreferences` → `expect/actual`, `ArkaPlanSenkronizasyonu` arayüzü, `Theme.kt` ayrımı | JVM testleri |
| i3a | `app` ortak giriş ekranına bağlanıyor; i1'deki kopya siliniyor | Android derlemesi — zincirin gerçekten çalıştığının kanıtı |
| i3-tarih | Ortak tarih biçimlendirme (`TarihBicimi`) + ekranlardaki `SimpleDateFormat`/`java.time` çağrılarının değiştirilmesi | `commonTest` — JVM **ve** Kotlin/Native'de aynı çıktı |
| i3b | Küçük ekranlar (paketler, sipariş geçmişi, ayarlar) ~700 satır | Çizim testleri |
| i3c | Orta ekranlar (pano, üye listesi, takvim, kayıt) ~1400 satır | Çizim testleri |
| i3d | Büyük ekranlar (personel, finans, market, üye detayı) ~2150 satır | Çizim testleri |
| i3e | ViewModel'ler + gezinme ortak modüle | CI |
| i4 | `iosApp` Xcode projesi; CI'da simülatör derlemesi | macOS CI işi |
| i5 | İmzasız `.ipa` + TestFlight yükleme iş akışı | Elle tetiklenen CI işi |
| — | **KULLANICI:** Apple Developer üyeliği (99 $/yıl), imza sertifikaları, TestFlight | Telefonda uygulama |

## Mantık kontrolü — planın zayıf noktaları ve kararları

**1. Sürüm eşleşmesi kör nokta.** Compose Multiplatform'un Kotlin 2.2.21 ile
eşleşen sürümü yerelde doğrulanamıyor (Gradle koşmuyor). Karar: 1.9.3 ile
başla, ilk PR bilinçli olarak KÜÇÜK — sürüm tutmazsa tek dosyalık düzeltme.

**2. `material-icons-extended` CMP'de ayrı yaşıyor.** Ekranlar 52 yerde
Material ikonları kullanıyor; CMP'de bu artık `compose.materialIconsExtended`
değil, ayrı sürümlenen bir artefakt. i1'de tek ikonla sınanacak; tutmazsa
ikonlar geçici olarak metinle değiştirilip i3'te toplu çözülecek. Bu, "her
ekran taşınırken ayrı ayrı patlamasın" diye i1'e alındı.

**3. WorkManager'ın iOS karşılığı birebir yok.** iOS'ta arka plan çalışması
(BGTaskScheduler) sistemin insafına kalmış — Android'deki gibi "15 dakikada
bir garanti" verilemez. Karar: senkronizasyon zaten öndeyken dakikada bir
koşuyor (bkz. `docs/kararlar.md`); iOS'ta yalnızca bu korunacak, arka plan
senkronizasyonu **vaat edilmeyecek**. Uygulama açıkken eşitler, kapalıyken
eşitlemez — bunu belgeye açıkça yazmak, çalışmayan bir vaadin sessizce
beklenmesinden iyi.

**4. `collectAsStateWithLifecycle` Android'e özgüydü.** JetBrains'in
multiplatform `lifecycle` portu var; i2'de ona geçilecek. Tutmazsa düz
`collectAsState` yeterli — uygulama tek pencereli, yaşam döngüsü kaybı
masaüstü/iOS'ta pratikte fark yaratmıyor.

**5. Room'un iOS'u gerçek cihazda hiç görülmedi.** Derleniyor ve Native
testleri geçiyor, ama gerçek iPhone'da dosya izinleri/yolu sürpriz çıkarabilir.
Bu yüzden i4'te ilk iOS ekranı "veritabanını aç, sürümü göster" kadar basit —
sorun varsa ilk gün görünsün.

**6. İmza olmadan telefona kurulamaz.** CI imzasız `.ipa` üretebilir ama
iPhone 14 Pro'ya kurmak için Apple Developer hesabı + TestFlight şart. Bu
kullanıcı adımı en geç i3 biterken başlamalı, yoksa i5 bitince beklemeye düşer.

**7. i3 tek parça olamaz — bölündü (i2 sonrası eklendi).** İlk planda 13 ekran
tek dilimdi. Yerel Gradle koşmadığı için her hata ancak CI'da görülüyor ve
4 327 satırlık bir dilimde hatalar birikip birbirini gizler; ayrıca düşen bir
derleme, hangi ekranın soruna yol açtığını söylemez. Dilimler ekran boyutuna
göre büyüyen sırada (i3a → i3e): en riskli dördü (personel, finans, market,
üye detayı) en sona bırakıldı, çünkü o noktada desen dört dilim boyunca
sınanmış olacak.

Bölmeyi mümkün kılan şey i1'deki **durum dışarıdan** (state hoisting) kararı:
ekranlar ViewModel tanımadığı için ViewModel'lerin taşınmasını (i3e)
beklemeden tek tek taşınabiliyorlar.

**8. Tarih biçimlendirme planda hiç yoktu (i3a sonrası bulundu).** Ekranlar
tarandığında **yedi ekran ve üç ViewModel'in** `SimpleDateFormat` ya da
`java.time` kullandığı görüldü; ikisi de JVM'e özgü, Kotlin/Native'de yok.
Plan bunu hesaba katmamıştı — ekranlar olduğu gibi taşınsaydı her biri iOS
derlemesinde ayrı ayrı patlardı.

Karar: biçimlendirme ekranlardan ÖNCE ortaklaştırılıyor (`TarihBicimi`,
`shared/commonMain`) ve önce **yerinde** uygulanıyor — ekranlar `app`'te
kalırken. Böylece değişiklik mevcut Android derlemesiyle doğrulanabiliyor ve
taşıma işi saf bir dosya taşımasına indirgeniyor. Beş dilime dağıtılsaydı aynı
iş beş kez yapılır ve beş kez sapma şansı doğardı.

Yan kazanç: eski kodun iki yerinde `Locale.getDefault()` vardı, yani telefonu
İngilizce olan personel "19 August 2026", Türkçe olan "19 Ağustos 2026"
görüyordu. Ortak biçimlendirici ay adlarını açıkça taşıdığı için bu tutarsızlık
da kapandı.

## Süre

Kabaca 2–3 hafta; her PR CI ile doğrulanarak. Payın %30'u 5. maddedeki
bilinmeyenlere ayrıldı.
