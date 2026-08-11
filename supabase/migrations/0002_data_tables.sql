-- ---------------------------------------------------------------------------
-- Veri tabloları
-- ---------------------------------------------------------------------------
-- Uygulamadaki Room tablolarının sunucu karşılıkları. Tablo adları bilinçli
-- olarak birebir aynı; kolon adları ise Postgres geleneğine uyup snake_case.
-- Eşleme istemci tarafındaki serileştirme katmanında yapılacak — tırnaksız
-- Postgres tanımlayıcıları küçük harfe katlandığı için `tenantId` gibi bir kolon
-- her sorguda tırnak istemek demekti ve tek bir unutulan tırnak sessiz hataya
-- dönüşürdü.
--
-- Ortak kolonlar ve nedenleri:
--   id            text  — istemcide üretilen UUID. Çevrimdışı iki cihaz kayıt
--                         eklediğinde çakışmasın diye sunucuda üretilmiyor.
--   tenant_id     uuid  — salon. Tüm erişim kuralları buna dayanıyor.
--   *_ms          bigint— epoch milisaniye. İstemcideki değerle birebir aynı
--                         kalsın diye timestamptz'e çevrilmiyor: dönüşüm
--                         yapılsaydı saat dilimi ve yuvarlama farkları
--                         senkronizasyonu gereksiz yere kırılgan yapardı.
--                         Panel gösterirken çevirir.
--   deleted_at_ms bigint— tombstone. Silme de senkronize edilebilsin diye satır
--                         fiziksel olarak kalıyor.
--
-- `sync_outbox` burada YOK ve olmamalı: o tablo cihazın kendi gönderim kuyruğu,
-- sunucuya taşınacak bir veri değil.
-- ---------------------------------------------------------------------------

-- ─── Paketler ──────────────────────────────────────────────────────────────
create table if not exists public.gym_packages (
    id              text primary key,
    tenant_id       uuid not null references public.gyms (id) on delete cascade,
    name            text not null,
    type            text not null check (type in ('FITNESS', 'FUNCTIONAL', 'REFORMER')),
    category        text not null check (category in ('INDIVIDUAL', 'DUET', 'GROUP')),
    validity_days   integer not null,
    -- null = sınırsız (abonman). `-1` sihirli sayısı bilinçli olarak kullanılmıyor.
    session_count   integer,
    base_price_minor bigint not null,
    is_active       boolean not null default true,
    created_at_ms   bigint not null,
    updated_at_ms   bigint not null,
    deleted_at_ms   bigint
);

-- ─── Personel ──────────────────────────────────────────────────────────────
-- DİKKAT: `password` kolonu YOK. Uygulamadaki `staff.password` alanı sunucuya
-- taşınmıyor; kimlik doğrulama Supabase Auth'a ait. Düz metin şifreleri sunucuya
-- kopyalamak çözmeye çalıştığımız sorunu büyütmek olurdu.
create table if not exists public.staff (
    id                      text primary key,
    tenant_id               uuid not null references public.gyms (id) on delete cascade,
    full_name               text not null,
    title                   text not null,
    role                    text not null check (role in ('ADMIN', 'MANAGER', 'TRAINER')),
    branch                  text not null,
    -- Baz puan: 4000 = %40. Yüzde/kesir karışıklığı birim tipte kodlu.
    commission_basis_points integer not null check (commission_basis_points between 0 and 10000),
    monthly_salary_minor    bigint not null,
    phone                   text not null,
    nickname                text not null,
    is_active               boolean not null default true,
    created_at_ms           bigint not null,
    updated_at_ms           bigint not null,
    deleted_at_ms           bigint,
    unique (tenant_id, nickname)
);

-- ─── Üyeler ────────────────────────────────────────────────────────────────
create table if not exists public.gym_members (
    id                  text primary key,
    tenant_id           uuid not null references public.gyms (id) on delete cascade,
    full_name           text not null,
    -- E.164 (+90XXXXXXXXXX). Normalize edilmeden yazılırsa tekillik kısıtı işe yaramaz.
    phone               text not null,
    email               text,
    birth_date_ms       bigint,
    active_package_id   text,
    total_sessions      integer,
    remaining_sessions  integer,
    start_date_ms       bigint,
    end_date_ms         bigint,
    status              text not null check (status in ('ACTIVE', 'FROZEN', 'ARCHIVED')),
    payment_type        text not null check (payment_type in ('CASH', 'CARD', 'MULTISPORT')),
    installment_count   integer not null default 1,
    package_price_minor bigint not null default 0,
    discount_minor      bigint not null default 0,
    price_paid_minor    bigint not null default 0,
    payment_status      text not null default 'PENDING',
    payment_date_ms     bigint,
    notes               text,
    health_risks        text,
    risk_level          text,
    health_notes        text,
    created_at_ms       bigint not null,
    updated_at_ms       bigint not null,
    deleted_at_ms       bigint,
    -- Tombstone satırlar da dahil: silinmiş üyenin numarası yeniden kaydedilirken
    -- istemci tarafı kaydı canlandırıyor, yeni satır açmıyor.
    unique (tenant_id, phone)
);

create index if not exists gym_members_tenant_end_date_idx
    on public.gym_members (tenant_id, end_date_ms);

-- ─── Ürünler ───────────────────────────────────────────────────────────────
create table if not exists public.products (
    id            text primary key,
    tenant_id     uuid not null references public.gyms (id) on delete cascade,
    name          text not null,
    category      text not null,
    price_minor   bigint not null,
    image_url     text,
    is_active     boolean not null default true,
    created_at_ms bigint not null,
    updated_at_ms bigint not null,
    deleted_at_ms bigint
);

-- ─── Randevular ────────────────────────────────────────────────────────────
create table if not exists public.appointments (
    id                  text primary key,
    tenant_id           uuid not null references public.gyms (id) on delete cascade,
    member_id           text not null,
    staff_id            text not null,
    training_type       text not null check (training_type in ('FITNESS', 'FUNCTIONAL', 'REFORMER')),
    start_time_ms       bigint not null,
    end_time_ms         bigint not null,
    state               text not null
                        check (state in ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'POSTPONED', 'NO_SHOW')),
    -- Hakediş matrahı randevu anında donduruluyor: üye arada paketini
    -- yenilerse aynı ders için farklı hakediş çıkmasın diye.
    session_value_minor bigint not null default 0,
    -- "finansal etki uygulandı" demek; "durum kilitlendi" değil.
    settled_at_ms       bigint,
    notes               text,
    created_at_ms       bigint not null,
    updated_at_ms       bigint not null,
    deleted_at_ms       bigint
);

create index if not exists appointments_tenant_start_idx
    on public.appointments (tenant_id, start_time_ms);

-- ─── Siparişler ────────────────────────────────────────────────────────────
create table if not exists public.orders (
    id                 text primary key,
    tenant_id          uuid not null references public.gyms (id) on delete cascade,
    member_id          text,
    total_price_minor  bigint not null,
    discount_minor     bigint not null default 0,
    final_price_minor  bigint not null,
    payment_method     text not null check (payment_method in ('CASH', 'CARD', 'MULTISPORT')),
    payment_status     text not null,
    delivery_status    text not null check (delivery_status in ('PRE_DELIVERY', 'POST_DELIVERY')),
    date_ms            bigint not null,
    notes              text,
    created_at_ms      bigint not null,
    updated_at_ms      bigint not null,
    deleted_at_ms      bigint
);

-- ─── Ölçümler ──────────────────────────────────────────────────────────────
create table if not exists public.measurements (
    id            text primary key,
    tenant_id     uuid not null references public.gyms (id) on delete cascade,
    member_id     text not null,
    date_ms       bigint not null,
    height        double precision not null,
    weight        double precision not null,
    shoulder      double precision not null,
    chest         double precision not null,
    waist         double precision not null,
    hips          double precision not null,
    leg           double precision not null,
    arm           double precision not null,
    notes         text not null default '',
    created_at_ms bigint not null,
    updated_at_ms bigint not null,
    deleted_at_ms bigint
);

create index if not exists measurements_tenant_member_idx
    on public.measurements (tenant_id, member_id);

-- ─── Finans defteri ────────────────────────────────────────────────────────
-- Append-only: düzeltme silme/güncelleme ile değil ters kayıtla yapılıyor.
-- `deleted_at_ms` bilinçli olarak yok.
create table if not exists public.ledger_entries (
    id             text primary key,
    tenant_id      uuid not null references public.gyms (id) on delete cascade,
    type           text not null check (type in ('CHARGE', 'PAYMENT', 'EXPENSE')),
    category       text not null
                   check (category in ('MEMBERSHIP', 'MARKET', 'COMMISSION', 'SALARY',
                                       'RENT', 'BILL', 'PURCHASE', 'OTHER')),
    -- Tutar daima pozitif; yön `type` ile ifade ediliyor.
    amount_minor   bigint not null check (amount_minor > 0),
    payment_method text not null check (payment_method in ('CASH', 'CARD', 'MULTISPORT')),
    member_id      text,
    staff_id       text,
    order_id       text,
    appointment_id text,
    description    text not null,
    occurred_at_ms bigint not null,
    -- Doluysa bu satır başka bir kaydı iptal eden ters kayıttır.
    reverses_id    text references public.ledger_entries (id),
    created_at_ms  bigint not null
);

create index if not exists ledger_entries_tenant_occurred_idx
    on public.ledger_entries (tenant_id, occurred_at_ms);
create index if not exists ledger_entries_reverses_idx
    on public.ledger_entries (reverses_id);

-- ─── Stok hareketleri ──────────────────────────────────────────────────────
-- Stok, ürün satırındaki bir sayaçta değil hareketlerin toplamında. Hareketler
-- toplanabilir olduğu için iki cihaz aynı anda satış yaptığında hiçbiri kaybolmaz.
create table if not exists public.stock_movements (
    id             text primary key,
    tenant_id      uuid not null references public.gyms (id) on delete cascade,
    product_id     text not null,
    quantity_delta integer not null,
    reason         text not null check (reason in ('PURCHASE', 'SALE', 'CORRECTION', 'RETURN')),
    order_id       text,
    note           text,
    occurred_at_ms bigint not null,
    created_at_ms  bigint not null
);

create index if not exists stock_movements_tenant_product_idx
    on public.stock_movements (tenant_id, product_id);

-- ---------------------------------------------------------------------------
-- Erişim kuralları
-- ---------------------------------------------------------------------------
-- Her tablo aynı kuralı alıyor: kullanıcı yalnızca bağlı olduğu salonun
-- satırlarına erişir.
--
-- `with check` kısmı en az `using` kadar önemli ve unutulması kolay: `using`
-- hangi satırların **görüneceğini** söyler, `with check` hangi satırların
-- **yazılabileceğini**. `with check` olmasaydı bir istemci başka bir salonun
-- tenant_id'siyle satır yazabilirdi — okuyamayacağı ama bozabileceği veriye.
do $$
declare
    t text;
begin
    foreach t in array array[
        'gym_packages', 'staff', 'gym_members', 'products',
        'appointments', 'orders', 'measurements',
        'ledger_entries', 'stock_movements'
    ]
    loop
        execute format('alter table public.%I enable row level security', t);
        execute format('drop policy if exists %I on public.%I', t || '_tenant_access', t);
        execute format(
            'create policy %I on public.%I for all
                 to authenticated
                 using (tenant_id in (select public.user_gym_ids()))
                 with check (tenant_id in (select public.user_gym_ids()))',
            t || '_tenant_access', t
        );
        -- Tablo yetkisi, satır kuralından AYRI bir katman ve ikisi de gerekli.
        -- Yetki olmadan satır bazlı güvenlik hiç devreye girmez: sorgu daha
        -- önce "permission denied" ile düşer.
        execute format('grant select, insert, update, delete on public.%I to authenticated', t);
    end loop;
end
$$;

-- ---------------------------------------------------------------------------
-- Rol yetkileri
-- ---------------------------------------------------------------------------
-- Satır bazlı güvenlik hangi SATIRLARIN görüneceğini söyler; tablo yetkisi ise
-- tabloya erişilip erişilemeyeceğini. İkisi ayrı katman ve ikisi de gerekli.
--
-- Bu bölüm eksikti ve testler bunu göstermedi, çünkü grant'lar test dosyasının
-- içindeydi — yani test, migrasyonun kurmadığı bir durumu doğruluyordu. Grant'lar
-- buraya taşındı, test dosyasından çıkarıldı.
--
-- `anon` rolüne bilinçli olarak HİÇBİR yetki verilmiyor: giriş yapmamış bir
-- istemcinin salon verisine erişmesi için hiçbir sebep yok.
grant usage on schema public to authenticated;
grant select on public.gyms, public.gym_users to authenticated;
grant execute on function public.user_gym_ids() to authenticated;
