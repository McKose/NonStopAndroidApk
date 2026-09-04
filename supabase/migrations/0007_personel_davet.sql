-- ---------------------------------------------------------------------------
-- Personel daveti: uygulamadan hesap açabilmek
-- ---------------------------------------------------------------------------
-- Bugüne kadarki boşluk: yeni bir personele uygulama erişimi vermek için
-- birinin Supabase paneline girip (1) Auth hesabı açması, (2) `gym_users`
-- satırını elle yazması, (3) `staff.auth_user_id`yi elle doldurması
-- gerekiyordu. Üç adımın ikisi atlanabiliyor ve atlandığında belirti şu
-- oluyor: kişi giriş yapıyor, uygulama açılıyor, ama HİÇBİR VERİ gelmiyor —
-- çünkü `gym_users` satırı yoksa bütün erişim kuralları boş küme döndürüyor.
-- Sebebi ekranda yazmıyor.
--
-- ### Neden `gym_users`a yazma kuralı EKLENMİYOR
--
-- En kısa yol "ADMIN kendi salonuna `gym_users` satırı yazabilsin" demek
-- olurdu. Reddedildi: o tablo bütün erişim kurallarının dayanağı
-- (`user_gym_ids()`), yani ona yazabilen kişi kendi yetkisini de yükseltebilir.
-- İstemciden gelen bir isteğin bu tabloyu değiştirebilmesi, yetki modelinin
-- kendisini istemciye açmak demek.
--
-- Bunun yerine yazma işini sunucu tarafındaki bir Edge Function yapıyor
-- (`supabase/functions/personel-davet/`). O fonksiyon `service_role`
-- anahtarıyla koşuyor, anahtar hiçbir zaman istemciye gitmiyor ve yetkiyi
-- kendisi doğruluyor: çağıranın JWT'sinden kimliğini çıkarıp o kişinin HEDEF
-- SALONDA ADMIN olduğunu kontrol ediyor.
--
-- Bu dosyanın getirdiği iki şey, o fonksiyonun ve panelin ihtiyaç duyduğu
-- asgari yüzey.
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- 1. E-postadan hesap kimliği
-- ---------------------------------------------------------------------------
-- Davet akışının **tekrar edilebilir** olması için gerekiyor.
--
-- Senaryo: davet yarıda kalıyor (ağ hatası, sekme kapanıyor). Auth hesabı
-- açılmış ama `gym_users` satırı yazılmamış. Yönetici tekrar deniyor — ve
-- hesap zaten var olduğu için "bu e-posta kayıtlı" hatası alıyor. Kimliği
-- bulmanın bir yolu olmazsa o personel KALICI olarak yarım kalıyor ve
-- düzeltmek yine Supabase paneli gerektiriyor. Yani tam da kapatmaya
-- çalıştığımız duruma geri dönüyoruz.
--
-- ### Neden `security definer`
-- `auth.users` istemciye kapalı ve kapalı kalmalı. Fonksiyon tablo sahibinin
-- yetkisiyle koşup yalnızca TEK bir alanı (kimlik) döndürüyor.
--
-- ### Yetki kontrolü fonksiyonun İÇİNDE
-- `security definer` bir fonksiyon `public` şemasındaysa varsayılan olarak
-- herkese açık bir uç nokta olur. Bu yüzden gövdede `auth.uid()` kontrolü var:
-- çağıran, herhangi bir salonda ADMIN değilse fonksiyon `null` döndürüyor.
-- Giriş yapmamış çağıranda `auth.uid()` zaten `null`, dolayısıyla kontrol
-- düşüyor.
--
-- ### Bilerek kabul edilen açıklama
-- Bir ADMIN, verdiği e-postanın sistemde hesabı olup olmadığını öğrenebiliyor.
-- Bu bir e-posta doğrulama (enumeration) yüzeyi ve kaçınılmaz: davetin çalışması
-- için o sorunun cevabı gerekiyor. Dönen değer yalnızca bir kimlik — tek başına
-- hiçbir veriye erişim vermiyor, çünkü erişim `gym_users` üzerinden kurulu.
-- ### Birden çok eşleşmede `null` — `limit 1` DEĞİL
-- İlk yazımı `limit 1` idi ve sessizce yanlıştı: aynı e-postayı taşıyan iki
-- hesap varsa hangisinin döneceği belirsiz olur, dönen kimlik `gym_users`a
-- yazılır ve salona YANLIŞ KİŞİ alınır. Belirsizliği "birini seç" diye
-- çözmek, sonucu kimsenin fark edemeyeceği bir yetki hatasına dönüştürüyor.
-- Belirsizlikte hiçbir şey döndürmüyoruz; davet ekranı da "bu e-posta ile
-- devam edilemiyor" diyerek insana bırakıyor.
create or replace function public.auth_kullanici_id(p_email text)
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
    with eslesen as (
        select u.id
          from auth.users u
         where lower(u.email) = lower(trim(p_email))
         limit 2                      -- ikiden fazlasını saymaya gerek yok
    )
    select e.id
      from eslesen e
     where (select count(*) from eslesen) = 1
       and exists (
             select 1
               from public.gym_users g
              where g.user_id = auth.uid()
                and g.role = 'ADMIN'
           )
$$;

comment on function public.auth_kullanici_id(text) is
    'Verilen e-postanın Auth hesabı kimliği. Yalnızca bir salonda ADMIN olan çağırana yanıt verir; davetin tekrar edilebilmesi için var.';

-- Varsayılan olarak her yeni fonksiyonun `execute` yetkisi `PUBLIC`'e verilir.
-- `security definer` bir fonksiyonda bu, istemeden herkese açık bir uç nokta
-- bırakmak demek. Önce geri alınıyor, sonra yalnızca giriş yapmışlara veriliyor.
revoke all on function public.auth_kullanici_id(text) from public;
grant execute on function public.auth_kullanici_id(text) to authenticated;


-- ---------------------------------------------------------------------------
-- 2. Yönetici kendi salonundaki erişimleri görebiliyor
-- ---------------------------------------------------------------------------
-- 0001'deki tek kural `user_id = auth.uid()` diyor: herkes yalnızca KENDİ
-- bağlılığını görüyor. Bu, davet ekranını imkânsız kılıyor — yönetici "bu
-- personelin hesabı var mı, hangi yetkiyle" sorusunu soramıyor ve aynı kişiyi
-- ikinci kez davet edip etmediğini bilemiyor.
--
-- Yeni kural yalnızca ADMIN'e ve yalnızca KENDİ salonunun satırlarına açıyor.
-- MANAGER ve TRAINER'a açılmadı: kimin hangi yetkiyle girdiği bir yönetim
-- bilgisi, günlük iş için gerekmiyor.
--
-- Eski kural DURUYOR ve durması şart: kaldırılsaydı ADMIN olmayan hiç kimse
-- kendi satırını okuyamaz, oturum açılışında rolünü çözemez ve uygulama
-- herkesi yetkisiz sanardı.
drop policy if exists gym_users_admin_select on public.gym_users;

create policy gym_users_admin_select on public.gym_users
    for select
    to authenticated
    using (gym_id in (select public.user_gym_ids_with_role(array['ADMIN'])));


-- ---------------------------------------------------------------------------
-- 3. Edge Function'ın yazma yetkisi
-- ---------------------------------------------------------------------------
-- `service_role` satır bazlı güvenliği baypas ediyor ama TABLO yetkisi ayrı bir
-- şey ve varsayılanlara güvenmek istemiyoruz: yetki eksikse hata Edge Function
-- içinde "permission denied for table gym_users" diye çıkar ve davet
-- ekranında sebebi anlaşılmaz bir hata olarak görünür.
--
-- Açıkça verilenler davetin ihtiyacı kadar. `delete` LİSTEDE YOK ama bu bir
-- KISITLAMA DEĞİL, yalnızca bir niyet beyanı: Supabase yeni bir tabloyu
-- `anon`, `authenticated` ve `service_role` rollerine bütün ayrıcalıklarla
-- (`delete` ve `truncate` dâhil) açıyor ve `grant` yalnızca ekleme yapıyor.
-- Yani bu satırlar çalıştıktan sonra da `service_role` bu tabloları
-- silebiliyor. Doğrulandı: `information_schema.role_table_grants`.
--
-- Gerçek koruma tablo yetkisi değil, RLS: `gym_users` üzerinde RLS açık ve
-- HİÇBİR yazma kuralı yok (yukarıdaki tek kural `for select`). İstemci —
-- `anon` da `authenticated` de — bu tabloya yazamıyor. `service_role` RLS'i
-- baypas ediyor, dolayısıyla ona karşı tek koruma Edge Function'ın kendi
-- yetki kontrolü; anahtar da yalnızca sunucuda duruyor.
--
-- `revoke delete` YAZILMADI: bir kişinin erişimini kaldırma akışı hâlâ eksik
-- (bkz. supabase/README.md) ve yazıldığında `gym_users`tan satır silmesi
-- gerekecek. Bugün yetkiyi geri almak, yarın onu geri vermekten başka bir işe
-- yaramazdı.
grant select, insert, update on public.gym_users to service_role;
grant select, update on public.staff to service_role;
