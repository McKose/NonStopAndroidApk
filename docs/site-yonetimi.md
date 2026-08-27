# Siteyi yönetmek — ne, nasıl, ne zaman

`nonstopstudio.tr` üç yüzeyden oluşuyor:

```
nonstopstudio.tr/          açılış sayfası — etkinlikler, branşlar, iletişim
nonstopstudio.tr/panel/    personel paneli — üyeler, paketler, finans, market
nonstopstudio.tr/uye/      üye alanı — paket durumu, ölçümler, sağlık beyanı
```

Yanlış bir adres yazıldığında (`nonstopstudio.tr/eski/kampanya`) GitHub'ın gri
hata sayfası değil, sitenin kendi **404 sayfası** açılıyor: ziyaretçiyi ana
sayfaya, branşlara ve iletişime yönlendiriyor.

Panel ayrıca **`admin.nonstopstudio.tr`** adresinden açılacak. Kurulum Turhost
panelinde birkaç adım gerektiriyor ve tarifi ayrı bir belgede:
[`docs/admin-alan-adi.md`](admin-alan-adi.md). Açılış sayfasından panele
bağlantı **verilmiyor** — ziyaretçinin işine yaramıyor, personel kendi
adresinden giriyor.

Bu belge **neyi kimin, nasıl değiştirebileceğini** anlatıyor. Sonunda da neyin
henüz hazır olmadığı ve hangi sırayla geleceği var.

---

## Hızlı tablo

| Ne değiştirmek istiyorsun | Kim yapar | Nasıl | Durum |
|---|---|---|---|
| Açılış fotoğrafı, reformer fotoğrafı, logo | **Sen** | GitHub'dan dosya yükle | ✅ hazır |
| Sayfadaki metinler, başlıklar, iletişim | Claude | Söylemen yeterli | ✅ hazır |
| Etkinlik / duyuru ekleme, kaldırma | **Sen** | Panel → Duyurular | ✅ hazır |
| Etkinlik görseli | **Sen** | Panelden yükle (5 MB'a kadar) | ✅ hazır |
| Üye kaydını onaylama, hesaba bağlama | **Sen** | Panel → Üye Hesapları | ✅ hazır |
| Üyenin kendi paket/ölçüm görmesi | Üye | `/uye/` | ✅ hazır |
| Üyenin hesap açması | Üye | `/uye/` → Kayıt olun | ✅ hazır\* |

\* Sunucuda `0006` migrasyonu ve `SUPABASE_TENANT_ID` gizli anahtarı gerekiyor —
"Sende bekleyenler" bölümüne bakın.

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

> **İletişim bilgisi iki yerde birden duruyor.** Telefon, adres ve çalışma
> saatleri hem gözle okunan iletişim bölümünde hem de arama motorunun okuduğu
> yapısal veri bloğunda yazılı. Biri değişirse ikisi birden değişmeli —
> ayrıştıklarında hiçbir belirti vermezler: sayfa doğru görünür, arama sonucu
> sessizce eski bilgiyi yayınlar. Bunu bir test koruyor
> (`web/iletisim.test.js`), yani ayrışma CI'da yakalanıyor. Değişiklik
> gerektiğinde bana söylemen yeterli.

---

## 3. Etkinlik ve duyurular — **panelden, koda dokunmadan**

Etkinlikler sabit dosya **değil** — veritabanından geliyor. Yazılımcıya ihtiyacın
yok: panelde **Duyurular** sekmesine gir, formu doldur, kaydet.

| Alan | Ne işe yarıyor |
|---|---|
| Başlık | Kartın başlığı — boş bırakılamıyor |
| Metin | Açıklama |
| Tür | Etkinlik / Kampanya / Duyuru |
| Başlangıç–bitiş tarihi | Bitince siteden **kendiliğinden** kalkıyor |
| Görsel | Ya adres yapıştır ya da dosya seçip **"Görseli yükle"** |
| Hemen yayınla | İşaretlemeden site göstermiyor |

Kaydettikten sonra listede görünüyor; oradaki **Yayınla / Yayından kaldır**
düğmesiyle istediğin an açıp kapatabiliyorsun.

> **Otomatik kalkma neden önemli:** bitmiş bir etkinliğin sitede durması, siteye
> bakılmadığı izlenimi veriyor. Bitiş tarihi girersen o tarihte kendiliğinden
> kayboluyor; kaldırmayı hatırlaman gerekmiyor.

### Görsel yükleme

Karar verildi: **Supabase Storage**. Panelde "Görseli yükle" düğmesi var;
bilgisayardan ya da telefondan dosya seçiyorsun, yüklendikten sonra adres
"Görsel adresi" alanına kendiliğinden yazılıyor ve küçük bir önizleme çıkıyor.

Sınırlar:

- **En fazla 5 MB.** Büyüğünü seçersen yüklemeden önce söylüyor.
- **Yalnızca görsel** (jpg, jpeg, png, webp, avif, gif).
- Yükleme **yönetici ve müdür** rollerinde; eğitmen yükleyemiyor.

Adres alanı hâlâ duruyor ve elle de yapıştırabilirsin — görsel başka bir yerde
duruyorsa (tasarımcının verdiği bağlantı gibi) o yol da açık. Görselsiz de
olur: kart görselsiz düzgün görünüyor, kırık resim simgesi çıkmıyor.

> ⚠️ Yüklediğin görseller **herkese açık** bir alanda duruyor — açılış sayfası
> onları giriş yapılmadan gösterdiği için başka türlüsü mümkün değil. Oraya
> yalnızca duyuru görseli koy; üye fotoğrafı, sağlık belgesi gibi şeyleri asla.

---

## 3b. Üye kayıtları — bekleyen istekleri onaylamak

Üye `nonstopstudio.tr/uye/` adresinden **kendisi kayıt oluyor**: e-posta, şifre,
ad ve telefon bırakıyor. Kayıt olması tek başına ona **hiçbir şey açmıyor** —
hesabını üyelik kaydına bağlayana kadar kendi verisi dahil hiçbir şey göremiyor.

Panelde **Üye Hesapları** sekmesinde "Bekleyen kayıt istekleri" listesi var:

1. Kişinin adı, telefonu, e-postası ve notu görünüyor.
2. Yanındaki listede **telefonu tutan üye önceden seçili** geliyor — telefon
   salonda tekil olduğu için en güvenilir ipucu bu. Doğruluğunu sen onaylıyorsun.
3. **Bağla** dersen hesap o üyeliğe bağlanıyor ve kişi anında kendi paketini,
   ölçümlerini görmeye başlıyor.
4. **Reddet** dersen istek listeden düşüyor.

> **Neden otomatik değil:** e-postaya bakıp otomatik bağlamak denendi ve
> reddedildi. Üye kaydındaki e-posta hem boş olabiliyor hem de tekil değil — tek
> bir yazım hatası, birinin sağlık verisini başka birine açardı. Bu bir erişim
> kararı ve kararı insan veriyor.

**Bunun çalışması için bir ayar gerekiyor** (bir kerelik): deponun
Settings → Secrets and variables → Actions bölümüne `SUPABASE_TENANT_ID`
adıyla salon kimliğini eklemen lazım. Değeri panelde **Üye Hesapları →
"Site kayıt ayarı (salon kimliği)"** başlığının altında yazıyor; oradan
kopyalayabilirsin. Eklemezsen site açılır, giriş çalışır, yalnızca **kayıt
formu** kapalı kalır ve ekranda bunu söyler.

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

**Faz 4 — üye alanı** (`/uye/`). Üye giriş yapıp kendi paket durumunu (kalan
seans, üyelik bitişi, ödenen tutar), eğitmeninin kaydettiği ölçümleri görüyor ve
sağlık durumundaki değişiklikleri bildiriyor. Sağlık beyanı **üzerine
yazmıyor**: her bildirim ayrı bir kayıt ve geçmiş duruyor.

**Faz 5 — panelde iki yeni bölüm.** *Duyurular* ve *Üye hesapları*.

**Faz 6 — üye kayıt akışı ve görsel yükleme.** Üye kendisi kayıt oluyor, sen
panelden onaylıyorsun; duyuru görselleri panelden yükleniyor. (Yukarıdaki
3. ve 3b. bölümler.)

### ⏳ Sırada

Sitenin planlanan işleri bitti. Bundan sonrası isteğe bağlı ekler ve aşağıdaki
"sende bekleyenler" listesi.

### 📌 Sende bekleyenler

| Konu | Ne gerekiyor |
|---|---|
| **`SUPABASE_TENANT_ID` gizli anahtarı** | Üye kayıt formunun açılması için. Değeri panelde Üye Hesapları sekmesinin altında yazıyor; Settings → Secrets → Actions'a ekle. |
| **Supabase'de `0006` migrasyonu** | Kayıt isteği tablosu ve görsel kovası bu dosyada. Supabase panelinde SQL Editor'e `supabase/migrations/0006_member_signup_and_storage.sql` içeriğini yapıştırıp çalıştır. Yapılmazsa kayıt ve görsel yükleme çalışmaz (ekran sebebini söyler). |
| **`admin.nonstopstudio.tr`** | Turhost'ta alt alan adı + SSL + FTP hesabı, sonra üç GitHub secret'ı. Yayın akışı hazır ve ayarlar gelene kadar sessizce atlıyor. Adımlar: [`docs/admin-alan-adi.md`](admin-alan-adi.md). |
| **E-posta riski** | Aşağıdaki ayrı bölüme bakın — sitede yapılacak bir şey yok, iş Turhost panelinde. |
| **Yayın anahtarı** | Android uygulamasının mağaza sürümü için. Adımlar `docs/yayin.md` içinde. Anahtarı **yedekle** — kaybı geri dönüşsüz. |
| **Yatay salon fotoğrafı** | Açılış ekranı için (isteğe bağlı). |

---

## E-posta: ne bozuk, ne bozuk değil

**Kod tarafında yapılacak bir şey yok — kontrol edildi.** Site hiçbir yerde
`@nonstopstudio.tr` adresi yayınlamıyor, `mailto:` bağlantısı yok; iletişim
Instagram ve harita üzerinden. Üye kaydı da etkilenmiyor: doğrulama e-postasını
**Supabase kendi sunucusundan** üyenin adresine gönderiyor, senin alan adından
geçmiyor.

**Bozuk olan tek şey:** `@nonstopstudio.tr` adresine **gelen** postalar.

Bugün ölçülen kayıtlar:

```
nonstopstudio.tr        MX     0 nonstopstudio.tr
nonstopstudio.tr        A      185.199.108–111.153   ← GitHub Pages
mail.nonstopstudio.tr   CNAME  nonstopstudio.tr      ← o da aynı yere gidiyor
```

`MX`, alan adının kendisini gösteriyor; alan adı da artık GitHub Pages'e
bakıyor. GitHub Pages **posta sunucusu değil**, yani sana yazan birinin maili
teslim edilemeyip geri döner. `mail.nonstopstudio.tr` de aynı yere baktığı için
`MX`'i ona çevirmek **çözüm değil** — sık yapılan hata bu.

### Sende yapılacak (Turhost paneli)

1. Turhost'ta bu alan adı için **e-posta hizmetin var mı** öğren.
2. **Varsa:** posta sunucusunun gerçek adresini iste (genelde `mail.turhost.com`
   gibi bir şey) ve DNS'te `MX` kaydını ona çevir. Bir de `TXT` (SPF) kaydı
   iste — şu an hiç yok, o yüzden gönderdiğin mailler de spam'a düşebilir.
3. **Yoksa ve bu adresle mail kullanmıyorsan:** `MX` kaydını **sil**. Şu anki
   hâli, olmayan bir posta sunucusunu işaret ettiği için gönderene yanıltıcı
   bir hata döndürüyor; kayıt hiç olmazsa hata daha dürüst olur.

Bunu ben yapamıyorum: DNS Turhost'ta ve oraya erişimim yok.

---

## "There isn't a GitHub Pages site here" (404)

Alan adına girdiğinde GitHub'ın gri 404 sayfası çıkıyorsa **sitenin dosyalarında
sorun yok** — o sayfa GitHub'ın kendi hata sayfası ve tek bir şey demek: hiçbir
depo şu an `nonstopstudio.tr` adresini üstlenmiyor.

En sık sebebi, Pages ayarlarındaki **özel alan adı kaydının düşmesi**. Yayın
yeşil bitmeye devam ettiği için sessiz bir arıza: Actions sekmesinde her şey
başarılı görünür.

**Düzeltme (1 dakika):**

1. `https://github.com/McKose/NonStopAndroidApk/settings/pages`
2. **Custom domain** kutusuna `nonstopstudio.tr` yaz → **Save**.
3. GitHub bir DNS kontrolü yapar (birkaç dakika). Yeşil tik çıkınca
   **Enforce HTTPS** kutusunu işaretle.

> **Neden depodaki `CNAME` dosyası bunu tek başına çözmüyor:** site GitHub
> Actions ile yayınlanıyor ve o yolda alan adının yetkili kaydı **Settings →
> Pages**. Yapıtın içindeki `CNAME` dosyası ayarı kendi başına kurmuyor. Dosya
> yine de duruyor ama ona güvenmek yetmiyor — bu yüzden yayın iş akışına, adres
> beklenenden farklıysa günlüğe uyarı basan bir adım eklendi.

**Bu arada site erişilebilir:** `https://mckose.github.io/NonStopAndroidApk/`
adresi her zaman çalışır. Panel ve üye alanı da oradan açılır
(`.../NonStopAndroidApk/panel/` ve `.../NonStopAndroidApk/uye/`). Alan adı
düzelene kadar denemelerini orada yapabilirsin.

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
