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

   `tests/` altındaki dosyalar **yüklenmez**; onlar yalnızca CI içindir.

3. **Salonu oluşturun.**
   ```sql
   insert into public.gyms (name) values ('Salon adınız') returning id;
   ```
   Dönen `id` değerini not edin — uygulamadaki `tenantId` bu olacak.

4. **Personel hesaplarını açın.** Authentication → Users → Add user. Her personel
   için bir e-posta ve şifre.

5. **Kullanıcıları salona bağlayın.** Her kullanıcı için:
   ```sql
   insert into public.gym_users (user_id, gym_id, role)
   values ('<auth.users.id>', '<gyms.id>', 'TRAINER');  -- ya da MANAGER / ADMIN
   ```
   Bu adım atlanırsa kullanıcı giriş yapar ama **hiçbir satır göremez** — kural
   "bağlı olduğun salonun satırları" dediği için bağlantısı olmayan kullanıcının
   sonucu boş olur. Beklenen davranış budur.

6. **Uygulama anahtarlarını alın.** Project Settings → API: `Project URL` ve
   `anon public` anahtarı. Uygulamanın istemci tarafı bunları kullanacak.
   `service_role` anahtarı uygulamaya **konulmaz** — o anahtar tüm kuralları
   baypas eder.

## Doğrulama

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

## Henüz yapılmadı

- **Uygulama tarafı bağlantı.** `RemoteDataSource`'un Supabase uygulaması ve
  oturum yönetimi yazılmadı; senkronizasyon motoru hazır ve bekliyor.
- **`tenantId` hâlâ sabit.** Uygulamada `"default"` değeri gömülü; oturumdaki
  kullanıcının salonundan gelmesi gerekiyor.
- **Role dayalı ince yetkilendirme.** `gym_users.role` alanı duruyor ama kurallar
  şu an rolü ayırt etmiyor: salona bağlı olan yazabiliyor.
