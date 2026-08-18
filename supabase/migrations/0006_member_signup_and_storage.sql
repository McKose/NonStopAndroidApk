-- ---------------------------------------------------------------------------
-- Üye kayıt istekleri ve duyuru görselleri
-- ---------------------------------------------------------------------------
-- İki şey getiriyor:
--
--   1. `member_link_requests` — üyenin "hesabımı üyeliğime bağlayın" isteği.
--   2. Duyuru görselleri için depolama (Supabase Storage) kuralları.
--
-- ### Kayıt isteği neden ayrı bir tabloda
-- Üye `/uye/` üzerinden Supabase Auth ile hesap açabiliyor, ama panelin o
-- hesabı GÖRMESİ mümkün değil: `auth.users` tablosu yalnızca `service_role`
-- anahtarıyla okunuyor ve o anahtar istemciye asla konulmaz (bütün erişim
-- kurallarını baypas eder). Panel `anon`/`authenticated` anahtarıyla çalışıyor.
--
-- Yani "kayıt olmuş ama bağlanmamış hesaplar" listesi `auth.users`tan
-- türetilemez. Üye kayıt olduktan hemen sonra KENDİSİ bir istek satırı yazıyor;
-- panel o satırları okuyor. Üye kendi satırını yazabildiği için ek bir yetki de
-- gerekmiyor.
--
-- ### İstek neden kiracı taşıyor
-- Şema çok kiracılı ve istekte kiracı olmasaydı personel kuralı yazılamazdı:
-- "bütün istekleri herkes görsün" demek, bir salonun personelinin başka salona
-- kayıt olan kişilerin adını ve telefonunu görmesi olurdu. Kiracı sitenin
-- ayarından (`config.js`) geliyor — açılış sayfası zaten tek bir salonu
-- temsil ediyor.
--
-- Üyenin yanlış kiracı yazması mümkün ama zararsız: kendi bilgisi yanlış
-- salonun personeline düşer, kimsenin verisi açılmaz. Tersi — kiracıyı sunucuda
-- doğrulamak — üyenin daha kayıt olurken salonları listeleyebilmesini
-- gerektirirdi.
-- ---------------------------------------------------------------------------

create table if not exists public.member_link_requests (
    -- Hesap başına tek istek: birincil anahtar bunu sağlıyor. Aynı kişi
    -- defalarca istek gönderip personelin listesini şişiremiyor.
    auth_user_id  uuid primary key references auth.users (id) on delete cascade,
    tenant_id     uuid not null references public.gyms (id) on delete cascade,
    -- Personelin kişiyi üye kaydıyla eşleştirebilmesi için. Telefon `gym_members`
    -- üzerinde kiracı bazında tekil, yani en güvenilir eşleştirme anahtarı o.
    full_name     text not null,
    phone         text not null,
    email         text,
    note          text,
    state         text not null default 'PENDING'
                  check (state in ('PENDING', 'LINKED', 'REJECTED')),
    created_at_ms bigint not null,
    updated_at_ms bigint not null
);

create index if not exists member_link_requests_tenant_idx
    on public.member_link_requests (tenant_id, state, created_at_ms);

alter table public.member_link_requests enable row level security;

-- `create policy`nin `if not exists` biçimi yok; migrasyon tekrar
-- çalıştırılabilir olmalı (bkz. 0001'de yaşanan "already exists" hatası).
drop policy if exists link_requests_self_insert on public.member_link_requests;
drop policy if exists link_requests_self_select on public.member_link_requests;
drop policy if exists link_requests_self_update on public.member_link_requests;
drop policy if exists link_requests_staff_select on public.member_link_requests;
drop policy if exists link_requests_staff_update on public.member_link_requests;

-- Üye yalnızca KENDİ isteğini yazabiliyor.
create policy link_requests_self_insert on public.member_link_requests
    for insert
    to authenticated
    with check (auth_user_id = auth.uid());

create policy link_requests_self_select on public.member_link_requests
    for select
    to authenticated
    using (auth_user_id = auth.uid());

-- Üye bekleyen isteğini düzeltebiliyor (yanlış telefon yazmış olabilir), ama
-- yalnızca BEKLERKEN ve durumu kendisi değiştiremiyor. `with check` içindeki
-- `state = 'PENDING'` olmasaydı üye kendi isteğini "LINKED" yapabilirdi —
-- panelde bağlanmış görünür, gerçekte hiçbir bağ kurulmamış olurdu.
create policy link_requests_self_update on public.member_link_requests
    for update
    to authenticated
    using (auth_user_id = auth.uid() and state = 'PENDING')
    with check (auth_user_id = auth.uid() and state = 'PENDING');

create policy link_requests_staff_select on public.member_link_requests
    for select
    to authenticated
    using (tenant_id in (select public.user_gym_ids()));

-- Durumu değiştirmek (bağlandı/reddedildi) erişim kararı: ADMIN + MANAGER.
-- `member_accounts` yazma kuralıyla aynı seviye.
create policy link_requests_staff_update on public.member_link_requests
    for update
    to authenticated
    using (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    )
    with check (
        tenant_id in (select public.user_gym_ids_with_role(array['ADMIN', 'MANAGER']))
    );

grant select, insert, update on public.member_link_requests to authenticated;

-- ---------------------------------------------------------------------------
-- Duyuru görselleri (Supabase Storage)
-- ---------------------------------------------------------------------------
-- `storage` şeması yalnızca Supabase'de var; yerel test ortamı çıplak bir
-- PostgreSQL ve orada bu blok atlanıyor. Koşulsuz yazılsaydı bütün şema
-- testleri "schema storage does not exist" ile düşerdi.
--
-- Atlamanın bedeli açık: depolama kuralları yerel testlerde SINANMIYOR. Bunu
-- gizlemek yerine yazıyoruz — kuralların doğruluğu Supabase üzerinde elle
-- doğrulanmalı.
do $$
begin
    if not exists (select 1 from information_schema.schemata where schema_name = 'storage') then
        raise notice 'storage şeması yok (yerel test) — depolama kuralları atlandı.';
        return;
    end if;

    -- Kova: duyuru görselleri. `public = true` çünkü görseller açılış
    -- sayfasında giriş yapılmadan gösteriliyor; imzalı adres üretmek için
    -- sunucu tarafı bir bileşen gerekirdi ve burada öyle bir şey yok.
    --
    -- Kovaya YALNIZCA duyuru görselleri giriyor. Üye fotoğrafı, sağlık belgesi
    -- gibi şeyler buraya konulmamalı: bu kova herkese açık.
    insert into storage.buckets (id, name, public)
    values ('duyuru-gorselleri', 'duyuru-gorselleri', true)
    on conflict (id) do update set public = true;

    execute 'drop policy if exists duyuru_gorsel_okuma on storage.objects';
    execute 'drop policy if exists duyuru_gorsel_yazma on storage.objects';
    execute 'drop policy if exists duyuru_gorsel_silme on storage.objects';

    -- Okuma herkese açık: açılış sayfası giriş yapılmadan gösteriyor.
    execute $k$
        create policy duyuru_gorsel_okuma on storage.objects
            for select to anon, authenticated
            using (bucket_id = 'duyuru-gorselleri')
    $k$;

    -- Yükleme ve silme ADMIN + MANAGER. `announcements` yazma kuralıyla aynı
    -- seviye: duyuruyu yazabilen görselini de yükleyebilmeli, eğitmen ikisini
    -- de yapmamalı.
    --
    -- Salon süzgeci yok ve sebebi şu: `storage.objects` üzerinde kiracı bilgisi
    -- yalnızca dosya yolunda duruyor (`<tenant>/<dosya>`), ve yol üzerinden
    -- kural yazmak yanlış bir güven duygusu verirdi — yolu istemci belirliyor.
    -- Kovanın içeriği zaten herkese açık; korunan tek şey KİMİN yazabildiği.
    execute $k$
        create policy duyuru_gorsel_yazma on storage.objects
            for insert to authenticated
            with check (
                bucket_id = 'duyuru-gorselleri'
                and exists (
                    select 1 from public.user_gym_ids_with_role(array['ADMIN', 'MANAGER'])
                )
            )
    $k$;

    execute $k$
        create policy duyuru_gorsel_silme on storage.objects
            for delete to authenticated
            using (
                bucket_id = 'duyuru-gorselleri'
                and exists (
                    select 1 from public.user_gym_ids_with_role(array['ADMIN', 'MANAGER'])
                )
            )
    $k$;

    raise notice 'Duyuru görselleri kovası ve kuralları kuruldu.';
end
$$;
