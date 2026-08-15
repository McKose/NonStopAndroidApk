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
   - `migrations/0004_role_based_access.sql`

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

   **Rol seçimi önemli** (0004'ten sonra): rol yalnızca bilgi değil, ne
   yazabileceğini belirliyor. Kendi hesabınıza `ADMIN` verin — `MANAGER`
   verirseniz personel ekleyemez, `TRAINER` verirseniz fiyat da
   değiştiremezsiniz. Ayrıntı için "Kim neyi yazabilir" bölümüne bakın.

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
sınanıyor (`tests/run.sh`). Migrasyonlar **iki kez** uygulanıyor: tekrar
çalıştırılabilir olmaları şart, çünkü kurulum sırasında yarıda kalan bir dosya
yeniden koşturulacak.

`10_rls_test.sql` — salonlar arası yalıtım:

- kullanıcı yalnızca kendi salonunun satırlarını görüyor,
- başka salona yazma **reddediliyor**,
- silme hiç kimseye açık değil,
- kendi salonuna yazma çalışıyor (kural fazla kısıtlayıcı da olmamalı).

`20_role_test.sql` — salon içi rol ayrımı. Her rol için hem izin verilen hem
reddedilen yol ayrı ayrı kanıtlanıyor; yalnızca reddi sınamak "her şeyi reddet"
diye yazılmış bozuk bir kuralı da geçirirdi.

**Testlerin dişli olduğu tek tek sınandı.** Kural dosyasına altı ayrı bozma
uygulandı ve her birinde takım düştü: eski tek parçalı kuralın bırakılması,
`staff`'ın herkese açılması, `products`'ın herkese açılması, `DELETE` yetkisinin
bırakılması, güncellemede `with check`'in kaldırılması ve rol süzgecinin
kaldırılması.

Bunlardan biri ilk yazımda **yakalanmıyordu**: `with check` kaldırıldığında
testler geçmeye devam ediyordu, çünkü iddia tek salonlu bir kullanıcıyla
yazılmıştı ve satır taşıma zaten okuma kuralına takılıyordu — yani testin
düşmesinin sebebi sınamak istediği kural değildi. İddia, iki salonda farklı role
sahip bir kullanıcıyla yeniden yazıldı; ayırt edici olan bu.

Yerelde koşturmak için:

```bash
PGURL="postgres://postgres:postgres@localhost:5432/postgres" ./supabase/tests/run.sh
```

Yeni bir test dosyası eklemek için `tests/` altına `NN_ad.sql` koymak yeterli:
`run.sh` dosyaları dizinden okuyor. Elle sayılan bir listede yeni dosyayı
eklemeyi unutmak, o testin **hiç koşmaması** ama takımın yeşil kalması demekti.

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

## Kim neyi yazabilir

Salona bağlı olmak artık tek başına yetmiyor; `gym_users.role` ne
yazabileceğinizi de belirliyor (migrasyon `0004`).

| Tablo | Okuma | Yazma (ekleme + güncelleme) |
|---|---|---|
| `staff` — personel, maaş, hakediş | salona bağlı herkes | **yalnızca ADMIN** |
| `gym_packages`, `products` — fiyat listesi | salona bağlı herkes | **ADMIN, MANAGER** |
| `gym_members`, `appointments`, `orders`, `measurements`, `ledger_entries`, `stock_movements` — günlük iş | salona bağlı herkes | ADMIN, MANAGER, TRAINER |

**Okuma bilinçli olarak bölünmedi.** Bir eğitmenin randevu yazabilmesi için üye
listesini, satış yapabilmesi için fiyatı görmesi zaten gerekiyor. Okumayı role
göre daraltmak ekranları boş gösterirdi.

**Silme hiçbir role açık değil.** Uygulama zaten hiç `DELETE` göndermiyor:
silme mezar taşıyla yapılıyor (`deleted_at_ms` doldurulur), yani sunucuya giden
şey bir güncelleme. Yetkiyi tamamen geri almak bu yüzden hiçbir şeye mal olmuyor
ama kalıcı veri kaybını imkânsız kılıyor — ele geçirilmiş bir jetonla bile satır
silinemez, en fazla mezar taşı konur ve mezar taşı geri alınabilir.

### Uygulama da aynı kuralı biliyor

Kural iki yerde yazılı ve bu bilinçli:

- **Sunucu** kuralı *zorunlu tutuyor*. Tek yetkili o; uygulama yanlış olsa bile
  sunucu yazmayı reddeder.
- **Uygulama** aynı kuralın bir kopyasını taşıyor (`SyncTable.writableBy`) ve
  yetkisi olmayan kullanıcıya ekleme/silme düğmelerini hiç göstermiyor. Kopya
  olmasaydı eğitmen fiyatı değiştirir, kayıt kuyruğa girer ve sonucu ancak
  senkronizasyon turunda 403 olarak öğrenirdi.

İkisinin ayrışması sessiz bir hata olurdu, bu yüzden `SyncTableRolesTest`
migrasyon dosyasını okuyup uygulamadaki tabloyla karşılaştırıyor: biri değişip
diğeri değişmezse CI düşüyor. **Migrasyon kaynak sayılıyor**, uygulama değil.

### Yetkisiz yazma denenirse ne oluyor

Sunucu `403` döner, uygulama bunu **kalıcı hata** sayar ve kayıt gönderim
kuyruğunda işaretli kalır — sonsuza kadar tekrar denenmez. Ayarlar ekranındaki
senkronizasyon durumu "reddedildi" der.

`403`'ün iki sebebi olabilir ve ayırt etmenin yolu sunucunun döndürdüğü
gövdedir:

| Sebep | Çözüm |
|---|---|
| Kullanıcı `gym_users`'a hiç eklenmemiş | Kurulum 5. adımı çalıştırın |
| Eklenmiş ama rolü bu tabloya yetmiyor | Rolü yükseltin ya da işlemi yetkili hesapla yapın |

Rolü değiştirmek için:
```sql
update public.gym_users
   set role = 'ADMIN'
 where user_id = (select id from auth.users where email = '<e-posta>');
```

## Senkronizasyon nasıl çalışıyor

**Yukarı (cihaz → sunucu).** Her yazma, satırı değiştiren işlemle aynı
transaction içinde gönderim kuyruğuna kayıt bırakıyor. Kuyruk sırayla
boşaltılıyor; başarısız kayıt kuyrukta kalıp üstel geri çekilmeyle tekrar
deneniyor.

**Aşağı (sunucu → cihaz).** Tablo başına bir su işareti tutuluyor: en son hangi
ana kadar okunduğu. Her turda o andan itibaren değişen satırlar iniyor.

**Çakışma kuralı:** yerelde gönderim bekleyen bir satır varsa sunucudaki hâli
**atlanıyor**. O satır için henüz yukarı çıkmamış bir değişiklik var demektir;
üzerine yazmak kullanıcının az önce yaptığı işi geri almak olurdu. Gönderim
tamamlandıktan sonra bir sonraki turda güncel hâli zaten iniyor.

**Sıra:** önce gönderim, sonra indirme. Ters sırada, bekleyen satırlar bir tur
boyunca atlanır ve indirme sürekli bir tur geriden gelirdi.

Silmeler de iniyor: silinen satır fiziksel olarak durmuyor, `deleted_at_ms` ile
işaretleniyor. Süzülseydi bir cihazda silinen üye diğerinde sonsuza kadar
görünmeye devam ederdi.

## Henüz yapılmadı

- **iOS tarafında oturum saklama yok.** Android'de oturum Keystore ile
  şifrelenip saklanıyor ve uygulama kapansa da korunuyor; iOS uygulaması
  yazıldığında Keychain karşılığı `SessionStore` arayüzünün arkasına eklenecek.
- **Arkaplanda gönderim yok.** Tetikleme girişte, uygulama önplandayken dakikada
  bir ve Ayarlar'daki "Sunucuya Eşitle" ile oluyor. Uygulama tamamen kapalıyken
  gönderim yapılmıyor; bunun için bir arka plan işi (WorkManager) gerekiyor.
- **`staff.password` kolonu duruyor** ama hiçbir yerde okunmuyor ve sunucuya
  gönderilmiyor. Şemadan kaldırılması ayrı bir geçiş.
