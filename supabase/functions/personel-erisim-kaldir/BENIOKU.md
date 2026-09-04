# `personel-erisim-kaldir` — kurulum ve kullanım

`personel-davet`in tersi: bir personelin bu salona erişimini kaldırır. İşten
ayrılan biri için yapılacak şey bu.

Neyin silinip neyin silinmediği [`../../README.md`](../../README.md) içindeki
"Erişimi geri alma" bölümünde.

---

## Durum: yayında

Fonksiyon canlı projede **yayınlanmış** durumda — `ACTIVE`, `verify_jwt` açık.
Aşağıdaki adımları yeniden yapmanız gerekmiyor; bölüm, kod değiştiğinde yeniden
yayınlamak ve sıfırdan kurulan bir projeyi ayağa kaldırmak için duruyor.

## Önkoşul

Migrasyon `0008` uygulanmış olmalı. Sebebi somut: `gym_users` üzerinde `delete`
yetkisi bu dosyada **açıkça** `service_role`e veriliyor. Supabase varsayılan
olarak da veriyor, dolayısıyla uygulanmadan da çalışabilir — ama o yetkinin
varlığı bir platform varsayılanına kalmış olur, yazılı olmaz.

## Yayınlama

Depo kökünden:

```bash
supabase functions deploy personel-erisim-kaldir
```

> **`--no-verify-jwt` KULLANMAYIN.** `personel-davet`teki gerekçenin aynısı
> geçerli: Supabase, geçerli bir jeton taşımayan istekleri fonksiyona hiç
> ulaştırmıyor ve bu, fonksiyonun kendi yetki kontrolünün önündeki ilk kapı.

## Ortam değişkenleri

`SUPABASE_URL`, `SUPABASE_ANON_KEY` ve `SUPABASE_SERVICE_ROLE_KEY` Supabase
tarafından **kendiliğinden** sağlanıyor — elle tanımlamayın.

`IZINLI_KAYNAKLAR` `personel-davet` ile **ortak**: ikisi aynı değişkeni okuyor
ve aynı varsayılana sahip. Ayrı tutulsaydı panelin bir düğmesi çalışır, diğeri
CORS'ta sessizce dururdu.

---

## Kullanım

`POST https://<proje>.supabase.co/functions/v1/personel-erisim-kaldir`

Başlıklar: `Authorization: Bearer <çağıranın jetonu>`, `apikey: <anon anahtarı>`

```json
{
  "gym_id": "<salonun uuid'si>",
  "staff_id": "<personel kaydının kimliği>"
}
```

### Yanıtlar

| Kod | Durum | Anlamı |
|---|---|---|
| `200` | `kaldirildi` | Yetki silindi, hesap bağı boşaltıldı |
| `200` | `zaten_yok` | Personelin zaten bağlı bir hesabı yoktu — hata değil |
| `400` | | Salon kimliği ya da personel kaydı geçersiz |
| `401` | | Jeton yok ya da geçersiz |
| `403` | | Çağıran bu salonda ADMIN değil |
| `404` | | Personel kaydı bu salonda yok |
| `409` | | Çağıran **kendi** erişimini kaldırmaya çalıştı |
| `502` | | Yetki silindi ama hesap bağı temizlenemedi — **tekrar çalıştırın** |

`502` en dikkat edilmesi gereken hâl ve mesajı da bunu söylüyor: erişim
gitmiştir (istenen sonuç olmuştur), geriye yalnızca `staff` satırındaki artık
bağ kalmıştır. Panelde "Hesabı var, yetkisi yok" olarak görünür ve aynı isteği
tekrarlamak düzeltir.

## Tekrar çalıştırılabilir

Yarıda kalan bir kaldırma aynı verilerle tekrar denendiğinde tamamlanıyor:
olmayan bir `gym_users` satırını silmek hata değil, `staff.auth_user_id`yi
ikinci kez boşaltmak da öyle.
