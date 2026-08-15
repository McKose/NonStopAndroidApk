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

## Arama ve tarih süzgeci

Üyeler, Paketler, Randevular ve Finans sekmelerinde listenin üstünde bir süzgeç
çubuğu var: arama kutusu, (tablo tarih taşıyorsa) tarih aralığı ve sayaç.

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

### Testler

```bash
cd web && npm test        # TZ=Europe/Istanbul ile koşar
```

Saat dilimi **bilinçli** olarak ayarlanıyor: koşucular UTC ve UTC'de yerel gün
ile UTC gün aynı çıkıyor, dolayısıyla tarih hesabını bozan bir hata testlerden
geçerdi — ama salonun makinesinde (UTC+3) yanlış sonuç verirdi.
