# Web yönetim paneli

Salonun verisini tarayıcıdan görüntüleyen panel. Uygulamanın gönderdiği veriyi
okuyor; aynı Supabase projesine bağlanıyor.

## Ne yapar, ne yapmaz

**Yapar:** üyeler, paketler, randevular ve finans defterini listeler. Giriş,
uygulamadaki hesapların aynısıyla (e-posta + şifre).

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

## Yerelde denemek

`file://` üzerinden açmayın — tarayıcı ES modüllerini o protokolde yüklemiyor.
Herhangi bir statik sunucu yeter:

```bash
cd web
python3 -m http.server 8000
```

Sonra `http://localhost:8000` adresini açın.

## nonstopstudio.tr üzerinde yayınlamak

Panel statik olduğu için seçenek çok; en az bakım isteyeni:

1. **Netlify** ya da **Vercel** hesabı açın, GitHub deposunu bağlayın.
2. Yayın (publish) dizinini `web` olarak ayarlayın; derleme komutu **boş**.
3. `config.js` depoda olmadığı için yayın ortamına elle eklenmeli. İki yol var:
   - Netlify/Vercel arayüzünden dosyayı ortam dosyası olarak eklemek, ya da
   - `config.js`'i yayın öncesi oluşturan tek satırlık bir komut kullanmak:
     ```bash
     printf 'window.NONSTOP_CONFIG={url:"%s",anonKey:"%s"};' "$SUPABASE_URL" "$SUPABASE_ANON_KEY" > web/config.js
     ```
     `SUPABASE_URL` ve `SUPABASE_ANON_KEY` değerlerini panelin ortam
     değişkenlerine girersiniz.
4. Alan adı ayarlarında `nonstopstudio.tr` alanını bağlayın; sertifika otomatik
   geliyor.

Alternatif olarak kendi sunucunuza da koyabilirsiniz: `web` klasörünü herhangi
bir HTTP sunucusunun kök dizinine kopyalamak yeterli.

### Supabase tarafında bir ayar gerekiyor mu

Hayır. Panel `anon` anahtarıyla ve giriş yapan kullanıcının jetonuyla çalışıyor;
uygulamanın kullandığı yolun aynısı. Yalnızca alan adınızı Supabase panelinde
**Authentication → URL Configuration** altındaki izinli adresler listesine
eklemeniz gerekebilir (şifre sıfırlama e-postaları için).

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
