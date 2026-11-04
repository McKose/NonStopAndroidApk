-- ---------------------------------------------------------------------------
-- `security definer` fonksiyonlarında `anon` yetkisi kapatılıyor
-- ---------------------------------------------------------------------------
-- Supabase proje danışmanı dört fonksiyonu işaretliyor: `anon` rolü bunları
-- `/rest/v1/rpc/...` üzerinden çağırabiliyor ve dördü de `security definer`,
-- yani tanımlayanın (tablo sahibinin) yetkileriyle koşuyor.
--
--     public.auth_kullanici_id(text)
--     public.user_gym_ids()
--     public.user_gym_ids_with_role(text[])
--     public.user_member_ids()
--
-- VERİ SIZMIYOR ve bu önemli, çünkü aksi hâlde bu dosya acil bir yama olurdu.
-- Dördünün de gövdesi `auth.uid()`ye bakıyor; `anon` için o NULL olduğundan
-- eşleşme olmuyor ve sonuç boş dönüyor. `auth_kullanici_id` ayrıca çağıranın
-- bir salonda ADMIN olmasını şart koşuyor. Yani KORUMA KATMANI SAĞLAM;
-- kapatılan şey YETKİ KATMANI, ve ikisinin birbiriyle tutarlı olması için.
--
-- NEDEN YETKİ ORADA DURUYORDU — 0008'DEKİ DERSİN AYNISI. `0007` niyetini
-- açıkça yazmıştı:
--
--     revoke all on function public.auth_kullanici_id(text) from public;
--     grant execute on function public.auth_kullanici_id(text) to authenticated;
--
-- Ama `revoke ... from public` PSEUDO-ROLÜ hedefliyor; Supabase ayrıca
-- `alter default privileges` ile her yeni fonksiyonun `execute` yetkisini
-- DOĞRUDAN `anon`, `authenticated` ve `service_role` rollerine veriyor. Role'e
-- özel o grant, `public`ten yapılan revoke'tan etkilenmiyor. Sonuç: niyet
-- belgelenmiş, canlıda tutmamış. `0004`/`0008`de tablolarda yaşanan şeyin
-- fonksiyonlardaki hâli.
--
-- Bu yüzden aşağıda İKİ YOLDAN DA alınıyor (`from public, anon`) ve gereken
-- rollere AÇIKÇA geri veriliyor. Tek yoldan almak işe yaramıyor: ölçüldü —
-- yalnız `anon`dan alındığında `anon` yetkiyi `public` üzerinden korumaya
-- devam ediyor ve hiçbir şey değişmiyor.
--
-- `authenticated` grant'leri BUGÜN GEREKSİZ ve bilerek yazılıyor. Dördünün de
-- `authenticated` yetkisi daha önceki migrasyonlardan geliyor (`0002`, `0004`,
-- `0005`, `0007`), yani bu satırlar olmadan da çalışırdı — denendi: yalnız
-- buradaki grant kaldırıldığında hiçbir test düşmüyor.
--
-- Yine de duruyorlar, iki sebeple. Birincisi: aynı fonksiyonun yetkisini
-- alıp vermek tek dosyada görünmeli; okuyan kişi `authenticated`in erişimini
-- sürdürdüğünü anlamak için dört ayrı migrasyonu izlemek zorunda kalmamalı.
-- İkincisi desen: eklenecek YENİ bir `security definer` fonksiyonda revoke
-- tek başına yazılırsa `authenticated` de kesilir. O hâlin gerçekten kırdığı
-- ölçüldü — `user_member_ids`in grant'i hem `0005`ten hem buradan
-- kaldırıldığında erişim kuralı testleri `permission denied for function`
-- ile düşüyor. Revoke ile grant'i yan yana tutmak o tuzağı kapatıyor.
--
-- `service_role` grant'i ise GEREKSİZ DEĞİL: ona ilk veren yer bu dosya.
-- Canlıda yetkisi Supabase'in varsayılanıyla zaten vardı, ama boş bir
-- PostgreSQL'de `0005` ve `0007` `public`ten aldığı için iki fonksiyonda hiç
-- yoktu. Yani bu satırlar boş veritabanını canlıyla aynı hâle getiriyor.
--
-- `service_role` Edge Function'ların rolü; satır bazlı güvenliği baypas ettiği
-- için kurallar onda değerlendirilmiyor, ama fonksiyonu doğrudan çağırabilmesi
-- gerekiyor ve yetkisi bugün de var. Açıkça korunuyor.
--
-- DANIŞMAN BU DOSYADAN SONRA DA BİR UYARI VERMEYE DEVAM EDİYOR ve o uyarı
-- KAPATILMAMALI: "Signed-In Users Can Execute SECURITY DEFINER Function"
-- (`authenticated_security_definer_function_executable`). Doğru tespit ama bu
-- şemada kaçınılmaz — bu üç fonksiyon ~40 erişim kuralının İÇİNDE çağrılıyor
-- ve Postgres'te kuralda geçen fonksiyon için sorgulayan rolün `execute`
-- yetkisi gerekiyor. `authenticated`ten alınsa uygulamanın ve panelin bütün
-- okumaları `permission denied for function` ile düşer; ölçüldü (grant hem
-- `0005`ten hem buradan kaldırıldığında erişim kuralı testleri böyle düşüyor).
--
-- Danışmanın asıl işaret ettiği risk olan "giriş yapmış biri bunları RPC ile
-- çağırabilir" durumu ise zararsız: üçü de `auth.uid()`ye bakıyor, yani
-- çağıran yalnızca KENDİ salonlarını/üyeliklerini görüyor — hâlihazırda
-- erişebildiği bilgi. `auth_kullanici_id` ayrıca ADMIN şartı koyuyor.
--
-- Yani danışmanda beklenen son durum: `anon` satırı YOK, `authenticated`
-- satırı VAR. İkincisini görüp "eksik iş" sanmamak için burada yazılı.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 1. `gyms_select` yalnız giriş yapmışlara
-- ---------------------------------------------------------------------------
-- Bu adım 2. adımın ÖNKOŞULU, kozmetik değil.
--
-- `gyms_select` şemadaki TEK `{public}` kapsamlı kural ve ifadesi
-- `user_gym_ids()` çağırıyor. Postgres'te bir kuralda geçen fonksiyon için
-- SORGULAYAN ROLÜN `execute` yetkisi gerekiyor. Yani kural `{public}` kalırken
-- `anon`dan yetki alınsa, `anon`un `gyms` sorgusu BOŞ SONUÇ YERİNE HATA
-- döndürürdü: `permission denied for function user_gym_ids`. Ölçüldü, aynen
-- böyle oldu.
--
-- `{public}` olması bilinçli bir tercih değil: `0001` kuralı `to` yazmadan
-- oluşturuyor ve Postgres'in varsayılanı PUBLIC. Şemadaki diğer bütün kurallar
-- `{authenticated}`; bu tek istisnaydı.
--
-- Davranış değişmiyor: `anon` için `auth.uid()` NULL, `user_gym_ids()` boş
-- dönüyor ve kural zaten 0 satır veriyordu. Kapsam daraltıldıktan sonra `anon`
-- için uygulanabilir bir `select` kuralı kalmadığından sonuç yine 0 satır —
-- ölçüldü: önce de sonra da 0, hata yok. Giriş yapmış ADMIN ise salonunu
-- görmeye devam ediyor (ölçüldü: 1 satır).
--
-- `0001`deki desenin aynısı: `create policy`'nin `if not exists` biçimi yok ve
-- bu dosya iki kez uygulanabilir olmak zorunda (test düzeni migrasyonları iki
-- kez koşuyor).
drop policy if exists gyms_select on public.gyms;
create policy gyms_select on public.gyms
    for select
    to authenticated
    using (id in (select public.user_gym_ids()));

-- ---------------------------------------------------------------------------
-- 2. Fonksiyon yetkileri
-- ---------------------------------------------------------------------------
-- `revoke`/`grant` idempotent; dosya iki kez uygulanabilir.
revoke all on function public.user_gym_ids()                 from public, anon;
grant execute on function public.user_gym_ids()              to authenticated, service_role;

revoke all on function public.user_gym_ids_with_role(text[]) from public, anon;
grant execute on function public.user_gym_ids_with_role(text[]) to authenticated, service_role;

revoke all on function public.user_member_ids()              from public, anon;
grant execute on function public.user_member_ids()           to authenticated, service_role;

-- `auth_kullanici_id` hiçbir erişim kuralında geçmiyor (doğrulandı: kuralları
-- tarayan sorgu boş döndü). Yalnızca davet ekranından RPC ile çağrılıyor, yani
-- `anon`dan alınması hiçbir kuralı etkilemiyor. Dördü içinde PARAMETRE ALAN
-- tek fonksiyon olduğu için kapatılması en çok bunun için anlamlı.
revoke all on function public.auth_kullanici_id(text)        from public, anon;
grant execute on function public.auth_kullanici_id(text)     to authenticated, service_role;
