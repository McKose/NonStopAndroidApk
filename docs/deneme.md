# Nasıl denerim

Üç yol var, hızlıdan yavaşa. Küçük bir değişikliğe bakmak için birincisi yeter.

Bu belgedeki bağlantılar bu depoya ve `jvkytncwedjcvssilhih` numaralı Supabase
projesine göre yazıldı; başka bir kuruluma bakıyorsanız proje numarasını
değiştirin.

---

## 1. Paneli kurulumsuz denemek (30 saniye)

Panelin ekranlarına bakmak için Supabase ayarı, hesap ve internet gerekmiyor.

**En kolayı:** yayındaki panele `?demo` ekleyerek girmek —

**<https://mckose.github.io/NonStopAndroidApk/?demo>**

Bilgisayarınızda hiçbir şey kurmanız gerekmiyor, telefondan da açılıyor.

Depodan çalıştırmak isterseniz iki yolu var. **Tek dosyalık kopya** üretip tarayıcıda açmak (paylaşmaya da
uygun, sunucu gerektirmiyor):

```bash
node web/onizleme.mjs > /tmp/panel.html
```

Sonra `/tmp/panel.html` dosyasını tarayıcıda açın.

**Ya da depodan çalıştırın:**

```bash
cd web
python3 -m http.server 8000
```

Sonra tarayıcıda: **`http://localhost:8000/?demo`**

Adresin sonundaki `?demo` şart. Onsuz panel gerçek sunucuya bağlanmaya çalışır
ve `web/config.js` olmadığı için "Kurulum tamamlanmamış" der.

Giriş ekranında **herhangi bir e-posta ve şifre** yazıp "Giriş yap" deyin —
demo modda kontrol yok. İçeride örnek bir salonun verisi var: aktif, süresi
dolmuş, dondurulmuş ve arşivlenmiş üyeler; dört paket; randevular; defter
kayıtları.

Denemeye değer birkaç şey:

- **Üyeler** sekmesi → arama kutusuna `ayse` yazın. `Ayşe Yılmaz` gelmeli:
  arama aksansız yazımı da tanıyor.
- Aynı kutuya `yilmaz` yazın → `Ayşe Yılmaz`. Noktasız `ı` ile `i` ayrımı
  aramada kaldırılıyor.
- `ozturk` yazın → `Burak Öztürk`; `sahin` yazın → `Ali Şahin`.
- **Finans** sekmesi → tarih aralığını tek güne daraltın. Sayaç `1 / 6 kayıt`
  gibi görünmeli.
- **Temizle** düğmesi süzgeçleri sıfırlıyor.

Demo verisi **gerçek sunucu biçiminin aynısı** (snake_case kolonlar, kuruş
tutarlar, epoch ms tarihler). Sapmaması testle bağlı: sapsaydı demoda düzgün
görünen bir ekran gerçek veride bozuk çıkabilirdi.

---

## Önce: sunucu hazır mı (2. ve 3. yol için)

Birinci yol (`?demo`) sunucuya hiç gitmiyor, bu bölümü atlayabilirsiniz.
Diğer ikisi gerçek sunucuya bağlanıyor.

### a) Şemayı güncelleyin

1. Şu adresi açın:
   **<https://supabase.com/dashboard/project/jvkytncwedjcvssilhih/sql/new>**
   (elle: supabase.com → projeniz → sol menü **SQL Editor** → **New query**)
2. `supabase/migrations/` altındaki dosyaların içeriğini **sırayla** yapıştırıp
   her birinde **Run** deyin (yeşil düğme, sağ altta; ya da `Ctrl+Enter`).

   Hepsi tekrar çalıştırılabilir — emin değilseniz yeniden koşturun, zarar
   vermez.

En son eklenen: **`0004_role_based_access.sql`** — kim neyi yazabilir kuralı.
Çalıştırılmazsa uygulama ve panel çalışmaya devam eder; yalnızca yetki ayrımı
olmaz, yani salona bağlı herkes her şeyi yazabilir.

### b) Kendi rolünüzü kontrol edin

Rol yalnızca bilgi değil, **ne yazabileceğinizi** belirliyor: `MANAGER` iseniz
personel ekleyemez, `TRAINER` iseniz fiyat da değiştiremezsiniz.

Aynı SQL Editor'de çalıştırın:

```sql
select u.email, gu.role
  from public.gym_users gu
  join auth.users u on u.id = gu.user_id;
```

Sayfanın altında bir tablo çıkar. Kendi e-postanızın karşısında **`ADMIN`**
yazmalı.

**`ADMIN` yazmıyorsa** (e-postanızı ilk sorgunun çıktısındaki yazımla birebir
kullanın):

```sql
update public.gym_users
   set role = 'ADMIN'
 where user_id = (select id from auth.users where email = '<e-postanız>');
```

`UPDATE 1` dönmeli. `UPDATE 0` dönerse e-posta tutmamıştır — en sık sebep bir
harf farkı.

**Hiç satır dönmüyorsa** hesabınız salona hiç bağlanmamıştır; bağlama adımı
`supabase/README.md` → "Kurulum" 5. maddede.

Güncellemede salon süzgeci yok: bugün tek salon olduğu için gereksiz. İleride
bir kullanıcı birden fazla salona bağlanırsa bu ifade **hepsindeki** rolünü
değiştirir; o zaman `and gym_id = '<salon kimliği>'` eklenmeli.

Ayrıntılı anlatım: `supabase/README.md` → "Kim neyi yazabilir".

---

## 2. Uygulamayı APK indirerek denemek (Android Studio gerekmez)

Her CI koşusu kurulabilir bir APK üretiyor.

1. Şu adresi açın: **<https://github.com/McKose/NonStopAndroidApk/actions>**
2. Listenin **en üstündeki yeşil tikli** koşuya tıklayın. (Kırmızı çarpı varsa
   o koşu başarısız; bir alttakini seçin.)
3. Açılan sayfayı **en alta** kaydırın. **Artifacts** başlığı orada.
4. **`nonstop-debug-apk`** yazısına tıklayın — zip olarak iner.
5. Zip'i açın; içinden `app-debug.apk` çıkar.
6. Dosyayı telefona kopyalayıp açın. Android "bilinmeyen kaynak" uyarısı
   verirse izin verin — imzasız bir geliştirme sürümü.

Yapıt **14 gün** duruyor; süresi dolmuşsa yeni bir koşu gerekiyor (depoya
herhangi bir değişiklik gönderildiğinde ya da Actions sayfasından iş elle
tetiklendiğinde üretilir).

> **İmza uyarısı.** Projede özel bir imzalama ayarı yok, yani hata ayıklama
> derlemeleri makinedeki `~/.android/debug.keystore` ile imzalanıyor. CI
> koşucuları her seferinde sıfırdan kurulduğu için **her CI koşusu farklı bir
> anahtarla imzalıyor**. Sonucu: bir APK'yı diğerinin üzerine kurmaya
> çalıştığınızda `INSTALL_FAILED_UPDATE_INCOMPATIBLE` alırsınız. Bozuk bir şey
> değil; tek çözümü önce kaldırıp sonra kurmak. Aynı sebeple Android Studio'dan
> derlediğinizi CI APK'sının üzerine de kuramazsınız.

## 2b. Uygulamayı tarayıcıda denemek (Appetize)

Telefon ve kurulum gerekmez: master'a her merge sonrası APK Appetize'a
yükleniyor ve koşunun **özet sayfasında** çalıştırılabilir bir bağlantı çıkıyor.

1. <https://github.com/McKose/NonStopAndroidApk/actions> → master'daki son koşu
2. Sayfanın üstündeki **Summary** bölümü → **"Uygulamayı tarayıcıda dene"**
3. Bağlantıya tıklayın; uygulama tarayıcıda açılır

**Dikkat:** APK'da sunucu anahtarları gömülü, yani Appetize'daki cihaz
**gerçek verinize** bağlanır. Orada girdiğiniz üye gerçekten kaydedilir.

**Appetize'da yapılamayan tek şey: veritabanı göçü testi.** Her oturum temiz bir
cihazla başlıyor, yani üzerine kurulacak eski bir sürüm yok; veritabanı doğrudan
güncel sürümle oluşur ve göç hiç koşmaz. Göç testi zorunlu olarak gerçek
telefonda, eski sürümün üstüne kurarak yapılıyor (bkz. bölüm 3).

### Tek seferlik ayar (Appetize için)

1. <https://appetize.io> hesabınıza girin → hesap ayarlarından bir **API token**
   alın
2. Depo → **Settings → Secrets and variables → Actions** → **Secrets** sekmesi →
   **New repository secret**
   - Name: `APPETIZE_API_TOKEN`
   - Secret: aldığınız token
3. master'a bir değişiklik gidince yükleme çalışır. İlk koşunun özetinde bir
   `publicKey` ve onu nereye yazacağınız yazacak:
   - Aynı sayfa → **Variables** sekmesi → **New repository variable**
   - Name: `APPETIZE_PUBLIC_KEY`, Value: özette yazan anahtar

Üçüncü adım **atlanmamalı**: anahtar sabitlenmezse her koşu Appetize'da yeni bir
uygulama açar, bağlantı her seferinde değişir ve daha önce paylaştığınız bağlantı
eski sürümü göstermeye devam eder.

Token eklenmezse CI **düşmez**, yükleme adımı atlanır ve sebebini günlüğe yazar.

### Sunucuya bağlanmak için tek seferlik ayar (APK ve panel için ortak)

Bu adım yapılmazsa APK yine kurulur ve açılır, ama giriş ekranında **"sunucu
ayarları eksik"** der ve hiçbir yere bağlanamaz.

Sebebi: projenin adresi ve anahtarı depoda tutulmuyor. Derleme sırasında depo
gizli anahtarlarından okunuyor.

**Önce iki değeri Supabase'den alın:**

1. Şu adresi açın:
   **<https://supabase.com/dashboard/project/jvkytncwedjcvssilhih/settings/api>**
   (elle: projeniz → sol altta **Project Settings** → **API**. Bazı projelerde
   bu sayfa **API Keys** adıyla görünüyor.)
2. **Project URL** kutusundaki değeri kopyalayın —
   `https://jvkytncwedjcvssilhih.supabase.co` biçiminde.
3. **Project API keys** bölümünde **`anon`** `public` etiketli anahtarı
   kopyalayın. Uzun bir metindir.

> **`service_role` / `secret` anahtarını ASLA kopyalamayın.** O anahtar bütün
> erişim kurallarını baypas eder. Ne buraya, ne depoya, ne `local.properties`
> dosyasına, ne de sohbete yapıştırılır.
>
> `anon` anahtarı ise gizli değil: uygulamanın içinde ve tarayıcıda zaten
> görünür, tek başına hiçbir veriye erişemez (erişimi satır bazlı güvenlik
> kuralları belirliyor). Gizli anahtar olarak tutulmasının sebebi gizlilik
> değil, kuruluma özgü olması.

**Sonra depoya ekleyin:**

1. Şu adresi açın:
   **<https://github.com/McKose/NonStopAndroidApk/settings/secrets/actions>**
   (elle: depo → **Settings** sekmesi → sol menüde **Secrets and variables** →
   **Actions**)
2. Sağ üstteki yeşil **New repository secret** düğmesine basın.
3. İki tane ekleyin — her biri için ad, değer, **Add secret**:

   | Name | Secret |
   |---|---|
   | `SUPABASE_URL` | 2. adımda kopyaladığınız Project URL |
   | `SUPABASE_ANON_KEY` | 3. adımda kopyaladığınız `anon` `public` anahtarı |

   Adları birebir böyle yazın; büyük/küçük harf ve alt çizgiler önemli.

4. **Yeni bir APK üretin.** Gizli anahtarlar derleme sırasında okunduğu için
   daha önce indirdiğiniz APK bunları içermiyor. Actions sayfasında
   **Android CI** iş akışını açıp **Run workflow** ile elle tetikleyebilir ya
   da depoya herhangi bir değişiklik gönderebilirsiniz.

5. Yeni koşu bitince APK'yı yukarıdaki adımlarla indirin. Giriş ekranı artık
   "sunucu ayarları eksik" demiyorsa ayar tuttu demektir.

**Aynı iki anahtar yayındaki paneli de besliyor.** Ekledikten sonra panelin de
yeniden yayınlanması gerekiyor: Actions → **Paneli yayınla** → **Run workflow**.
Panelin ayrıca tek seferlik bir adımı daha var (GitHub Pages'in açılması);
`web/README.md` → "Tek seferlik kurulum".

Değerler eksikken derlemeyi düşürmemek bilinçli: ayarları olmayan biri de
uygulamanın ekranlarına bakabilsin diye.

---

## 3. Kendi makinenizde derlemek (tam kontrol)

Kod değiştirip anında görmek için.

```bash
git pull origin master
```

Kök dizindeki `local.properties` dosyasına ekleyin (dosya yoksa oluşturun):

```properties
supabase.url=https://jvkytncwedjcvssilhih.supabase.co
supabase.anonKey=<anon public anahtarı>
```

Bu dosya `.gitignore` içinde, yani depoya gitmiyor.

Android Studio'da Gradle sync + Run. Değerler **derleme sırasında** gömülüyor;
dosyayı kaydetmek tek başına yetmez, yeniden derlemek gerekiyor.

---

## Uçtan uca akışı görmek

Uygulama ile panelin aynı veriyi gösterdiğini doğrulamak:

1. Uygulamada giriş yapın, birkaç üye ekleyin.
2. Ayarlar → **Sunucuya Eşitle**. Aynı satırda bekleyen değişiklik sayısını ve
   son durumu görürsünüz.
3. Paneli `?demo` **olmadan** açıp aynı hesapla girin — eklediğiniz üyeler
   listede olmalı.

### Arka planda gönderimi denemek

Uygulama tamamen kapalıyken de gönderim sürüyor. Denemek için:

1. Giriş yapın, birkaç üye ekleyin.
2. Uygulamayı tamamen kapatın — son kullanılanlar listesinden de kaydırın.
3. Uçak modunu açıp kapatın (ağın geri gelmesi işi tetikliyor).
4. **15 dakika içinde** panelde görünmeleri gerekiyor.

Aralık işletim sisteminin toplu uyandırma penceresine bağlı, yani 15 dakika
"en sık" değeri — garanti değil. Hemen görmek isterseniz Ayarlar →
**Sunucuya Eşitle** zaten anında tetikliyor.

Eşitleme ayrıca girişte, oturum geri yüklendiğinde ve uygulama önplandayken
dakikada bir kendiliğinden çalışıyor.

---

## Bir şey ters giderse

Hata mesajları ne yapılması gerektiğini söyleyecek şekilde yazıldı.

| Gördüğünüz | Anlamı |
|---|---|
| "Sunucu ayarları eksik" | APK'da: depo gizli anahtarları eklenmemiş ya da eklendikten sonra yeni APK üretilmemiş. Yerel derlemede: `local.properties` satırları yok |
| "E-posta veya şifre hatalı" | Şifre yanlış ya da hesap onaylanmamış — parantez içi kısım hangisi olduğunu söyler |
| "Hesabınız bir salona bağlı değil" | Sunucuda `gym_users` satırı eksik |
| Ekleme/silme düğmeleri yok, yerine kilitli bir şerit var | Rolünüz o tabloya yazamıyor — beklenen davranış; rolü yükseltin ya da yetkili hesapla girin |
| Ayarlar'da gerekçe "Erişim reddedildi (403)" | Yetkisiz yazma sunucuda reddedildi. Uygulama artık bunu baştan engelliyor; yine de görüyorsanız kayıt `0004` uygulanmadan önce kuyruğa girmiş olabilir |
| Ayarlar'da "Bekleyen: N" düşmüyor | Gönderim takılmış; aynı satırdaki gerekçe sebebi söyler |
| Panelde "Kurulum tamamlanmamış" | `web/config.js` oluşturulmamış — ya da adrese `?demo` ekleyin |
| Panelde liste boş ama hata yok | O tabloda veri yok; erişim sorunu olsaydı hata görürdünüz |
| Panelde "Süzgece uyan kayıt yok" | Veri var ama arama/tarih süzgeci eliyor — **Temizle** düğmesine basın |
| Actions'ta `nonstop-debug-apk` yok | Koşu başarısız bitmiş ya da yapıtın 14 günü dolmuş |
