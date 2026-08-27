# `admin.nonstopstudio.tr` — panel için ayrı adres

Yönetim paneli bugün `nonstopstudio.tr/panel/` adresinde çalışıyor ve siteden
ona bağlantı verilmiyor. İstenen, panelin **`admin.nonstopstudio.tr`**
adresinden açılması.

Bu belge işin **sende** olan kısmını (Turhost paneli, GitHub ayarları) ve
**depoda** olan kısmını (yayın akışı) birlikte anlatıyor.

---

## Önce: neden DNS'te tek satırla olmuyor

Akla ilk gelen çözüm şu: Turhost'ta `admin` için bir CNAME açıp GitHub
Pages'e bakmasını sağlamak. **Bu çalışmıyor** ve çalışmadığı ancak denendikten
sonra anlaşılıyor:

- GitHub Pages **site başına yalnızca BİR** özel alan adı kabul ediyor. Bizde o
  ad `nonstopstudio.tr`.
- Pages, kendisine gelen ama kanonik olmayan her isteği **301 ile** kanonik
  adrese yolluyor.

Yani `admin.nonstopstudio.tr` Pages'e baksa bile ziyaretçi panele değil,
**açılış sayfasına** düşer. DNS doğru, sertifika doğru, sonuç yanlış.

Alan adının DNS'i zaten Turhost'ta ve panel derleme adımı olmayan düz statik
dosyalardan ibaret. Bu yüzden seçilen yol: **paneli Turhost hostingine koymak**
ve alt alan adını oraya bağlamak.

---

## Ne gerekiyor

**Turhost'ta bu alan adı için bir hosting (web barındırma) paketi.** Yalnızca
alan adı kayıtlıysa yetmiyor: dosyaların duracağı bir yer lazım.

Emin değilsen Turhost müşteri panelinde bak: "Hosting" / "Barındırma" başlığı
altında bu alan adına bağlı bir paket görünüyor mu, cPanel (ya da Plesk)
girişin var mı?

- **Varsa** → aşağıdaki adımlar.
- **Yoksa** → en küçük Linux paketi yeter; panel birkaç yüz kilobayt ve PHP,
  veritabanı, e-posta gerektirmiyor. (Panelin verisi Supabase'de duruyor,
  hostingde değil.)

> **Turhost'un "URL Yönlendirme" özelliği çözüm değil.** O, ziyaretçiyi
> `nonstopstudio.tr/panel/` adresine **taşır** — adres çubuğunda yazan da o
> olur. İstenen, panelin admin adresinde *durması*. Yönlendirme yalnızca
> "şimdilik kimse eski adresi aramasın" demek için işe yarar.

---

## Adımlar (Turhost paneli)

### 1. Alt alan adını oluştur

cPanel → **Alt Alan Adları (Subdomains)**:

| Alan | Değer |
|---|---|
| Subdomain | `admin` |
| Domain | `nonstopstudio.tr` |
| Document Root | (öneriliyorsa olduğu gibi bırak) |

**Document Root değerini bir yere not et** — GitHub ayarına birazdan aynısı
girilecek. cPanel genelde `public_html/admin` ya da `/admin.nonstopstudio.tr`
gibi bir yol öneriyor.

DNS de Turhost'ta olduğu için alt alan adının `A` kaydı bu adımda **kendiliğinden**
açılıyor. Açılmazsa DNS bölümünden `admin` için hosting sunucusunun IP'sine bir
`A` kaydı ekle.

### 2. SSL sertifikası çıkar

cPanel → **SSL/TLS Status** (ya da "Let's Encrypt" / "SSL Yönetimi") →
`admin.nonstopstudio.tr` satırını seç → **Run AutoSSL** / **Sertifika Kur**.

Bu adım atlanırsa adres `https://` ile açılmaz. Panel girişi şifre taşıyor;
`http://` üzerinden açılmasına izin verme.

### 3. FTP bilgilerini al

cPanel → **FTP Hesapları**. Ya mevcut ana FTP hesabını kullan ya da bu iş için
yeni bir tane aç (tercih edileni bu: yetkisi yalnızca panel klasörüyle sınırlı
kalır).

Not etmen gerekenler:

- **Sunucu adresi** (ör. `ftp.nonstopstudio.tr` — Turhost bazen sunucu adı
  veriyor)
- **Kullanıcı adı**
- **Parola**
- **Dizin** — 1. adımdaki Document Root

### 4. GitHub ayarlarını gir

`https://github.com/McKose/NonStopAndroidApk/settings/secrets/actions`

**Secrets → New repository secret** (üçü de gizli):

| Ad | Değer |
|---|---|
| `TURHOST_FTP_SUNUCU` | FTP sunucu adresi |
| `TURHOST_FTP_KULLANICI` | FTP kullanıcı adı |
| `TURHOST_FTP_SIFRE` | FTP parolası |

**Variables → New repository variable** (gizli değil):

| Ad | Değer |
|---|---|
| `TURHOST_FTP_DIZIN` | Document Root (ör. `/admin.nonstopstudio.tr`) |

> **Dizin neden secret değil:** yayın akışının güvenlik kontrolleri ve hata
> mesajları bu değeri ekrana yazmak zorunda ("dizin kök olamaz", "şu dizine
> gönderiliyor"). Secret olsaydı GitHub onu `***` ile maskelerdi ve yanlış
> dizin hatası okunamaz hâle gelirdi. Gizlenecek bir yanı da yok: sır olan
> parola.

### 5. Yayını başlat

`https://github.com/McKose/NonStopAndroidApk/actions` → soldan
**"Admin panelini yayınla (Turhost)"** → sağdaki **Run workflow**.

Bundan sonrası kendiliğinden: `web/panel/` altında bir değişiklik master'a
girdiğinde panel hem Pages'e hem Turhost'a gidiyor.

---

## Yayın akışı ne yapıyor

`.github/workflows/admin-yayin.yml`:

1. Turhost bilgileri tanımlı mı bakar. **Değilse iş kırmızıya boyanmaz** —
   atlanır ve sebebi yazılır. (Aksi hâlde hosting alınana kadar her master
   merge'ü başarısız görünür, gerçek hatalar bu gürültüde kaybolurdu.)
2. Gönderilecek dosyaları `panel/index.html`ten başlayıp referansları izleyerek
   **türetir** — elle tutulan liste yok.
3. `panel/` önekini kırpar: panel Turhost'ta **kökte** duruyor.
4. `config.js`i depo gizli anahtarlarından yazar (Pages'teki desenin aynısı).
5. Arama motorlarına kapatan `robots.txt` üretir.
6. FTPS ile gönderir.
7. `https://admin.nonstopstudio.tr/` gerçekten **panelle** mi açılıyor diye
   bakar ve açılmıyorsa uyarı basar (işi düşürmez).

### Silme neden ilk yayında yapılmıyor

Aktarım `mirror --delete` kullanıyor: pakette olmayan dosya sunucudan da
silinir. Doğru dizinde istenen budur — silinmiş bir panel modülü sunucuda
kalmamalı. **Yanlış** dizinde ise geri dönüşü olmayan bir kayıp.

Bu yüzden paketin içinde `.nonstop-admin` adında bir imza dosyası gidiyor ve
silme **yalnızca o dosya hedefte zaten varsa** açılıyor. Yani ilk yayın hiçbir
şey silmez; ikinciden itibaren temizlik yapar. Dizin süzgeci (kök ve
`public_html` reddediliyor) insan hatasını azaltıyor, imza dosyası kalanı da
kapatıyor.

### Sunucunun kendi dosyalarına dokunulmuyor

Klasörde bize ait olmayan şeyler de var ve üçü açıkça dışarıda bırakılıyor —
ne gönderiliyor ne siliniyor:

| Yol | Neden korunuyor |
|---|---|
| `.well-known/` | Let's Encrypt sertifikayı buradan doğruluyor. Silinseydi sertifika ilk **yenilemede** (90 gün) tükenir, panel bir sabah "güvenli değil" uyarısıyla açılmaz olurdu — ve aradaki gecikme yüzünden sebebi bu yayınla ilişkilendirilemezdi. |
| `.htaccess` | Turhost'un ya da senin eklediğin sunucu ayarı (https yönlendirmesi gibi). Paket böyle bir dosya üretmiyor. |
| `cgi-bin/` | cPanel her doküman kökünde açıyor. |

Yani bu klasöre elle eklediğin bir `.htaccess` yayınlar arasında kaybolmaz.

---

## Bir şey olmazsa

| Belirti | Sebep | Ne yapılır |
|---|---|---|
| İş "Admin yayını atlandı" diyor | Secret/variable eksik | 4. adım |
| `certificate common name doesn't match` | Turhost'un FTP sertifikası adresle eşleşmiyor | Turhost'tan doğru FTP adresini iste. Olmuyorsa `TURHOST_FTP_SERTIFIKA_DOGRULA` variable'ını `false` yap — bağlantı şifreli kalır ama araya girene karşı koruma kalkar |
| `cd ... failed` | `TURHOST_FTP_DIZIN` yanlış | cPanel'deki Document Root ile birebir aynı olmalı |
| Adres yanıt vermiyor (uyarı) | DNS yayılmamış ya da SSL çıkarılmamış | 15–30 dk bekle; sürerse 1. ve 2. adım |
| "Adres açılıyor ama panel değil" | Alt alan adı başka klasöre bağlı | Document Root ile `TURHOST_FTP_DIZIN` aynı yeri göstermiyor |
| Panel "Kurulum tamamlanmamış" diyor | `SUPABASE_URL` / `SUPABASE_ANON_KEY` secret'ları yok | Panel yayında ama sunucu ayarı eksik; `?demo` ile yine açılır |

---

## Panel iki adreste birden — bilerek

`nonstopstudio.tr/panel/` **kalıyor**. Turhost tarafı hazır değilken tek
kopyayı oraya taşımak, panelin bir süre hiç açılmaması demek olurdu. İkisi de
aynı depodan, aynı türetilmiş listeden yayınlanıyor; ayrışma ihtimali yok.

Admin adresinin çalıştığı doğrulandıktan sonra Pages kopyası kaldırılabilir.
O bir karar — söylersen yaparım.

---

## Panelin güvenliği hakkında

Adresin `admin.` ile başlaması panele **erişimi kısıtlamıyor**; sadece
bulmasını kolaylaştırıyor. Koruma, her zamanki gibi girişte ve sunucudaki
erişim kurallarında:

- Panel giriş istiyor; girişsiz hiçbir veri gelmiyor.
- Hangi rolün neyi görebildiğine **Supabase** karar veriyor, panel değil.
  Yani panelin adresini bilen biri veriye ulaşamaz.
- `noindex` meta etiketi ve `robots.txt` ile arama motorlarına kapalı: aranan
  bir sayfa değil ve dizine girmesi yalnızca yönetim adresini ilan ederdi.
