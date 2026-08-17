# Siteyi yönetmek — ne, nasıl, ne zaman

`nonstopstudio.tr` üç yüzeyden oluşuyor:

```
nonstopstudio.tr/          açılış sayfası — etkinlikler, branşlar, iletişim
nonstopstudio.tr/panel/    personel paneli — üyeler, paketler, finans, market
nonstopstudio.tr/uye/      üye alanı — paket durumu, ölçümler, sağlık beyanı
```

Bu belge **neyi kimin, nasıl değiştirebileceğini** anlatıyor. Sonunda da neyin
henüz hazır olmadığı ve hangi sırayla geleceği var.

---

## Hızlı tablo

| Ne değiştirmek istiyorsun | Kim yapar | Nasıl | Durum |
|---|---|---|---|
| Açılış fotoğrafı, reformer fotoğrafı, logo | **Sen** | GitHub'dan dosya yükle | ✅ hazır |
| Sayfadaki metinler, başlıklar, iletişim | Claude | Söylemen yeterli | ✅ hazır |
| Etkinlik / duyuru ekleme, kaldırma | **Sen** | Panelden | ⏳ Faz 5 |
| Etkinlik görseli | **Sen** | Faz 5'teki karara bağlı | ⏳ Faz 5 |
| Üye hesabı açma, üyeye bağlama | **Sen** | Panelden | ⏳ Faz 5 |
| Üyenin kendi paket/ölçüm görmesi | Üye | `/uye/` | ⏳ Faz 4 |

---

## 1. Sabit görselleri değiştirmek (şimdi yapabilirsin)

Bu dosyalar depoda duruyor ve doğrudan sayfada görünüyor:

| Nerede görünüyor | Dosya |
|---|---|
| Açılış ekranı arka planı | `web/varliklar/salon-ic.jpg` |
| Reformer bölümü fotoğrafı | `web/varliklar/reformer.jpg` |
| Üst çubuktaki logo (koyu zemin) | `web/varliklar/nonstop-gym-beyaz.svg` |
| Logonun koyu hâli (açık zemin için) | `web/varliklar/nonstop-gym.svg` |

### Adımlar — tarayıcıdan, kod bilgisi gerekmez

1. Şu adresi aç:
   <https://github.com/McKose/NonStopAndroidApk/tree/master/web/varliklar>
2. Değiştireceğin dosyaya tıkla (ör. `salon-ic.jpg`)
3. Sağ üstteki **⋯** menüsünden **Delete file** → sayfanın altındaki
   **Commit changes**
4. Klasöre dön, **Add file → Upload files** ile yeni fotoğrafı sürükle
5. **Commit changes**

Bittiğinde yayın kendiliğinden başlıyor (`web/` altındaki her değişiklik
tetikliyor) ve 1–2 dakikada site güncelleniyor. İlerleyişi
[Actions sekmesinden](https://github.com/McKose/NonStopAndroidApk/actions)
izleyebilirsin.

### İki kural

**Dosya adı birebir aynı olmalı.** Sayfa fotoğrafı adıyla arıyor;
`salon-ic-yeni.jpg` yüklersen sayfa eskisini aramaya devam eder ve o alan boş
görünür. Adı değiştirmek istersen `web/index.html` içindeki referansın da
değişmesi gerekiyor — o zaman Claude'a söyle.

**Dosya boyutu 1–2 MB'ı geçmesin.** Telefondan çekilen ham fotoğraf 5–8 MB
oluyor ve sayfa mobil bağlantıda çok yavaş açılıyor. Şu anki fotoğraflar
~300 KB. Küçültmeyi Claude yapabilir, ya da herhangi bir görsel sıkıştırma
sitesi iş görür.

> **Neden fotoğraf yatay olmalı:** açılış ekranı geniş bir alan; dikey fotoğraf
> ortadan kırpılıyor ve genelde en önemli kısım kesiliyor. Şu anki salon içi
> fotoğrafı dikey ve masaüstünde tavandaki tabela kırpılıyor — yatay bir
> fotoğraf daha iyi durur.

---

## 2. Metinleri değiştirmek

Başlıklar, tanıtım yazısı, branş açıklamaları ve iletişim bilgisi
`web/index.html` içinde sabit duruyor.

Teknik olarak bunları da GitHub'dan düzenleyebilirsin, ama metinler HTML
etiketlerinin arasında ve yanlışlıkla bir etiketi bozmak kolay — bozulduğunda
sayfanın o bölümü tamamen kaybolabiliyor. **Metin değişikliklerini Claude'a
söylemen daha güvenli.**

Eksik olan ve eklenebilecekler: telefon numarası, çalışma saatleri, açık adres
(şu an yalnızca "Kartepe / Kocaeli" ve harita bağlantısı var).

---

## 3. Etkinlik ve duyurular (Faz 5'te açılacak)

Etkinlikler sabit dosya **değil** — veritabanından geliyor. Sayfa öyle kuruldu ki
her etkinlik için yazılımcıya ihtiyacın olmasın.

**Sunucu tarafı hazır** (migrasyon `0005`): `announcements` tablosu, yayın
penceresi ve erişim kuralları kuruldu ve sınandı. Yalnızca **yayınlanmış** ve
tarihi geçmemiş duyurular siteye çıkıyor; taslak olanlar giriş yapmadan
okunamıyor bile.

**Eksik olan:** panelde etkinlik girdiğin ekran. Faz 5'te gelecek. O ekran
açıldığında şunları gireceksin:

| Alan | Ne işe yarıyor |
|---|---|
| Başlık | Kartın başlığı |
| Metin | Açıklama |
| Tür | Etkinlik / Kampanya / Duyuru |
| Başlangıç–bitiş tarihi | Bitince siteden **kendiliğinden** kalkıyor |
| Yayınla | İşaretlemeden site göstermiyor |
| Görsel adresi | Aşağıya bakın |

> **Otomatik kalkma neden önemli:** bitmiş bir etkinliğin sitede durması, siteye
> bakılmadığı izlenimi veriyor. Bitiş tarihi girersen o tarihte kendiliğinden
> kayboluyor; kaldırmayı hatırlaman gerekmiyor.

### Etkinlik görseli — karar verilmesi gereken konu

Tabloda görsel bir **adres** olarak tutuluyor, yani görselin internette bir
yerde durması gerekiyor. Üç yol var:

1. **Supabase Storage (önerilen).** Panele "Görsel yükle" düğmesi konur;
   telefondan ya da bilgisayardan seçersin, gerisi otomatik. Gerçek çözüm bu
   ve seni tamamen bağımsız yapıyor. Faz 5'e ek iş getiriyor.
2. **Dış bağlantı yapıştırma.** Görseli başka bir yere yükleyip adresini
   yapıştırırsın. Hızlı ama kırılgan: o site görseli silerse ya da dışarıdan
   bağlanmayı engellerse etkinlik görselsiz kalır.
3. **Görselsiz etkinlikler.** Başlık, metin ve tarih yeter. **Bu bugün zaten
   çalışıyor** — görsel yoksa kart düzgün görünüyor, kırık resim simgesi
   çıkmıyor.

Karar verilmezse 3 geçerli olur ve 2 de kendiliğinden çalışır; 1 ayrıca
yapılması gereken bir iş.

---

## 4. Neyi Claude yapmalı

- Metin, başlık, bölüm ekleme/çıkarma
- Renk, yerleşim, yazı tipi değişiklikleri
- Yeni sayfa (ör. fiyat listesi, sıkça sorulanlar)
- Fotoğraf adını değiştirmek ya da yeni fotoğraf alanı açmak
- Alan adı, sertifika, yayın akışı sorunları

---

## Yol haritası

### ✅ Bitti

**Faz 1 — sunucu temeli.** `announcements`, `member_accounts` ve
`member_health_updates` tabloları; üyenin yalnızca kendi kaydını görmesini
sağlayan erişim kuralları. Gerçek PostgreSQL üzerinde sınandı: üye başka üyeyi,
kasayı, personel maaşlarını göremiyor; anonim ziyaretçi yalnızca yayınlanmış
duyuruları okuyabiliyor.

**Faz 2 — site iskeleti.** Panel `/panel/` altına taşındı, üç yüzeyli yapı
kuruldu, `nonstopstudio.tr` bağlandı.

**Faz 3 — açılış sayfası.** Marka, branşlar, iletişim, etkinlik vitrini.
Fotoğraflar ve logo yerleşti.

### ⏳ Sırada

**Faz 4 — üye alanı** (`/uye/`). Üye giriş yapıp kendi paket durumunu (kalan
seans, üyelik bitişi, ödeme durumu), eğitmeninin kaydettiği ölçümleri görüyor ve
sağlık durumundaki değişiklikleri bildiriyor. Şu an yer tutucu sayfa var.

**Faz 5 — panelde iki yeni bölüm.**
- *Duyurular*: etkinlik girip yayınlama (yukarıdaki bölüm).
- *Üye hesapları*: bir üyeyi bir hesaba bağlama. Üye alanının çalışması buna
  bağlı — bağ kurulmadan üye hiçbir veri göremiyor, ve bu bilinçli.

### 📌 Sende bekleyenler

| Konu | Ne gerekiyor |
|---|---|
| **E-posta riski** | `MX` kaydı hâlâ alan adının kendisini gösteriyor, o da artık GitHub'a bakıyor. Bu alan adıyla e-posta kullanıyorsan mailler geri döner. Turhost'tan gerçek posta sunucusu adresini alıp `MX`'i ona çevirmen gerekiyor. |
| **Yayın anahtarı** | Android uygulamasının mağaza sürümü için. Adımlar `docs/yayin.md` içinde. Anahtarı **yedekle** — kaybı geri dönüşsüz. |
| **Görsel yükleme kararı** | Yukarıdaki üç seçenekten biri. |
| **Telefon, çalışma saatleri, açık adres** | Siteye eklenecek. |
| **Yatay salon fotoğrafı** | Açılış ekranı için (isteğe bağlı). |

---

## Bir şey bozulursa

Site açılmıyorsa ya da yanlış görünüyorsa ilk bakılacak yer
[Actions sekmesi](https://github.com/McKose/NonStopAndroidApk/actions):
kırmızı bir koşu varsa neyin düştüğünü yazıyor.

Yüklediğin bir fotoğraftan sonra bozulduysa en olası sebep **dosya adının
değişmiş olması**. Eski adla tekrar yüklemek düzeltir.

Yayın akışı, yayınlanacak dosyaları sayfaların birbirine verdiği referanslardan
**kendisi çıkarıyor**; elle tutulan bir liste yok. Bir dosyayı listeye eklemeyi
unutmak diye bir durum kalmadı — bu, bir kez yaşandığı için böyle kuruldu.
