-- Supabase'in sağladığı parçaların yerel taklidi.
--
-- Şemayı Supabase'e yüklemeden sınayabilmek için gereken asgari yüzey:
-- `auth.users` tablosu ve `auth.uid()` fonksiyonu. Gerçek Supabase'de bunlar
-- hazır gelir; testte oturumdaki kullanıcıyı bir ayarla taklit ediyoruz.
--
-- Bu dosya **yalnızca test içindir**, Supabase projesine yüklenmez.

create schema if not exists auth;

create table if not exists auth.users (
    id    uuid primary key,
    email text
);

create or replace function auth.uid()
returns uuid
language sql
stable
as $$
    select current_setting('test.uid', true)::uuid
$$;
