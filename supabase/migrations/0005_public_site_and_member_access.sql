-- ---------------------------------------------------------------------------
-- Herkese açık site ve üye erişimi
-- ---------------------------------------------------------------------------
-- Üç yeni şey getiriyor:
--
--   1. `announcements` — açılış sayfasındaki etkinlik/reklam vitrini. Giriş
--      YAPILMADAN okunuyor: `anon` rolüne okuma açılan ilk tablo.
--   2. `member_accounts` — üye kaydı ile Supabase hesabı arasındaki bağ.
--      Üyeler bugüne kadar giriş yapamıyordu; bağ bu tabloyla kuruluyor.
--   3. `member_health_updates` — üyenin kendi beyan ettiği sağlık bilgisi.
--
-- ### Üye erişimi neden `gym_users`'a EKLENMİYOR
-- En kısa yol üyeye bir `gym_users` satırı verip rolüne 'MEMBER' yazmak olurdu.
-- Bu, felaketle sonuçlanırdı: bütün veri tablolarındaki okuma kuralları
-- `tenant_id in (select public.user_gym_ids())` diyor ve `user_gym_ids()`
-- `gym_users`'ı okuyor. Yani o satır eklendiği an üye **salonun tamamını**
-- görürdü: her üyenin sağlık verisi, tüm kasa hareketleri, personel maaşları.
--
-- Bu yüzden üye erişimi ayrı bir yol: `member_accounts` → `user_member_ids()` →
-- yalnızca kendi satırına kilitli kurallar. İki mekanizma birbirine hiç
-- değmiyor.
--
-- ### Üyenin sağlık beyanı neden AYRI tabloda
-- `gym_members` üzerinde `health_risks`, `risk_level`, `health_notes` alanları
-- zaten var ve personel dolduruyor. Üye onları doğrudan yazsaydı iki yönlü
-- veri kaybı olurdu:
--
--   * Uygulama çevrimdışı-öncelikli ve üye satırını BÜTÜN olarak gönderiyor
--     (`RowPayloads.of(MemberEntity)` bu üç alanı da yükte taşıyor). Eğitmenin
--     telefonundaki eski kopya senkronize olduğunda üyenin web'de yazdığını
--     üzerine yazardı — birleştirme yok, son yazan kazanır.
--   * Tersi de geçerli: üye eğitmenin klinik notunu ezebilirdi.
--
-- Ayrı tablo ikisini de kapatıyor: uygulama bu tabloyu hiç bilmiyor, hiç
-- yazmıyor.
--
-- Tablo **yalnızca eklenen** (append-only), defter gibi: güncelleme ve silme
-- kimseye açık değil. Beyan bir geçmiş — üye dün "sırt ağrım var" deyip bugün
-- silememeli, düzeltme yeni satırla yapılır. Eğitmen de neyin ne zaman beyan
-- edildiğini görebilmeli.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 1. Duyurular / etkinlikler
-- ---------------------------------------------------------------------------
create table if not exists public.announcements (
    id            text primary key,
    tenant_id     uuid not null references public.gyms (id) on delete cascade,
    title         text not null,
    body          text not null,
    -- Görsel adresi; dosyanın kendisi burada tutulmuyor.
    image_url     text,
    kind          text not null check (kind in ('EVENT', 'AD', 'NOTICE')),
    -- Yayın penceresi. İkisi de `null` olabilir: süresiz duyuru.
    starts_at_ms  bigint,
    ends_at_ms    bigint,
    -- Varsayılan `false`: yanlışlıkla oluşturulan bir satır kendiliğinden
    -- herkese açık olmasın. Yayınlamak açık bir eylem.
    is_published  boolean not null default false,
    sort_order    integer not null default 0,
    created_at_ms bigint not null,
    updated_at_ms bigint not null,
    deleted_at_ms bigint
);

create index if not exists announcements_tenant_idx
    on public.announcements (tenant_id, sort_order);

-- ---------------------------------------------------------------------------
-- 2. Üye ↔ hesap bağı
-- ---------------------------------------------------------------------------
-- Bağ **personel tarafından** kuruluyor. E-posta eşleşmesiyle kendiliğinden
-- kurulmuyor ve sebebi somut: `gym_members.email` alanı hem `null` olabiliyor
-- hem de tekillik kısıtı YOK (tabloda yalnızca `unique (tenant_id, phone)`
-- var). İki üye aynı e-postayı paylaşabilir, bir yazım hatası başka birinin
-- sağlık verisini açabilirdi. Sağlık verisinde bu risk alınamaz.
--
-- İki yönlü tekillik bilinçli:
--   * Birincil anahtar → bir üyenin en fazla bir hesabı olur.
--   * `member_accounts_user_unique` → bir hesap en fazla bir üyeye bağlanır.
-- İkincisi olmasa bir hesap birden fazla üyeye bağlanıp hepsinin verisini
-- görebilirdi.
create table if not exists public.member_accounts (
    member_id    text not null references public.gym_members (id) on delete cascade,
    tenant_id    uuid not null references public.gyms (id) on delete cascade,
    auth_user_id uuid not null references auth.users (id) on delete cascade,
    -- Bağı kim kurdu: erişim kararının izi. Hesap silinirse bağ kalsın diye
    -- `on delete set null` değil, kasıtlı olarak kısıt yok.
    linked_by    uuid,
    linked_at_ms bigint not null,
    primary key (member_id, tenant_id)
);

create unique index if not exists member_accounts_user_unique
    on public.member_accounts (tenant_id, auth_user_id);

-- ---------------------------------------------------------------------------
-- 3. Üyenin sağlık beyanı (yalnızca eklenen)
-- ---------------------------------------------------------------------------
create table if not exists public.member_health_updates (
    id             text primary key,
    tenant_id      uuid not null references public.gyms (id) on delete cascade,
    member_id      text not null references public.gym_members (id) on delete cascade,
    reported_at_ms bigint not null,
    -- Yapılandırılmış alanlar + serbest not. Tek bir metin kutusu olsaydı
    -- eğitmen ilaç bilgisini serbest metnin içinde aramak zorunda kalırdı.
    conditions     text,
    medications    text,
    injuries       text,
    note           text,
    -- KVKK: sağlık verisi özel nitelikli kişisel veri ve açık rıza gerektiriyor.
    -- Rıza zamanı satırın kendisinde duruyor — ayrı bir yerde tutulsaydı hangi
    -- beyanın hangi rızayla verildiği zamanla belirsizleşirdi. `not null`:
    -- rızasız beyan kaydedilemiyor.
    consent_at_ms  bigint not null,
    created_at_ms  bigint not null
);

create index if not exists member_health_updates_member_idx
    on public.member_health_updates (tenant_id, member_id, reported_at_ms);

-- ---------------------------------------------------------------------------
-- Üyenin kendi kayıtlarını bulan yardımcı
-- ---------------------------------------------------------------------------
-- `security definer` şart ve sebebi `user_gym_ids()` ile aynı: kurallar bu
-- fonksiyonu çağırıyor, fonksiyon da `member_accounts`'u okuyor. O tabloda da
-- satır bazlı güvenlik açık olduğu için normal (invoker) bir fonksiyon kendi
-- kuralını tetikler ve sonsuz özyinelemeye girer.
--
-- `set search_path = public` da şart: sabitlenmezse çağıran taraf kendi şemasını
-- öne alıp fonksiyonun okuduğu tabloyu değiştirebilir.
create or replace function public.user_member_ids()
returns setof text
language sql
security definer
set search_path = public
stable
as $$
    select member_id
    from public.member_accounts
    where auth_user_id = auth.uid();
$$;

revoke all on function public.user_member_ids() from public;
grant execute on function public.user_member_ids() to authenticated;

-- ---------------------------------------------------------------------------
-- Satır bazlı güvenlik
-- ---------------------------------------------------------------------------
alter table public.announcements          enable row level security;
alter table public.member_accounts        enable row level security;
alter table public.member_health_updates enable row level security;

-- Migrasyon tekrar çalıştırılabilir olmalı: `create policy`'nin `if not exists`
-- biçimi yok, o yüzden her kural önce siliniyor. 0001 tam olarak bu adım
-- unutulduğu için gerçek kurulumda "already exists" ile düşmüştü.
drop policy if exists announcements_public_select on public.announcements;
drop policy if exists announcements_staff_select  on public.announcements;
drop policy if exists announcements_insert        on public.announcements;
drop policy if exists announcements_update        on public.announcements;
drop policy if exists member_accounts_member_select on public.member_accounts;
drop policy if exists member_accounts_staff_select  on public.member_accounts;
drop policy if exists member_accounts_insert        on public.member_accounts;
drop policy if exists member_accounts_update        on public.member_accounts;
drop policy if exists gym_members_member_select     on public.gym_members;
drop policy if exists measurements_member_select    on public.measurements;
drop policy if exists health_updates_member_select  on public.member_health_updates;
drop policy if exists health_updates_member_insert  on public.member_health_updates;
drop policy if exists health_updates_staff_select   on public.member_health_updates;

-- ─── Duyurular ─────────────────────────────────────────────────────────────
-- Giriş yapılmadan okunuyor. Açılan tek şey **yayınlanmış** duyurular; bu
-- tablonun içinde kiracıya özel hiçbir iş verisi yok ve olmamalı.
--
-- Yayın penceresi kuralın içinde: süresi geçmiş bir etkinlik sitede kalmasın
-- diye. Süzme yalnızca istemcide yapılsaydı, adresi bilen biri bitmiş ya da
-- ileri tarihli duyuruları API'den okuyabilirdi.
--
-- DİKKAT: `anon` için kiracı süzgeci YOK — anonim isteğin kiracısı yoktur.
-- Yani yayınlanmış bir duyuru kiracıdan bağımsız olarak dünyaya açıktır. Bu
-- kabul edilebilir çünkü "yayınlanmış duyuru" tanımı gereği herkese açık;
-- sitenin kendisi hangi salonu gösterdiğini sorguda süzüyor.
create policy announcements_public_select on public.announcements
    for select
    to anon, authenticated
    using (
        is_published
        and deleted_at_ms is null
        and (starts_at_ms is null
             or starts_at_ms <= (extract(epoch from now()) * 1000)::bigint)
        and (ends_at_ms is null
             or ends_at_ms >= (extract(epoch from now()) * 1000)::bigint)
    );

-- Personel yayınlanmamış taslakları da görüyor — panelden düzenlenebilmeleri
-- için. Kurallar VEYA'landığı için bu, yukarıdakine ek olarak çalışıyor.
create policy announcements_staff_select on public.announcements
    for select
    to authenticated
    using (tenant_id in (select public.user_gym_ids()));

-- Yazma ADMIN + MANAGER: duyuru salonun dışa dönük yüzü. Eğitmenin siteye
-- içerik yayınlaması için sebep yok. `gym_packages` ve `products` ile aynı
-- seviye.
create policy announcements_insert on public.announcements
    for insert
    to authenticated
    with check (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    );

create policy announcements_update on public.announcements
    for update
    to authenticated
    using (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    )
    with check (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    );

-- ─── Üye ↔ hesap bağı ──────────────────────────────────────────────────────
-- Üye kendi bağını görebiliyor: üye alanı "hangi üye kaydına bağlıyım"
-- sorusunu yanıtlamak zorunda.
create policy member_accounts_member_select on public.member_accounts
    for select
    to authenticated
    using (auth_user_id = auth.uid());

create policy member_accounts_staff_select on public.member_accounts
    for select
    to authenticated
    using (tenant_id in (select public.user_gym_ids()));

-- Bağı kurmak ADMIN + MANAGER işi. `gym_members` her role yazılabilir olsa da
-- (günlük iş) bu bir erişim kararı: kimin hangi sağlık verisini göreceğini
-- belirliyor. Eğitmenin bunu yapması gerekmiyor.
create policy member_accounts_insert on public.member_accounts
    for insert
    to authenticated
    with check (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    );

create policy member_accounts_update on public.member_accounts
    for update
    to authenticated
    using (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    )
    with check (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    );

-- ─── Üyenin kendi kayıtlarını okuması ──────────────────────────────────────
-- Bu iki kural mevcut personel kurallarına EK olarak çalışıyor (kurallar
-- VEYA'lanır); personelin gördüğü hiçbir şey daralmıyor.
create policy gym_members_member_select on public.gym_members
    for select
    to authenticated
    using (id in (select public.user_member_ids()));

create policy measurements_member_select on public.measurements
    for select
    to authenticated
    using (member_id in (select public.user_member_ids()));

-- ─── Sağlık beyanı ─────────────────────────────────────────────────────────
create policy health_updates_member_select on public.member_health_updates
    for select
    to authenticated
    using (member_id in (select public.user_member_ids()));

-- Üye yalnızca KENDİ adına beyan ekleyebiliyor.
--
-- `tenant_id` de sınanıyor: yalnızca `member_id` kontrol edilse üye kendi
-- kaydına ait bir beyanı başka bir salonun kiracı kimliğiyle yazabilirdi ve o
-- satır o salonun personeline görünürdü.
create policy health_updates_member_insert on public.member_health_updates
    for insert
    to authenticated
    with check (
        member_id in (select public.user_member_ids())
        and tenant_id = (
            select m.tenant_id from public.gym_members m where m.id = member_id
        )
    );

create policy health_updates_staff_select on public.member_health_updates
    for select
    to authenticated
    using (tenant_id in (select public.user_gym_ids()));

-- ---------------------------------------------------------------------------
-- Yetkiler
-- ---------------------------------------------------------------------------
-- Yetkiler burada veriliyor, testte değil: testin kendisi verdiğinde migrasyon
-- eksik kalır ve gerçek projede tablolar erişilemez olur — bu tuzağa bu depoda
-- bir kez düşüldü (bkz. `tests/10_rls_test.sql` başındaki not).

-- Şema kullanım yetkisi. `0002` bunu `authenticated` için veriyor; `anon` ilk
-- kez burada tabloya eriştiği için onun için de gerekiyor.
--
-- Supabase projelerinde `anon` bu yetkiyle geliyor, yani "zaten var" denip
-- atlanabilirdi. Atlanmadı: varsayıma dayanan bir yetki, geri alındığı ya da
-- şema sıfırdan kurulduğu anda açılış sayfasını sessizce boşaltır ve sebebi
-- hiçbir yerde yazmaz. Yerel test bunu bizzat yakaladı.
grant usage on schema public to anon;

-- `anon` YALNIZCA duyuruları okuyor. Başka hiçbir tabloya, hiçbir yetki.
grant select on public.announcements to anon;

grant select, insert, update on public.announcements   to authenticated;
grant select, insert, update on public.member_accounts to authenticated;

-- Sağlık beyanında UPDATE yok: tablo yalnızca eklenen. Düzeltme yeni satırla
-- yapılıyor. DELETE hiçbir tabloda kimseye açık değil (bkz. 0004).
grant select, insert on public.member_health_updates to authenticated;
