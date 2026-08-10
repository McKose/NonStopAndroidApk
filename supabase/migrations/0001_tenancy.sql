-- ---------------------------------------------------------------------------
-- Salon (kiracı) modeli ve kimlik eşlemesi
-- ---------------------------------------------------------------------------
-- Bugün tek salon var, ileride artabilir. Şema baştan çok salonlu: bugün
-- `gyms` tablosunda tek satır olur, yarın on satır — hiçbir şey değişmez.
-- Uygulamadaki her satırda zaten `tenantId` taşınıyor, burada karşılığı
-- `tenant_id` ve doğrudan `gyms.id`'yi gösteriyor.
--
-- Kimlik doğrulama Supabase Auth'a ait: her personelin kendi hesabı var.
-- Uygulamadaki yerel `staff.password` alanı sunucuya **taşınmıyor** (bkz.
-- 0002_data_tables.sql) — düz metin şifreleri sunucuya kopyalamak, çözmeye
-- çalıştığımız sorunu büyütmek olurdu.
-- ---------------------------------------------------------------------------

create table if not exists public.gyms (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    created_at  timestamptz not null default now()
);

comment on table public.gyms is
    'Salonlar. Uygulamadaki tenantId bu tablonun id değerini taşır.';

-- Hangi kullanıcı hangi salona bağlı.
--
-- Rol alanı bugün yalnızca bilgi amaçlı: erişim kuralları şu an "salona bağlı
-- olan yazabilir" diyor. Role dayalı ince yetkilendirme (ör. yalnızca ADMIN
-- personel silebilir) sonradan bu alan üzerine kurulacak; alanı baştan koymak
-- o adımda veri taşımayı gereksiz kılıyor.
create table if not exists public.gym_users (
    user_id     uuid not null references auth.users (id) on delete cascade,
    gym_id      uuid not null references public.gyms (id) on delete cascade,
    role        text not null default 'TRAINER'
                check (role in ('ADMIN', 'MANAGER', 'TRAINER')),
    created_at  timestamptz not null default now(),
    primary key (user_id, gym_id)
);

create index if not exists gym_users_gym_id_idx on public.gym_users (gym_id);

-- ---------------------------------------------------------------------------
-- Erişim kurallarının dayandığı yardımcı
-- ---------------------------------------------------------------------------
-- Giriş yapmış kullanıcının bağlı olduğu salon kimliklerini döndürür.
--
-- `security definer` şart: veri tablolarındaki kurallar bu fonksiyonu çağırıyor,
-- fonksiyon da `gym_users`'ı okuyor. `gym_users` üzerinde de satır bazlı güvenlik
-- açık olduğu için normal (invoker) bir fonksiyon kendi kuralını tetikler ve
-- sonsuz özyinelemeye girer. `security definer` ile fonksiyon tablo sahibinin
-- yetkisiyle koşar, özyineleme oluşmaz.
--
-- `set search_path = public` da şart: `security definer` fonksiyonlarda arama
-- yolu sabitlenmezse çağıran taraf kendi şemasını öne alıp fonksiyonun
-- çağırdığı nesneleri değiştirebilir.
create or replace function public.user_gym_ids()
returns setof uuid
language sql
stable
security definer
set search_path = public
as $$
    select gym_id from public.gym_users where user_id = auth.uid()
$$;

comment on function public.user_gym_ids() is
    'Giriş yapmış kullanıcının salonları. Tüm erişim kuralları buna dayanır.';

-- ---------------------------------------------------------------------------
-- gyms / gym_users üzerindeki erişim kuralları
-- ---------------------------------------------------------------------------
alter table public.gyms      enable row level security;
alter table public.gym_users enable row level security;

-- Kullanıcı yalnızca bağlı olduğu salonu görür.
create policy gyms_select on public.gyms
    for select
    using (id in (select public.user_gym_ids()));

-- Salon oluşturma ve silme uygulamadan yapılmıyor; yönetim panelinden ya da
-- doğrudan veritabanından yapılır. Bu yüzden bilinçli olarak yazma kuralı yok:
-- kural yoksa satır bazlı güvenlik altında yazma tamamen reddedilir.

-- Kullanıcı yalnızca kendi bağlılıklarını görür. Kimin hangi salonda olduğunu
-- listelemek yetki gerektirir; bu da panel tarafına ait.
create policy gym_users_select on public.gym_users
    for select
    using (user_id = auth.uid());
