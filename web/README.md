# Web yönetim paneli

Salonun verisini tarayıcıdan görüntüleyen panel. Uygulamanın gönderdiği veriyi
okuyor; aynı Supabase projesine bağlanıyor.

## Ne yapar, ne yapmaz

**Yapar:** üyeleri, paketleri, randevuları, market ürünlerini (eldeki stokla),
satışları, personeli ve finans defterini listeler. Giriş, uygulamadaki
hesapların aynısıyla (e-posta + şifre).

**Yapmaz: yazma.** Bu bilinçli bir sınır. Uygulamadaki her yazma yolu, satırı
değiştiren işlemle aynı transaction içinde gönderim kuyruğuna kayıt bırakıyor ve
iş kuralları (hakediş hesabı, seans düşme, defter kaydı) ortak Kotlin modülünde.
Panelden yazmak, o kuralların ikinci bir kopyasını JavaScript'te tutmak demek
olurdu — ve iki kopya er geç birbirinden sapardı. Sapma da sessiz olurdu: panelden
girilen bir ödeme uygulamada farklı bir bakiye üretirdi.

**Kimin neyi göreceğine panel karar vermiyor.** Sorgularda salon süzgeci yok;
sunucudaki satır bazlı güvenlik kuralları zaten yalnızca kullanıcının salonunun
satırlarını döndürüyor. Panelin kodu yanlış yazılsa bile başka bir salonun verisi
gelmez.

## Kurulum

Derleme adımı, paket yöneticisi ve bağımlılık yok — dosyalar olduğu gibi
çalışıyor.

1. `config.example.js` dosyasını `config.js` adıyla kopyalayın.
2. İçindeki iki değeri doldurun (Supabase panelinde Project Settings → API):
   ```js
   window.NONSTOP_CONFIG = {
     url: "https://<proje-ref>.supabase.co",
     anonKey: "<anon public anahtarı>",
   };
   ```
3. Klasörü bir statik sunucuya koyun.

`config.js` depoya işlenmiyor: değerler kuruluma özgü.

`anonKey` gizli değil — tarayıcıda görünmesi normaldir ve tek başına hiçbir
veriye erişemez. **`service_role` anahtarı buraya asla konmaz:** o anahtar bütün
erişim kurallarını baypas eder ve panelde görünür olması tüm verinin herkese açık
olması demektir.

## Kurulumsuz denemek

Ekranlara bakmak için Supabase ayarı, hesap ve internet gerekmiyor: adrese
`?demo` ekleyin.

```bash
cd web
python3 -m http.server 8000
```

Sonra `http://localhost:8000/?demo` — giriş ekranında herhangi bir e-posta ve
şifre kabul edilir.

Demo verisi gerçek sunucu biçiminin aynısı ve sapmaması testle bağlı: sapsaydı
demoda düzgün görünen bir ekran gerçek veride bozuk çıkabilirdi.

## Yerelde denemek (gerçek veriyle)

`file://` üzerinden açmayın — tarayıcı ES modüllerini o protokolde yüklemiyor.
Herhangi bir statik sunucu yeter:

```bash
cd web
python3 -m http.server 8000
```

Sonra `http://localhost:8000` adresini açın.

## Testler

İş kuralları (tutar biçimi, tarih, üyelik durumu, tombstone ayıklama) Node'un
yerleşik test koşucusuyla sınanıyor:

```bash
cd web
node --test *.test.js
```

Bu kurallar uygulamadaki Kotlin karşılıklarının kopyası ve bu bir risk — iki
taraf sapabilir. Sapmayı sınırlamak için panele yalnızca **gösterime dair**
kurallar giriyor; hesaplama yapan hiçbir şey yok.

## Sekmeler ve rol görünürlüğü

Sekmeler: **Özet, Üyeler, Paketler, Randevular, Market, Satışlar, Personel,
Finans.** Senkronize edilen dokuz tablodan sekizi panelde; dışarıda kalan tek
tablo `measurements` (aşağıda "Kapsam dışı").

**Hangi rolün hangi sekmeyi göreceğine panel karar vermiyor.** Kural
uygulamadaki `AppDestination.visibleTo` ile aynı ve `roller.js`te duruyor:
Finans yalnızca salon sahibi ve müdürde, geri kalan her sekme üç rolde de açık.

Bu bir kopya ve kopya olduğu için **sınanıyor**: `roller.test.js` Kotlin
dosyasını (`RoleAccess.kt`) okuyup tabloyla karşılaştırıyor. Kotlin tarafındaki
kural değişirse panel testi düşüyor. Ayrıştırma bozulursa test de düşüyor —
"okuyamadım, geçtim" davranışı bilinçli olarak yok.

Panel daha önce **Finans'ı her role gösteriyordu**; uygulama ise onu eğitmene
göstermiyor. Aynı ürün iki farklı cevap veriyordu ve bu, projede tekrar tekrar
düzelttiğimiz hata sınıfının aynısı.

**Bu bir güvenlik sınırı değil.** Sunucudaki okuma kuralları (migrasyon `0004`)
salona bağlı her role bütün tabloları açıyor; rol yalnızca **yazmayı**
kısıtlıyor. Sekme gizlemek arayüz kararı: eğitmenin işine yaramayan ve yanlış
anlaşılması kolay bir ekranı yoluna koymamak. Gerçek sınır sunucuda.

## Market: stok hareketlerden türüyor

Ürün tablosunda stok kolonu **yok** ve bu bilinçli — mutlak bir sayaç olsaydı iki
cihaz aynı anda satış yaptığında bir satış sessizce kaybolurdu. Eldeki stok
`stock_movements` toplamı ve kural uygulamadaki `StockMovementDao.onHand` ile
birebir aynı: sebebe göre süzme yok, satış/alım/düzeltme/iade hepsi toplamaya
giriyor (`stok.js`).

**Liste kesilirse sayı gösterilmiyor.** Hareket sayısı okuma sınırına dayanırsa
toplam eksik hesaplanır ve sonuç tamamen makul bir sayı gibi görünür — 500
hareketin ilk 500'ünden çıkan bir stok, doğru stoktan ayırt edilemez. O durumda
sayı yerine `?` yazıyor ve sebebi ekranda söyleniyor.

Aynı sebeple okunamayan bir hareket, o ürünün stoğunu **bilinmez** yapıyor;
sıfır ya da eksik toplam göstermiyor.

**Negatif stok ayrı bir durum**, tükenmenin daha kötüsü değil: fazla satış ya da
eksik alım kaydı demek, yani veri sorunu. Aynı rozeti vermek sebebini
araştırılmaz kılardı. Uyarı sayaçları ve tablodaki rozetler **aynı**
sınıflandırmadan geliyor (`stokDurumu`); ayrı yazıldıklarında gerçekten
ayrıştılar ve kutuda "3 tükendi" yazarken tabloda 2 rozet görünüyordu.

## Arama ve tarih süzgeci

Üyeler, Randevular, Satışlar ve Finans sekmelerinde listenin üstünde bir süzgeç
çubuğu var: arama kutusu, (tablo tarih taşıyorsa) tarih aralığı ve sayaç.
Paketler ve Personel'de tarih aralığı yok — o tablolarda süzülecek anlamlı bir
tarih kolonu yok.

Sekmelerin veri tanımı (`sekmeler.js`) 20'den fazla kolon adı taşıyor ve üç
yazım hatası türü de sessiz: `order`da hata sunucudan 400 aldırır, `ara`da hata
aramayı o alanda sessizce çalıştırmaz, `tarihAlani`nda hata süzgeci hiçbir şeyi
süzmez hâle getirir. Bu yüzden `sekmeler.test.js` her adı **SQL
migrasyonlarındaki** gerçek şemayla karşılaştırıyor (`sema.js`) — demo verisiyle
değil, çünkü demo da bir kopya ve kopyayı kopyayla karşılaştırmak ikisinin
birlikte yanlış olmasını yakalamaz.

**Arama Türkçe'ye göre katlanıyor.** "ayse" yazan biri "Ayşe"yi bulur; "isil"
yazan "Işıl"ı bulur. JavaScript'in varsayılan büyük/küçük harf dönüşümü burada
yanlış sonuç veriyor (`"İ".toLowerCase()` ayrı bir birleşen nokta bırakır,
`"I".toLowerCase()` ise `ı` yerine `i` üretir), bu yüzden Türkçe'ye özgü harfler
önce ASCII karşılıklarına eşleniyor. `ı` ile `i` bilinçli olarak aynı sayılıyor:
kullanıcı çoğu zaman hangisini yazdığını bilmiyor.

Boşlukla ayrılan parçaların **hepsi** aranıyor ve farklı kolonlarda olabilirler:
"ayse 0532" hem ada hem telefona bakar.

**Tarih aralığı her iki uçta da dahil.** "1–15 Ağustos" dendiğinde 15 Ağustos
akşamı yazılmış kayıt da girer. Aralık **yerel** güne göre hesaplanıyor;
`new Date("2026-08-15")` kullanılmıyor çünkü o UTC gece yarısı demek ve
Türkiye'de günün ilk üç saati aralığın dışında kalırdı.

Süzme tarayıcıda yapılıyor, sunucuda değil — salonun ölçeğinde (birkaç yüz üye)
her tuşta ağ turu atmanın karşılığı yok. Kayıt sayısı on binlere çıkarsa
değişecek yer `suzme.js`, ekranlar değil.

### Kapsam dışı: ölçümler

`measurements` tablosu panelde **yok** ve bu bilinçli. Ölçüm verisi üyeye ait bir
zaman serisi (boy, kilo, çevreler) ve anlamı ancak tek bir üyenin geçmişi yan yana
görüldüğünde ortaya çıkıyor. Panelin bugünkü yapısı düz liste: bütün üyelerin
ölçümlerini tarih sırasına dizmek teknik olarak kolay ama kimsenin bakmayacağı bir
tablo üretirdi.

Doğru karşılığı üye detay görünümü ve panelde öyle bir görünüm yok. Eklendiğinde
ölçümler oraya girer.

### Testler

```bash
cd web && npm test        # TZ=Europe/Istanbul ile koşar
```

Testlerin bir kısmı panelin **kendi dışındaki** dosyaları okuyor ve bu bilinçli:

- `roller.test.js` → `shared/.../RoleAccess.kt` (rol kuralı aynı mı)
- `sekmeler.test.js` → `supabase/migrations/*.sql` (kolon adları gerçek mi)
- `onizleme.test.js` → panelin kendi modül listesi (önizleme eksik mi)

Üçü de "iki yerde duran aynı bilgi" problemini sınıyor. Kopyayı silmek mümkün
değil (biri Kotlin, biri SQL, biri JavaScript), ama kopyanın sapması sınanabilir.

Saat dilimi **bilinçli** olarak ayarlanıyor: koşucular UTC ve UTC'de yerel gün
ile UTC gün aynı çıkıyor, dolayısıyla tarih hesabını bozan bir hata testlerden
geçerdi — ama salonun makinesinde (UTC+3) yanlış sonuç verirdi.


## Yayınlama

Panel her `web/` değişikliğinden sonra GitHub Pages'e kendiliğinden yayınlanıyor
(`.github/workflows/panel-yayin.yml`). Adres:

**<https://mckose.github.io/NonStopAndroidApk/>**

### Tek seferlik kurulum

İki ayar gerekiyor ve ikisi de yalnızca bir kez yapılıyor.

**1. Pages'i açın.** İş akışı bunu kendisi yapamıyor: varsayılan `GITHUB_TOKEN`
Pages sitesi oluşturamıyor (ilk denemede `enablement: true` ile denendi,
"Resource not accessible by integration" ile düştü).

- <https://github.com/McKose/NonStopAndroidApk/settings/pages>
- **Build and deployment** → **Source** = **GitHub Actions**

Açılmamışsa iş akışı ilk adımda, ne yapılması gerektiğini söyleyerek duruyor.

**2. Sunucu ayarlarını ekleyin** (panelin gerçek veriye bağlanması için;
`?demo` bunlarsız da çalışıyor). Ayrıntılı anlatım `docs/deneme.md` içinde —
APK ile aynı iki gizli anahtar:

- <https://github.com/McKose/NonStopAndroidApk/settings/secrets/actions>
- `SUPABASE_URL` ve `SUPABASE_ANON_KEY`

Anahtarlar **derleme sırasında** okunuyor, yani ekledikten sonra yayının
yeniden çalışması gerekiyor: Actions → **Paneli yayınla** → **Run workflow**.

Derleme adımı yok; dosyalar olduğu gibi kopyalanıyor. İş akışının yaptığı tek
"derleme", depoya işlenmeyen `config.js` dosyasını depo gizli anahtarlarından
(`SUPABASE_URL`, `SUPABASE_ANON_KEY`) üretmek — APK tarafındaki desenin aynısı.
Anahtarlar tanımlı değilse panel yine yayınlanıyor, yalnızca "Kurulum
tamamlanmamış" diyor; `?demo` ile yine açılıyor.

Yayınlanan dosyalar tek tek sayılıyor, `web/` olduğu gibi kopyalanmıyor: testler,
örnek ayar dosyası ve önizleme üreticisi siteye ait değil.

Başka bir yere koymak isterseniz panel statik: `web` klasörünü herhangi bir HTTP
sunucusunun kök dizinine kopyalamak ve yanına `config.js` eklemek yeterli.

### Panelin herkese açık olması sorun mu

Hayır, ve bu tasarımın bir parçası:

- Adresi bilen herkes **giriş ekranını** görebilir. İçeri girebilmek için
  Supabase hesabı gerekiyor.
- `anonKey` sayfada görünür ve görünmesi normaldir — tek başına hiçbir veriye
  erişmiyor. Hangi satırın kime görüneceğini sunucudaki erişim kuralları
  belirliyor (bkz. `supabase/README.md`).
- Depo zaten herkese açık; panelin kaynak kodu da öyle.

`service_role` anahtarı hiçbir zaman buraya konmuyor: o anahtar bütün erişim
kurallarını baypas eder.

### Supabase tarafında bir ayar gerekiyor mu

Hayır. Panel `anon` anahtarıyla ve giriş yapan kullanıcının jetonuyla çalışıyor;
uygulamanın kullandığı yolun aynısı. Yalnızca şifre sıfırlama e-postaları için
adresi Supabase panelinde **Authentication → URL Configuration** altındaki
izinli adresler listesine eklemeniz gerekebilir.

### Kendi alan adına bağlamak (isteğe bağlı)

`panel.nonstopstudio.tr` gibi bir adres istenirse iki adım var:

1. **DNS kaydı** — alan adı sağlayıcınızda:

   | Tür | Ad | Değer |
   |---|---|---|
   | `CNAME` | `panel` | `mckose.github.io` |

   Apex (`nonstopstudio.tr`, alt alan adı olmadan) kullanmak isterseniz `CNAME`
   yerine dört `A` kaydı gerekiyor; GitHub'ın adresleri
   [belgelerinde](https://docs.github.com/pages/configuring-a-custom-domain-for-your-github-pages-site)
   yazılı. Alt alan adı hem daha basit hem de ana alan adını salonun tanıtım
   sitesine bırakıyor.

2. **Depoda `CNAME` dosyası** — `web/` altına tek satırlık bir `CNAME` dosyası
   (`panel.nonstopstudio.tr` yazan) koyup iş akışının kopyalama listesine
   eklemek yeterli.

**Sıra önemli:** önce DNS kaydını ekleyin, sonra `CNAME` dosyasını. Tersi
yapılırsa GitHub Pages özel alan adını beklemeye başlar ve `github.io` adresi
çalışmayı bırakır — yani DNS yayılana kadar panel hiçbir adresten açılmaz.
