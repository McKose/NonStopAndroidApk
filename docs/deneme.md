# Nasıl denerim

Üç yol var, hızlıdan yavaşa. Küçük bir değişikliğe bakmak için birincisi yeter.

---

## 1. Paneli kurulumsuz denemek (30 saniye)

Panelin ekranlarına bakmak için Supabase ayarı, hesap ve internet gerekmiyor.
Adres sonuna `?demo` ekleyin:

```bash
cd web
python3 -m http.server 8000
```

Sonra **`http://localhost:8000/?demo`**

Giriş ekranında herhangi bir e-posta ve şifreyle "Giriş yap" deyin — demo modda
kontrol yok. İçeride örnek bir salonun verisi var: aktif, süresi dolmuş,
dondurulmuş ve arşivlenmiş üyeler; dört paket; randevular; defter kayıtları.

Demo verisi **gerçek sunucu biçiminin aynısı** (snake_case kolonlar, kuruş
tutarlar, epoch ms tarihler). Sapmaması testle bağlı: sapsaydı demoda düzgün
görünen bir ekran gerçek veride bozuk çıkabilirdi.

`?demo` olmadan açıldığında panel normal çalışır ve sunucuya bağlanır.

---

## Önce: sunucu şeması güncel mi (2. ve 3. yol için)

Birinci yol (`?demo`) sunucuya hiç gitmiyor, bu adımı atlayabilirsiniz. Diğer
ikisi gerçek sunucuya bağlanıyor ve şemanın güncel olması gerekiyor.

Supabase panelinde **SQL Editor**'de `supabase/migrations/` altındaki dosyaları
sırayla çalıştırın. Hepsi tekrar çalıştırılabilir — emin değilseniz yeniden
koşturun, zarar vermez. En son eklenen:

- `0004_role_based_access.sql` — kim neyi yazabilir kuralı

Bu dosya çalıştırılmazsa uygulama ve panel çalışmaya devam eder; yalnızca
yetki ayrımı olmaz (salona bağlı herkes her şeyi yazabilir).

**Kendi hesabınızın rolü `ADMIN` olmalı.** `MANAGER` ise personel ekleyemez,
`TRAINER` ise fiyat da değiştiremezsiniz. Kontrol ve düzeltme:

```sql
select u.email, gu.role
  from public.gym_users gu join auth.users u on u.id = gu.user_id;

update public.gym_users
   set role = 'ADMIN'
 where user_id = (select id from auth.users where email = '<e-postanız>');
```

`UPDATE 1` dönmeli. `UPDATE 0` dönerse e-posta tutmamıştır.

Güncellemede salon süzgeci yok: bugün tek salon olduğu için gereksiz. İleride
bir kullanıcı birden fazla salona bağlanırsa bu ifade **hepsindeki** rolünü
değiştirir; o zaman `and gym_id = '<salon kimliği>'` eklenmeli.

Ayrıntılı anlatım: `supabase/README.md` → "Kim neyi yazabilir".

---

## 2. Uygulamayı APK indirerek denemek (Android Studio gerekmez)

Her CI koşusu kurulabilir bir APK üretiyor.

1. GitHub'da depo → **Actions** sekmesi → en üstteki başarılı koşuyu açın.
2. Sayfanın altındaki **Artifacts** bölümünden `nonstop-debug-apk` dosyasını
   indirin (zip olarak iner, içinden `app-debug.apk` çıkar).
3. APK'yı telefona kopyalayıp açın. Android "bilinmeyen kaynak" uyarısı verirse
   izin verin — imzasız bir geliştirme sürümü.

### APK'nın sunucuya bağlanması için tek seferlik ayar

APK'nın içine gömülecek proje bilgisi depoda tutulmuyor. Depo gizli
anahtarlarına eklemeniz gerekiyor — **bir kez**:

1. Depo → **Settings** → **Secrets and variables** → **Actions**
2. **New repository secret** ile iki tane ekleyin:

   | İsim | Değer |
   |---|---|
   | `SUPABASE_URL` | `https://<proje-ref>.supabase.co` |
   | `SUPABASE_ANON_KEY` | `anon` `public` anahtarı |

`anon` anahtarı gizli bir şey değil (istemcide bulunması normaldir ve tek başına
hiçbir veriye erişemez); gizli anahtar olarak tutulmasının sebebi kuruluma özgü
olması. **`service_role` anahtarı buraya da konmaz.**

Bu iki değer yoksa APK yine üretiliyor, ama giriş ekranında "sunucu ayarları
eksik" diyor. Derlemeyi düşürmek, ayarları olmayan birinin hiçbir şeyi
denemesini engellerdi.

---

## 3. Kendi makinenizde derlemek (tam kontrol)

Kod değiştirip anında görmek için.

```bash
git pull origin master
```

Kök dizindeki `local.properties` dosyasına ekleyin:

```properties
supabase.url=https://<proje-ref>.supabase.co
supabase.anonKey=<anon public anahtarı>
```

Android Studio'da Gradle sync + Run. Değerler derleme sırasında gömülüyor;
dosyayı kaydetmek tek başına yetmez.

---

## Uçtan uca akışı görmek

Uygulama ile panelin aynı veriyi gösterdiğini doğrulamak:

1. Uygulamada giriş yapın, birkaç üye ekleyin.
2. Ayarlar → **Sunucuya Eşitle**. Aynı satırda bekleyen değişiklik sayısını ve
   son durumu görürsünüz.
3. Paneli `?demo` **olmadan** açıp aynı hesapla girin — eklediğiniz üyeler
   listede olmalı.

Eşitleme ayrıca girişte, oturum geri yüklendiğinde ve uygulama önplandayken
dakikada bir kendiliğinden çalışıyor.

---

## Bir şey ters giderse

Hata mesajları ne yapılması gerektiğini söyleyecek şekilde yazıldı.

| Gördüğünüz | Anlamı |
|---|---|
| "Sunucu ayarları eksik" | `local.properties` satırları yok (ya da APK'da depo gizli anahtarları) |
| "E-posta veya şifre hatalı" | Şifre yanlış ya da hesap onaylanmamış — parantez içi kısım hangisi olduğunu söyler |
| "Hesabınız bir salona bağlı değil" | Sunucuda `gym_users` satırı eksik |
| Ekleme/silme düğmeleri yok, yerine kilitli bir şerit var | Rolünüz o tabloya yazamıyor — beklenen davranış; rolü yükseltin ya da yetkili hesapla girin |
| Ayarlar'da gerekçe "Erişim reddedildi (403)" | Yetkisiz yazma sunucuda reddedildi. Uygulama artık bunu baştan engelliyor; yine de görüyorsanız kayıt `0004` uygulanmadan önce kuyruğa girmiş olabilir |
| Ayarlar'da "Bekleyen: N" düşmüyor | Gönderim takılmış; aynı satırdaki gerekçe sebebi söyler |
| Panelde "Kurulum tamamlanmamış" | `web/config.js` oluşturulmamış — ya da `?demo` ile açın |
| Panelde liste boş ama hata yok | O tabloda veri yok; erişim sorunu olsaydı hata görürdünüz |
