# Sunucu tarafı

Uygulamanın verisinin tutulduğu ve web yönetim panelinin okuyacağı yer.

## Model

**Salon (kiracı).** Her satırda `tenant_id` var ve doğrudan `gyms.id`'yi
gösteriyor. Bugün tek salon var; şema baştan çok salonlu olduğu için ikinci salon
eklendiğinde hiçbir şey değişmiyor.

**Kimlik.** Her personelin kendi Supabase Auth hesabı var (e-posta + şifre).
Uygulama içine gömülen `anon` anahtarı tek başına hiçbir şeye erişemez; her sorgu
giriş yapan kişiye göre süzülür.

Uygulamadaki yerel `staff.password` alanı **sunucuya taşınmıyor**. Sunucudaki
`staff` tablosunda şifre kolonu yok; kimlik doğrulama tamamen Supabase Auth'a
ait. Düz metin şifreleri sunucuya kopyalamak çözmeye çalıştığımız sorunu
büyütmek olurdu.

**Erişim.** Her veri tablosunda tek bir kural var: *kullanıcı yalnızca bağlı
olduğu salonun satırlarına erişir.* Kuralın iki yarısı da gerekli —
`using` hangi satırların görüneceğini, `with check` hangi satırların
yazılabileceğini söyler. İkincisi olmasaydı bir istemci başka salonun
`tenant_id`'siyle satır yazabilirdi: okuyamayacağı ama bozabileceği veriye.

## Kurulum

1. **Proje aç.** [supabase.com](https://supabase.com) üzerinden yeni bir proje
   oluşturun.

2. **Şemayı uygulayın.** SQL Editor'de sırasıyla çalıştırın:
   - `migrations/0001_tenancy.sql`
   - `migrations/0002_data_tables.sql`
   - `migrations/0003_staff_auth_link.sql`

   Hepsi tekrar çalıştırılabilir: emin değilseniz yeniden koşturun, zarar vermez.

   `tests/` altındaki dosyalar **yüklenmez**; onlar yalnızca CI içindir.

3. **Salonu oluşturun.**
   ```sql
   insert into public.gyms (name) values ('Salon adınız') returning id;
   ```
   Dönen `id` değerini not edin — uygulamadaki `tenantId` bu olacak.

4. **Personel hesaplarını açın.** Bu adım SQL değil: Authentication → Users →
   Add user → Create new user. Her personel için bir e-posta ve şifre.
   **Auto Confirm User** işaretlensin — işaretlenmezse hesap doğrulama e-postası
   bekler ve giriş `invalid_grant` ile reddedilir.

5. **Kullanıcıları salona bağlayın.** UUID kopyalamamak için e-posta ve salon
   adından çözen biçim:
   ```sql
   insert into public.gym_users (user_id, gym_id, role)
   select u.id, g.id, 'ADMIN'          -- ya da MANAGER / TRAINER
   from auth.users u, public.gyms g
   where u.email = '<personelin e-postası>'
     and g.name  = '<salon adı>';
   ```
   `INSERT 0 1` dönmeli. `INSERT 0 0` dönerse e-posta ya da salon adı tutmamıştır.

   Bu adım atlanırsa kullanıcı giriş yapar ama **hiçbir satır göremez** — kural
   "bağlı olduğun salonun satırları" dediği için bağlantısı olmayan kullanıcının
   sonucu boş olur. Beklenen davranış budur.

6. **Uçtan uca doğrulayın.** Aşağıdaki bölüm.

## Kurulumun doğrulanması (6. adım)

Bu adım panelde **yapılmaz**. Panelin SQL Editor'ü veritabanına tablo sahibi
olarak bağlanır; sahip satır bazlı güvenliği baypas eder, orada her sorgu her
satırı görür. Yani panelde çalışan bir `select` hiçbir şey kanıtlamaz. Sınanması
gereken şey uygulamanın gireceği kapı: giriş yapmış bir personel kimliğiyle,
kuralların gerçekten uygulandığı yoldan veri gelip gelmediği.

Tek bir sorgu üç ayrı katmanı aynı anda doğruluyor: kimlik doğrulama, tablo
yetkileri (`grant`) ve salon yalıtımı kuralları.

**6.1 — Anahtarları alın.** Project Settings → API: `Project URL` ve
`anon public` anahtarı (yeni arayüzde "Publishable key", aynı şey). Kopyala
düğmesini kullanın; elle seçilince sonu kesilir ve hata anlaşılmaz olur.
`service_role` / `secret` anahtarı hiçbir yere yapıştırılmaz — o anahtar tüm
kuralları baypas eder ve uygulamaya da **konulmaz**.

**6.2 — Giriş yapıp jetonu alın** (PowerShell):

```powershell
$url    = "https://<proje-ref>.supabase.co"
$anon   = "<anon anahtarı>"
$eposta = "<4. adımdaki personelin e-postası>"
$sifre  = "<o hesabın şifresi>"

$giris = Invoke-RestMethod -Method Post `
  -Uri "$url/auth/v1/token?grant_type=password" `
  -Headers @{ apikey = $anon } `
  -ContentType "application/json" `
  -Body (@{ email = $eposta; password = $sifre } | ConvertTo-Json)

$jeton = $giris.access_token
$jeton.Substring(0,16)      # "eyJhbGciOiJIUzI1" benzeri → giriş başarılı
```

Jeton değişkende tutuluyor: 800 karakterlik bir metni kopyalayıp yapıştırmak
hataya davetiye. Ömrü **1 saat**; sonra bu adım tekrarlanır.

Burada `invalid_grant` dönerse şifre yanlıştır **ya da** hesap onaylanmamıştır
(4. adımda "Auto Confirm User" işaretlenmemiş). İkincisi için: Authentication →
Users → kullanıcı → ⋯ → Confirm email.

**6.3 — Asıl sınav:**

```powershell
Invoke-RestMethod -Uri "$url/rest/v1/gyms?select=*" `
  -Headers @{ apikey = $anon; Authorization = "Bearer $jeton" }
```

Beklenen: **tam olarak bir satır** — kullanıcının bağlı olduğu salon.

| Ne döndü | Anlamı | Ne yapmalı |
|---|---|---|
| tek satır | kurulum doğru | — |
| boş liste | jeton geçerli, tablo erişilebilir, kullanıcı hiçbir salona bağlı değil | 5. adım |
| `permission denied for table gyms` (`42501`) | `grant`'lar yok, `0002`'nin son bloğu koşmamış | `0002`'yi tekrar uygulayın |
| `401` / `JWT expired` | jeton yok ya da süresi dolmuş | 6.2'yi tekrarlayın |
| `relation "public.gyms" does not exist` (`42P01`) | `0001` koşmamış | `0001`'i uygulayın |

Boş liste ile hata arasındaki fark önemli: boş liste bir erişim sorunu
**değildir**. Kural "bağlı olduğun salonun satırları" dediği için, bağlantısı
olmayan kullanıcının doğru cevabı boş kümedir.

**6.4 — İki ek kontrol.**

Bir veri tablosu (`/rest/v1/products?select=*`, aynı başlıklarla) **boş** dönmeli
— hata değil boş. Tablo var, izin var, henüz veri yok demektir.

Aynı sorgu `Authorization` başlığı olmadan, yalnızca `apikey` ile **hata**
dönmeli. Bu, `anon` anahtarının APK içine gömülebilmesinin dayanağı: anahtar tek
başına hiçbir veriye ulaşmıyor. Buradan veri gelseydi tasarımda ciddi bir hata
olurdu.

## Kuralların CI'da doğrulanması

Şema ve erişim kuralları her CI koşusunda gerçek bir PostgreSQL üzerinde
sınanıyor (`tests/run.sh`). Test iki salon ve iki kullanıcı kurup şunları
doğruluyor:

- kullanıcı yalnızca kendi salonunun satırlarını görüyor,
- başka salona yazma **reddediliyor**,
- başka salonun satırını silme hiçbir satırı etkilemiyor,
- kendi salonuna yazma çalışıyor (kural fazla kısıtlayıcı da olmamalı).

Testin dişli olduğu ayrıca sınandı: politikadan `with check` kaldırıldığında test
sıfırdan farklı kodla düşüyor. Yalıtım kuralları sessizce bozulabilecek türden
olduğu için bu önemli — uygulama çalışmaya devam eder, diğer testler geçer, veri
sızar.

Yerelde koşturmak için:

```bash
PGURL="postgres://postgres:postgres@localhost:5432/postgres" ./supabase/tests/run.sh
```

## Uygulamayı bu projeye bağlama

Bu adım **kendi bilgisayarınızda**, projeyi derlediğiniz makinede yapılır.

Proje adresi ve `anon` anahtarı depoya işlenmiyor; `local.properties` dosyasından
okunuyor (o dosya `.gitignore`'da). Anahtarın kendisi gizli değil — istemcide
bulunması normaldir ve tek başına hiçbir veriye erişemez. Depoya konmamasının
sebebi başka: bu değerler kuruluma özgü, kaynak koda değil.

1. Projenin **kök dizinindeki** `local.properties` dosyasını açın (yoksa
   oluşturun; Android Studio genelde `sdk.dir` satırıyla zaten oluşturur).
2. Sonuna şu iki satırı ekleyin:
   ```properties
   supabase.url=https://<proje-ref>.supabase.co
   supabase.anonKey=<anon public anahtarı>
   ```
   İkisi de Project Settings → API altında.
3. Uygulamayı **yeniden derleyin** (Gradle sync + Run). Değerler derleme
   sırasında gömülüyor; sadece dosyayı kaydetmek yetmez.

Satırlar eksikse derleme düşmez — uygulama açılır ve giriş ekranında
"Sunucu ayarları eksik" der. Bilinçli: projeyi ilk kez klonlayan birinin hiçbir
şeyi derleyememesi daha kötü olurdu.

`service_role` anahtarı buraya da **konmaz**; o anahtar tüm erişim kurallarını
baypas eder.

## Personeli hesabına bağlama

Bir personelin uygulamada "bugün benim derslerim" listesini görebilmesi için
personel kaydının Supabase hesabına bağlanması gerekiyor.

1. Panel → Authentication → Users → ilgili kullanıcının **UID** değerini kopyalayın.
2. Uygulamada Ayarlar → Personel → kişiyi açın → **Supabase kullanıcı kimliği**
   alanına yapıştırın → Güncelle.

Elle yapılıyor çünkü uygulama `auth.users` tablosunu okuyamıyor; erişim kuralları
buna izin vermiyor ve vermesi de istenmez.

Bağlantı kurulmadan da giriş yapılabilir — yalnızca randevu eşleşmesi kurulamaz.
Ders vermeyen bir kullanıcı (ör. salon sahibi) için bu zaten doğru sonuç.

## Henüz yapılmadı

- **Oturum kalıcı değil.** Uygulama kapanınca tekrar giriş isteniyor: oturum
  şimdilik bellekte tutuluyor. Kalıcı saklama (Android'de şifreli tercih dosyası,
  iOS'ta Keychain) `SessionStore` arayüzünün arkasında gelecek; açılışta oturumu
  geri yükleyen çağrı da o adımda, giriş ekranı gösterilmeden önce beklenecek
  şekilde eklenecek.
- **Sunucudan aşağı çekme yok.** Senkronizasyon tek yönlü: cihazdan sunucuya.
  Panelden yapılan bir değişiklik cihaza inmiyor.
- **Role dayalı ince yetkilendirme.** `gym_users.role` artık oturuma taşınıyor ve
  uygulama içi yetkiyi belirliyor, ama **sunucu kuralları** hâlâ rolü ayırt
  etmiyor: salona bağlı olan yazabiliyor.
- **`staff.password` kolonu duruyor** ama hiçbir yerde okunmuyor ve sunucuya
  gönderilmiyor. Şemadan kaldırılması ayrı bir geçiş.
