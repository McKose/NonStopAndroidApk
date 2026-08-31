# `personel-davet` — kurulum ve kullanım

Bir personele uygulama erişimi verir: Auth hesabını açar, salon yetkisini yazar
ve personel kaydına bağlar. Üçü tek adımda.

Bu üçlünün neden birlikte yapılması gerektiği [`../../README.md`](../../README.md)
içindeki "Personele uygulama erişimi verme" bölümünde.

---

## Ne kadar sürer

Tek seferlik kurulum ~10 dakika. Sonrasında her davet birkaç saniye.

## Önkoşul

`migrations/0007_personel_davet.sql` uygulanmış olmalı. Fonksiyon o dosyadaki
`auth_kullanici_id` yardımcısını ve `service_role` yetkilerini kullanıyor;
uygulanmadan fonksiyon 500 döner.

---

## Kurulum

### 1. Supabase CLI kurun

```bash
npm install -g supabase
supabase --version
```

### 2. Projeye bağlanın

Proje referansını Supabase panelinde **Settings → General → Reference ID**
altında bulabilirsiniz.

```bash
supabase login
supabase link --project-ref <proje-referansi>
```

### 3. Fonksiyonu yayınlayın

Depo kökünden:

```bash
supabase functions deploy personel-davet
```

> **`--no-verify-jwt` KULLANMAYIN.** Varsayılan davranış doğru: Supabase, geçerli
> bir jeton taşımayan istekleri fonksiyona hiç ulaştırmıyor. Bu, fonksiyonun
> kendi yetki kontrolünün önündeki ilk kapı. Kapatılırsa fonksiyon yine de
> çalışır (kendi kontrolleri var) ama kimliği doğrulanmamış istekler doğrudan
> koda ulaşır ve tek bir hata bütün korumayı kaldırır.

### 4. Ortam değişkenleri

`SUPABASE_URL`, `SUPABASE_ANON_KEY` ve `SUPABASE_SERVICE_ROLE_KEY` Supabase
tarafından **kendiliğinden** sağlanıyor — elle tanımlamayın.

İsteğe bağlı tek değişken, tarayıcıdan çağıracak adreslerin listesi:

```bash
supabase secrets set IZINLI_KAYNAKLAR="https://nonstopstudio.tr,https://mckose.github.io"
```

Tanımlanmazsa yukarıdaki iki adres varsayılan olarak geçerli. Panel adresi
değişirse burayı güncelleyin, yoksa tarayıcı isteği CORS'ta durdurur.

---

## Kullanım

`POST https://<proje>.supabase.co/functions/v1/personel-davet`

Başlıklar:

```
Authorization: Bearer <giriş yapmış yöneticinin access_token'ı>
Content-Type: application/json
```

Gövde:

```json
{
  "gym_id": "salon kimliği (uuid)",
  "staff_id": "personel kaydının kimliği",
  "email": "personel@ornek.com",
  "role": "TRAINER"
}
```

`role`: `ADMIN`, `MANAGER` ya da `TRAINER`. Ne anlama geldikleri
[`../../README.md`](../../README.md) → "Kim neyi yazabilir".

### Yanıt

```json
{
  "durum": "hesap_acildi",
  "personel": "Ayşe Yılmaz",
  "eposta": "personel@ornek.com",
  "yetki": "TRAINER",
  "gecici_sifre": "Kf7mQx2pRt9Wnb"
}
```

**`gecici_sifre` yalnızca bir kez dönüyor.** Hiçbir yere kaydedilmiyor,
günlüğe yazılmıyor, ikinci kez alınamıyor. Personele siz iletiyorsunuz.

Kişinin hesabı zaten varsa `durum` `mevcut_hesap_baglandi` olur ve
`gecici_sifre` `null` gelir — mevcut şifresi değişmiyor.

---

## Neden e-posta gönderilmiyor

Davet bağlantısı göndermek SMTP kurulumu gerektiriyor. Supabase'in yerleşik
göndericisi üretim için ciddi biçimde sınırlı (saatte birkaç ileti) ve kendi
SMTP'nizi bağlamak ayrı bir kurulum işi. Birkaç kişilik bir salonda geçici
şifreyi yüz yüze vermek hem daha hızlı hem de çalışmayan bir e-posta
altyapısına bağlı değil.

**Bilinen eksik:** personelin kendi şifresini belirleyeceği ekran henüz yok,
yani geçici şifre kalıcı hâle geliyor. Bkz. `../../README.md` → "Henüz
yapılmadı".

---

## Güvenlik: neyi kim yapabiliyor

`service_role` anahtarı her şeyi yapabildiği için yetkiyi **fonksiyonun kendisi**
doğruluyor; veritabanı kuralları burada koruma sağlamıyor. İki kontrol var:

1. **Çağıran, hedef salonda `ADMIN` mi?** Başka bir salonda ADMIN olmak
   yetmiyor — sorgu hem kullanıcıyı hem salonu eşliyor.
2. **Personel kaydı gerçekten o salona mı ait?** Olmasaydı bir salonun
   yöneticisi, başka salonun personel satırına kendi seçtiği hesabı
   bağlayabilirdi.

Ayrıca:

- `gym_users` tablosuna **istemciden yazma yok** ve olmayacak. O tablo bütün
  erişim kurallarının dayanağı; ona yazabilen kişi kendi yetkisini de
  yükseltebilirdi.
- Fonksiyon `delete` yetkisi almadı: erişim kaldırma ayrı bir akış.
- `Access-Control-Allow-Origin` `*` değil, kapalı bir liste.

---

## Yarıda kalırsa

Aynı verilerle **tekrar çalıştırın**. Fonksiyon tekrar edilebilir:

- Hesap zaten açılmışsa bulunuyor, ikinci kez açılmaya çalışılmıyor
- Yetki satırı varsa üzerine yazılıyor (`merge-duplicates`)
- Personel bağı zaten aynı hesabaysa sorun çıkmıyor

Tekrar denemek zararsız. Bu bilinçli: yarıda kalan bir davet aksi hâlde kalıcı
olarak yarım kalır ve düzeltmek yine Supabase paneli gerektirirdi — yani
fonksiyonun kapatmak için var olduğu duruma geri dönerdi.

## Hata mesajları

| Yanıt | Sebep |
|---|---|
| `401` Oturum geçersiz | Jeton yok ya da süresi dolmuş — yeniden giriş yapın |
| `403` Salon yöneticisi olmalısınız | Çağıran o salonda `ADMIN` değil |
| `404` Personel kaydı bulunamadı | `staff_id` yanlış ya da başka salona ait |
| `409` Bu personel başka hesaba bağlı | Önce mevcut bağlantı kaldırılmalı |
| `409` Bu hesap başka personele bağlı | Bir hesap yalnızca bir personele bağlanabilir |
| `409` Tek bir hesaba eşlenemedi | Aynı e-postayı taşıyan birden çok hesap var; farklı e-posta kullanın |
| `500` Sunucu yapılandırması eksik | Ortam değişkenleri yok — fonksiyon doğru yayınlanmamış |
| `502` Hesap açılamadı | Auth API hata döndü; fonksiyon günlüğüne bakın |

Fonksiyon günlüğü: Supabase paneli → **Edge Functions → personel-davet → Logs**.
Geçici şifre günlüğe **yazılmıyor**.
