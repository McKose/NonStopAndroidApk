-- Supabase'in sağladığı parçaların yerel taklidi.
--
-- Şemayı Supabase'e yüklemeden sınayabilmek için gereken asgari yüzey:
-- `auth.users` tablosu ve `auth.uid()` fonksiyonu. Gerçek Supabase'de bunlar
-- hazır gelir; testte oturumdaki kullanıcıyı bir ayarla taklit ediyoruz.
--
-- Bu dosya **yalnızca test içindir**, Supabase projesine yüklenmez.

-- Supabase bu rolleri hazır sağlar; migrasyonlar da onların var olduğunu
-- varsayıyor. Yerel testte elle kuruluyorlar.
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
    -- `service_role`: Edge Function'ların koştuğu rol. Gerçek Supabase'de
    -- satır bazlı güvenliği baypas eder; burada yalnızca VAR OLMASI yetiyor,
    -- çünkü migrasyonlar ona tablo yetkisi veriyor ve rol yoksa migrasyon
    -- "role does not exist" ile düşer.
    if not exists (select 1 from pg_roles where rolname = 'service_role') then
        create role service_role nologin bypassrls;
    end if;
end
$$;

create schema if not exists auth;

create table if not exists auth.users (
    id    uuid primary key,
    email text
);

-- `nullif(..., '')`: oturum yokken gerçek `auth.uid()` NULL döndürüyor, stub da
-- öyle davranmalı. Testler "giriş yapılmamış" durumu `set test.uid = ''` ile
-- anlatıyor ve boş dize doğrudan uuid'ye çevrilemiyor — çevrilmeye
-- çalışıldığında test, sınamak istediği şeyi sınayamadan `invalid input syntax`
-- ile düşüyor. Erişim kurallarında bu fark görünmüyordu çünkü anonim yolda
-- çağrılan kurallar `auth.uid()`ye hiç bakmıyor; `auth.uid()`yi doğrudan
-- çağıran ilk test (50) bunu ortaya çıkardı.
create or replace function auth.uid()
returns uuid
language sql
stable
as $$
    select nullif(current_setting('test.uid', true), '')::uuid
$$;
