# `iletisim-istegi`

Siteden gelen iletişim / deneme dersi formunun **tek yazma yolu**.
`contact_requests` tablosuna `service_role` ile yazıyor.

## Neden bir fonksiyon — tabloya `anon` yazma kuralı yazmak yerine

Gerekçenin tamamı `supabase/migrations/0010_iletisim_istekleri.sql` başında.
Özeti:

- `anon` **yayınlanmış** anahtarla çalışıyor (anahtar uygulamanın içinde ve
  panelin kaynağında), yani tabloya `insert` açmak "internetteki herkes
  istediği hızda satır yazabilir" demek.
- Sızacak veri yok ama zarar var: bir betik salonun kotasını tüketip gerçek
  talepleri gömebilir.
- Tablo kuralı **hız sınırı koyamıyor**: `with check` yalnız satıra bakıyor,
  "son bir saatte kaç satır geldi" sorusunu soramıyor.

Yani bu fonksiyon yalnız yetki için değil, **üç kontrol** için var:
doğrulama, mükerrer bastırma ve hız sınırı.

## Çağıran kişinin hesabı yok

`personel-davet` ve `personel-erisim-kaldir` çağıranın kimliğini
`/auth/v1/user` ile doğruluyor. Burada doğrulanacak kimlik yok — formu
dolduran bir ziyaretçi.

Uç yine de çıplak değil: `verify_jwt` **açık** ve `anon` anahtarı geçerli bir
JWT olduğu için istek o anahtarla geliyor. Ama anahtar herkesin elinde
olduğundan bu **hiçbir koruma sayılmaz**; korumayı üç kontrol sağlıyor.

## Sözleşme

`POST` · gövde JSON:

| Alan | Zorunlu | Not |
|---|---|---|
| `tenantId` | evet | uuid; `gyms.id`e işaret etmeli |
| `adSoyad` | evet | 2–80 karakter |
| `telefon` | evet | **yalnızca** `+90` + 10 hane |
| `eposta` | hayır | en çok 120 karakter |
| `mesaj` | hayır | en çok 500 karakter |
| `tuzak` | hayır | tuzak alanı; dolu gelirse yazma yapılmaz |

Yanıtlar: `201 {tamam:true}` · `200 {tamam:true}` (tuzak) ·
`200 {tamam:true,mukerrer:true}` · `400` · `429` · `500`.

### Telefon normalleştirmesi burada DEĞİL

Fonksiyon yalnızca kanonik biçimi (`+90XXXXXXXXXX`) kabul ediyor. İnsanın
yazdığı `0541 971 50 95`, `+90 541…`, `541…` biçimlerini o hâle çeviren yer
**`web/iletisim-formu.js`** ve orada Node testleriyle sınanıyor.

Sebebi sınanabilirlik: Edge Function'lar Deno'da koşuyor ve bu depoda onlar
için test koşucusu yok, yalnızca `tsc` denetimi var. Normalleştirmeyi buraya
koymak, **en ince kuralı** (baştaki sıfır) testsiz bırakmak olurdu —
`+900541…` diye kaydedilen bir numara aranmıyor, üstelik ekranda doğru
görünüyor.

Mantık iki yerde değil: normalleştirme orada, kanonik biçim doğrulaması
burada. İkisi aynı işi yapmıyor ve yetkili taraf **burası**.

## Mükerrer ve hız sınırında yanıt neden "başarılı" olabiliyor

Aynı numaradan 10 dakika içinde ikinci istek gelirse yeni satır yazılmıyor
ama istemciye **başarılı** dönüyor. Sebebi somut: insanlar gönder düğmesine
iki kez basıyor ve "zaten gönderdiniz" demek, gönderip göndermediğinden emin
olmayan birini telefona sarılmaktan alıkoyar. Panelde de aynı talebin iki
kopyası, personelin aynı kişiyi iki kez aramasına yol açardı.

Tuzak alanı dolu geldiğinde de başarılı dönülüyor: bota "başarısız" demek,
denemeye devam etmesi için bilgi vermek olurdu.

## Hız sınırının bedeli bilinçli kabul edildi

Sınır **salon bazında saatte 20 istek**. Gerçek bir salon günde bir elin
parmakları kadar talep alıyor, yani 20 fazlasıyla geniş.

Ama bir saldırgan sınırı doldurursa o saatteki **gerçek talepler de**
reddedilir. Bu, sınırsız yazmaya izin vermenin alternatifi ve daha az kötü
olanı. Bedeli yumuşatan şey reddin **çıkışsız olmaması**: yanıt ziyaretçiyi
WhatsApp'a ve telefona yönlendiriyor, ikisi de sayfada duruyor. Saldırı
altında bile salona ulaşmanın yolu kapanmıyor, yalnız form kapanıyor.

## Ortam değişkenleri

| Değişken | Kaynak |
|---|---|
| `SUPABASE_URL` | Supabase otomatik veriyor |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase otomatik veriyor |
| `IZINLI_KAYNAKLAR` | İsteğe bağlı; yoksa gömülü liste kullanılıyor |

`IZINLI_KAYNAKLAR` üç fonksiyonda **aynı** değişkenden okunuyor; ayrışsalar
sitenin bir düğmesi çalışır, diğeri CORS'ta sessizce dururdu.

## Dağıtım

```bash
supabase functions deploy iletisim-istegi
```

**Güvenilir yol bu.** `personel-erisim-kaldir`ın BENIOKU'sunda yazılı
gerekçeyle: kaynağı elle bir araç çağrısına aktarmak hata üretiyor —
02.11.2026'da tam bu yolla bir yorumdaki kelime bozulmuştu.

### Canlıdaki sürümün depo dosyasından farkı

Canlı **sürüm 2**, bu oturumda MCP aracıyla ve **yorumlar olmadan**
dağıtıldı (kişisel erişim jetonu olmadığı için CLI kullanılamadı).
Doğrulandı: ziyaretçiye görünen on üç mesaj, altı sabit, iki regex ve bütün
REST yolları/kolon adları depo dosyasıyla birebir aynı. Fark **yalnızca
yorumlar**.

İlk dağıtımda (sürüm 1) Türkçe karakterler sadeleştirilmişti ve o dizgeler
ziyaretçiye görünüyor — yani canlıda "Adinizi kontrol edin." yazıyordu.
Sürüm 2 bunu düzeltti. Ders aynı: **aktarılan kaynak depo dosyası değildir.**

Elinde jeton olan biri yukarıdaki komutu bir kez çalıştırdığında canlı ile
depo bire bir eşitlenir.

## Henüz yapılmadı

**Uç UÇTAN UCA SINANMADI.** Bu oturumdan `*.supabase.co` adresine istek
atılamıyor (ağ politikası CONNECT'i 403'lüyor), yani gerçek bir `POST`
denenemedi. Doğrulanan şeyler: `tsc` denetimi (CI'ın birebir aynısı, dört
dosya tek çağrıda), dağıtılan kaynağın depo dosyasıyla eşliği, tablo
yetkilerinin canlıda doğru olması ve şema testleri.

Sınanmayanlar: CORS başlıkları, `Content-Range` sayımının gerçek biçimi, ve
yazmanın gerçekten satır üretmesi. Form siteye bağlandığında ilk gönderim bu
üçünü birden doğrulayacak; `query_logs` ile `function_edge_logs` kaynağına
bakmak da yeterli.
